// ─── Custom HTTP Error ────────────────────────────────────────────────────────
// Extiende Error para transportar el status y el cuerpo de la respuesta fallida,
// permitiendo que el catch del consumidor distinga errores HTTP de errores de red.

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
export function createApiClient({ baseURL = '', headers: globalHeaders = {} } = {}) {

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
   * @param {Object} [options.fetchOptions] - Additional options to pass to the native fetch call.
   * @returns {Promise<{data: *, status: number, headers: Headers, ok: boolean}>} The response object.
   * @throws {HttpError} If a network failure occurs or the server returns a non-OK status.
   */
  async function request(method, url, { body, headers: localHeaders = {}, ...fetchOptions } = {}) {
    const fullURL = url.startsWith('http') ? url : `${baseURL}${url}`;

    const mergedHeaders = {
      ...globalHeaders,
      ...localHeaders,
    };

    // Serializa el body y añade Content-Type solo cuando el caller pasa un objeto JS.
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
      // fetch solo rechaza la promesa ante fallos de red (sin conexión, DNS, etc.).
      throw new HttpError(`Network error: ${networkError.message}`, 0, null);
    }

    // Parseo automático: detecta JSON por Content-Type para evitar el doble await.
    const contentType = response.headers.get('Content-Type') ?? '';
    let data;
    if (contentType.includes('application/json')) {
      data = await response.json();
    } else if (contentType.includes('text/')) {
      data = await response.text();
    } else {
      data = await response.blob();
    }

    // fetch NO lanza en 4xx/5xx; lo hacemos nosotros con el cuerpo ya parseado
    // para que HttpError.data contenga el mensaje de error del servidor.
    if (!response.ok) {
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

  // ── Métodos públicos ─────────────────────────────────────────────────────────
  return {
    /**
     * Sends a GET request.
     * @param {string} url - Target URL.
     * @param {Object} [options] - Additional request configurations and options.
     * @returns {Promise<{data: *, status: number, headers: Headers, ok: boolean}>}
     */
    get:    (url, options)        => request('GET',    url, options),
    
    /**
     * Sends a DELETE request.
     * @param {string} url - Target URL.
     * @param {Object} [options] - Additional request configurations and options.
     * @returns {Promise<{data: *, status: number, headers: Headers, ok: boolean}>}
     */
    delete: (url, options)        => request('DELETE', url, options),
    
    /**
     * Sends a POST request.
     * @param {string} url - Target URL.
     * @param {*} body - Request body payload.
     * @param {Object} [options] - Additional request configurations and options.
     * @returns {Promise<{data: *, status: number, headers: Headers, ok: boolean}>}
     */
    post:   (url, body, options)  => request('POST',   url, { body, ...options }),
    
    /**
     * Sends a PUT request.
     * @param {string} url - Target URL.
     * @param {*} body - Request body payload.
     * @param {Object} [options] - Additional request configurations and options.
     * @returns {Promise<{data: *, status: number, headers: Headers, ok: boolean}>}
     */
    put:    (url, body, options)  => request('PUT',    url, { body, ...options }),
    
    /**
     * Sends a PATCH request.
     * @param {string} url - Target URL.
     * @param {*} body - Request body payload.
     * @param {Object} [options] - Additional request configurations and options.
     * @returns {Promise<{data: *, status: number, headers: Headers, ok: boolean}>}
     */
    patch:  (url, body, options)  => request('PATCH',  url, { body, ...options }),
  };
}
