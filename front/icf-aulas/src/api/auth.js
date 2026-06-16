import { createApiClient, HttpError } from './base.js';
import { LoginRequestSchema, LoginResponseSchema } from '../schemas/index.js';

/**
 * @typedef {Object} LoginRequestSchema
 * @property {string} username - Username.
 * @property {string} password - Plain-text password (max 128 chars).
 */

/**
 * @typedef {Object} LoginResponseSchema
 * @property {string} token        - Signed JWT access token.
 * @property {string} refreshToken - Signed JWT refresh token.
 * @property {string} uuid         - Public UUID of the authenticated user.
 * @property {string} name         - Full display name.
 * @property {string} role         - Role name (e.g. "ADMIN", "MAESTRO").
 * @property {string} email        - Login email of the authenticated user.
 */

/**
 * @typedef  {Object} HttpError
 * @property {string} message        - Error message.
 * @property {string} status         - Status code.
 * @property {string} type           - Error type.
 */

const BASE_PATH = '/api/v1/auth';

const api = createApiClient({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: { Accept: 'application/json' },
});

/**
 * Maps an {@link HttpError} to a human-readable Spanish message using the HTTP
 * status code. The ApiResponse body message is used as a fallback when the
 * server provides one; otherwise a generic message is returned.
 *
 * The status is intentionally read from the HTTP response (HttpError.status),
 * not from the ApiResponse.error flag, so callers never need to inspect the
 * wrapper envelope — they just catch a plain Error.
 *
 * @param {HttpError} error - The HTTP error thrown by the base client.
 * @param {Record<number, string>} [overrides] - Per-status message overrides.
 * @returns {string} User-facing error message.
 */
function resolveErrorMessage(error, overrides = {}) {
  if (overrides[error.status]) return overrides[error.status];

  const serverMessage = error.data?.message;

  const defaults = {
    0: 'No se pudo conectar con el servidor. Verifica tu conexión.',
    400: 'Los datos enviados no son válidos.',
    401: 'Credenciales incorrectas.',
    403: 'No tienes permisos para realizar esta acción.',
    404: 'El recurso solicitado no existe.',
    409: 'Conflicto con el estado actual del recurso.',
    422: 'Los datos enviados no pudieron ser procesados.',
    429: 'Demasiadas solicitudes. Intenta de nuevo más tarde.',
    500: 'Error interno del servidor. Intenta de nuevo más tarde.',
  };

  return serverMessage || defaults[error.status] || `Error inesperado (${error.status}).`;
}

// ── Endpoints ────────────────────────────────────────────────────────────────

/**
 * Authenticates a user and returns access + refresh tokens with basic profile info.
 *
 * @param {LoginRequestSchema} credentials - User credentials.
 * @returns {Promise<LoginResponseSchema>} Resolved token pair and user info on success.
 * @throws {Error} With a user-facing message derived from the HTTP status code.
 *
 * @example
 * const session = await login({ username: [USERNAME]', password: '[PASSWORD]' });
 * localStorage.setItem('token', session.token);
 */
export async function login(credentials) {
  try {
    const user = LoginRequestSchema.parse(credentials);
    const { data } = await api.post(`${BASE_PATH}/login`, user);
    const parsed = LoginResponseSchema.parse(data.data);
    return parsed;
  } catch (error) {
    if (error instanceof HttpError) {
      throw new Error(resolveErrorMessage(error, {
        401: 'Correo o contraseña incorrectos.',
        403: 'Tu cuenta no tiene acceso a este sistema.',
      }));
    }
    throw error;
  }
}

/**
 * Revokes the current access token and optionally a refresh token.
 * Should be called before clearing local session storage.
 *
 * @param {string} token         - The active JWT access token (without "Bearer " prefix).
 * @param {string} [refreshToken] - Optional refresh token to also invalidate.
 * @returns {Promise<void>}
 * @throws {Error} With a user-facing message if the revocation request fails.
 *
 * @example
 * await logout(localStorage.getItem('token'), localStorage.getItem('refreshToken'));
 * localStorage.clear();
 */
export async function logout(token, refreshToken) {
  try {
    await api.post(
      `${BASE_PATH}/logout`,
      refreshToken ? { refreshToken } : null,
      { headers: { Authorization: `Bearer ${token}` } },
    );
  } catch (error) {
    if (error instanceof HttpError) {
      throw new Error(resolveErrorMessage(error));
    }
    throw error;
  }
}

/**
 * Exchanges a valid refresh token for a new access/refresh token pair.
 * The old refresh token is rotated (invalidated) server-side.
 *
 * @param {string} refreshToken - The current refresh token.
 * @returns {Promise<AuthTokens>} New token pair and user info.
 * @throws {Error} With a user-facing message if the token is invalid or expired.
 *
 * @example
 * const renewed = await refresh(localStorage.getItem('refreshToken'));
 * localStorage.setItem('token', renewed.token);
 * localStorage.setItem('refreshToken', renewed.refreshToken);
 */
export async function refresh(refreshToken) {
  try {
    const { data } = await api.post(`${BASE_PATH}/refresh`, { refreshToken });
    return data.data;
  } catch (error) {
    if (error instanceof HttpError) {
      throw new Error(resolveErrorMessage(error, {
        401: 'La sesión ha expirado. Inicia sesión nuevamente.',
        403: 'El token de refresco ha sido revocado.',
      }));
    }
    throw error;
  }
}

/**
 * Sends a password-reset link to the given email address.
 * Always resolves successfully regardless of whether the email is registered,
 * to prevent account enumeration on the client side as well.
 *
 * @param {string} email - Account email to send the reset link to.
 * @returns {Promise<string>} Server confirmation message.
 * @throws {Error} With a user-facing message on network or server failure.
 *
 * @example
 * const message = await forgotPassword('user@icf.unam.mx');
 * showToast(message);
 */
export async function forgotPassword(email) {
  try {
    const { data } = await api.post(`${BASE_PATH}/forgot-password`, { email });
    return data.message;
  } catch (error) {
    if (error instanceof HttpError) {
      throw new Error(resolveErrorMessage(error));
    }
    throw error;
  }
}

/**
 * Sets a new password using the one-time reset token received by email.
 * The reset token is invalidated server-side after a successful call.
 *
 * @param {string} token       - One-time reset token from the email link query param.
 * @param {string} newPassword - New password (8–128 chars).
 * @returns {Promise<string>} Server confirmation message.
 * @throws {Error} With a user-facing message if the token is invalid, expired, or already used.
 *
 * @example
 * const token = new URLSearchParams(location.search).get('token');
 * await resetPassword(token, '!NewPass99');
 * navigate('/login', { state: { success: 'Contraseña actualizada.' } });
 */
export async function resetPassword(token, newPassword) {
  try {
    const { data } = await api.post(`${BASE_PATH}/reset-password`, { token, newPassword });
    return data.message;
  } catch (error) {
    if (error instanceof HttpError) {
      throw new Error(resolveErrorMessage(error, {
        401: 'El enlace de restablecimiento es inválido o ha expirado.',
        403: 'Este enlace ya fue utilizado.',
      }));
    }
    throw error;
  }
}
