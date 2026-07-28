import { resolveApiError } from '../errors/resolveApiError.js';

/**
 * Custom HTTP Error class that extends the native Error.
 * It carries the HTTP status code and response payload, allowing consumers
 * to distinguish server-returned HTTP errors from network failures.
 */
export class HttpError extends Error {
  /**
   * Creates an instance of HttpError.
   * @param {string} message - The error message.
   * @param {number} status - The HTTP status code (0 for network/connection errors).
   * @param {*} data - The parsed response body from the server.
   */
  constructor(message, status, data) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.data = data;
  }
}

// ─── Factory ──────────────────────────────────────────────────────────────────

/**
 * Factory function to create an API client configured with a base URL and global headers.
 * 
 * @param {Object} [config={}] - Configuration options for the API client.
 * @param {string} [config.baseURL=''] - The base URL for all relative request paths.
 * @param {Record<string, string>} [config.headers={}] - Global headers to include in every request.
 * @returns {Object} An object containing HTTP client helper methods (get, delete, post, put, patch).
 */
export function createApiClient({
  baseURL = import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: globalHeaders = { Accept: 'application/json' },
} = {}) {

  // ── Core request ────────────────────────────────────────────────────────────

  /**
   * Core request wrapper around the native fetch API.
   * Handles relative URL resolution, headers merging, body serialization,
   * response parsing (JSON, text, or blob), and error wrapping.
   * 
   * @param {'GET'|'POST'|'PUT'|'DELETE'|'PATCH'} method - The HTTP method to use.
   * @param {string} url - The target URL (absolute or relative to baseURL).
   * @param {Object} [options={}] - Extra request options.
   * @param {*} [options.body] - The request payload (automatically serialized to JSON if it is a plain object).
   * @param {Record<string, string>} [options.headers] - Request-specific headers.
   * @param {boolean} [options.skipAuthRedirect=false] - When true, a 401/403 response will NOT trigger the
   *   forced logout redirect. Use this for endpoints that intentionally send their own Authorization header
   *   (e.g. the logout endpoint) so a server-side 401 doesn't cause a redirect loop.
   * @param {Object} [options.fetchOptions] - Additional options to pass to the native fetch call.
   * @returns {Promise<{data: *, status: number, headers: Headers, ok: boolean}>} The response object.
   * @throws {HttpError} If a network failure occurs or the server returns a non-OK status.
   */
  async function request(method, url, { body, headers: localHeaders = {}, skipAuthRedirect = false, ...fetchOptions } = {}) {
    const fullURL = url.startsWith('http') ? url : `${baseURL}${url}`;

    const mergedHeaders = {
      ...globalHeaders,
      ...localHeaders,
    };

    // Request interceptor: auto-inject the JWT stored in localStorage.
    // localHeaders['Authorization'] always takes precedence so callers can override
    // (e.g. the logout endpoint passes its own header explicitly).
    const storedToken = localStorage.getItem('token');
    if (storedToken && !mergedHeaders['Authorization']) {
      mergedHeaders['Authorization'] = `Bearer ${storedToken}`;
    }

    // Serialize the body and add Content-Type only when the caller passes a JS object.
    let serializedBody;
    if (body !== undefined && body !== null) {
      if (typeof body === 'object' && !(body instanceof FormData) && !(body instanceof Blob)) {
        mergedHeaders['Content-Type'] = mergedHeaders['Content-Type'] ?? 'application/json';
        serializedBody = JSON.stringify(body);
      } else {
        serializedBody = body;
      }
    }

    let response;
    try {
      response = await fetch(fullURL, {
        method,
        headers: mergedHeaders,
        body: serializedBody,
        ...fetchOptions,
      });
    } catch (networkError) {
      // fetch only rejects promise on network failures (no connection, DNS, etc.).
      throw new HttpError(`Network error: ${networkError.message}`, 0, null);
    }

    // Auto-parse JSON by Content-Type to avoid double await.
    const contentType = response.headers.get('Content-Type') ?? '';
    let data;
    if (contentType.includes('application/json')) {
      data = await response.json();
    } else if (contentType.includes('text/')) {
      data = await response.text();
    } else {
      data = await response.blob();
    }

    // Fetch does not throw on non-OK HTTP status. 
    // We do it with parsed body for HttpError.data.
    if (!response.ok) {
      // Response interceptor: detect a revoked/expired/tampered session.
      // Only 401 means the session itself is invalid — a 403 is an authorization decision
      // about a perfectly valid session (e.g. a teacher hitting an admin-only endpoint), and
      // forcing a logout for it hid every permission-denied message the catalog defines.
      // The guard `storedToken` prevents this branch from firing on intentionally
      // unauthenticated requests like POST /auth/login (401 = wrong credentials).
      // `skipAuthRedirect` lets callers (e.g. the logout endpoint) opt-out so a
      // server-side 401 during logout doesn't re-trigger the redirect while the
      // local session is already being cleared.
      if (response.status === 401 && storedToken && !skipAuthRedirect) {
        // Signal the Login page to show the "session expired" modal after the
        // hard reload. sessionStorage survives a same-tab location change but
        // NOT a cross-tab or a new window, which is the desired scope.
        sessionStorage.setItem('authReason', 'revoked');
        localStorage.clear();
        window.location.href = '/login';
        // Return a promise that never resolves so no downstream code runs while
        // the browser is navigating away (prevents stale error toasts/state).
        return new Promise(() => { });
      }

      throw new HttpError(
        `HTTP ${response.status} ${response.statusText}`,
        response.status,
        data,
      );
    }

    return {
      data,
      status: response.status,
      headers: response.headers,
      ok: true,
    };
  }

  // ── Validated request (single point of error routing) ───────────────────────

  /**
   * Runs a request and, on success, unwraps the `ApiResponse` envelope and validates the
   * inner payload against `schema` (when given). On failure — network error, non-2xx status,
   * or the payload not matching `schema` — routes through {@link resolveApiError} exactly
   * once, so every one of the 8 API modules gets identical error handling instead of each
   * repeating its own `try/catch { if (error instanceof HttpError) ... }` block.
   *
   * @param {'GET'|'POST'|'PUT'|'DELETE'|'PATCH'} method
   * @param {string} url
   * @param {Object} [options={}]
   * @param {*} [options.body] - Request payload, forwarded to `request`.
   * @param {import('zod').ZodSchema} [options.schema] - Validates `envelope.data`. Omit for
   *   endpoints with no meaningful response body (e.g. a bare DELETE).
   * @param {Record<string, string>} [options.overrides] - Per-call message overrides keyed by
   *   ErrorCode name or HTTP status, forwarded to `resolveApiError`.
   * @returns {Promise<*>} the parsed inner payload (not the envelope).
   * @throws {ApiError}
   */
  async function requestAndValidate(method, url, { schema, overrides, ...options } = {}) {
    try {
      const { data: envelope } = await request(method, url, options);
      const payload = envelope?.data;
      return schema ? schema.parse(payload) : payload;
    } catch (error) {
      throw resolveApiError(error, overrides);
    }
  }

  // ── Public methods ─────────────────────────────────────────────────────────
  return {
    /**
     * Sends a GET request.
     * @param {string} url - Target URL.
     * @param {Object} [options] - Additional request configurations and options.
     * @returns {Promise<{data: *, status: number, headers: Headers, ok: boolean}>}
     */
    get: (url, options) => request('GET', url, options),

    /**
     * Sends a DELETE request.
     * @param {string} url - Target URL.
     * @param {Object} [options] - Additional request configurations and options.
     * @returns {Promise<{data: *, status: number, headers: Headers, ok: boolean}>}
     */
    delete: (url, options) => request('DELETE', url, options),

    /**
     * Sends a POST request.
     * @param {string} url - Target URL.
     * @param {*} body - Request body payload.
     * @param {Object} [options] - Additional request configurations and options.
     * @returns {Promise<{data: *, status: number, headers: Headers, ok: boolean}>}
     */
    post: (url, body, options) => request('POST', url, { body, ...options }),

    /**
     * Sends a PUT request.
     * @param {string} url - Target URL.
     * @param {*} body - Request body payload.
     * @param {Object} [options] - Additional request configurations and options.
     * @returns {Promise<{data: *, status: number, headers: Headers, ok: boolean}>}
     */
    put: (url, body, options) => request('PUT', url, { body, ...options }),

    /**
     * Sends a PATCH request.
     * @param {string} url - Target URL.
     * @param {*} body - Request body payload.
     * @param {Object} [options] - Additional request configurations and options.
     * @returns {Promise<{data: *, status: number, headers: Headers, ok: boolean}>}
     */
    patch: (url, body, options) => request('PATCH', url, { body, ...options }),

    /**
     * GET + unwrap + validate + route errors through resolveApiError. See {@link requestAndValidate}.
     * @param {string} url
     * @param {Object} [options] - `{ schema, overrides, ...fetchOptions }`
     * @returns {Promise<*>} the parsed inner payload
     */
    getValidated: (url, options) => requestAndValidate('GET', url, options),

    /**
     * DELETE + unwrap + validate + route errors through resolveApiError.
     * @param {string} url
     * @param {Object} [options] - `{ schema, overrides, ...fetchOptions }`
     * @returns {Promise<*>} the parsed inner payload
     */
    deleteValidated: (url, options) => requestAndValidate('DELETE', url, options),

    /**
     * POST + unwrap + validate + route errors through resolveApiError.
     * @param {string} url
     * @param {*} body
     * @param {Object} [options] - `{ schema, overrides, ...fetchOptions }`
     * @returns {Promise<*>} the parsed inner payload
     */
    postValidated: (url, body, options) => requestAndValidate('POST', url, { body, ...options }),

    /**
     * PUT + unwrap + validate + route errors through resolveApiError.
     * @param {string} url
     * @param {*} body
     * @param {Object} [options] - `{ schema, overrides, ...fetchOptions }`
     * @returns {Promise<*>} the parsed inner payload
     */
    putValidated: (url, body, options) => requestAndValidate('PUT', url, { body, ...options }),

    /**
     * PATCH + unwrap + validate + route errors through resolveApiError.
     * @param {string} url
     * @param {*} body
     * @param {Object} [options] - `{ schema, overrides, ...fetchOptions }`
     * @returns {Promise<*>} the parsed inner payload
     */
    patchValidated: (url, body, options) => requestAndValidate('PATCH', url, { body, ...options }),
  };
}
