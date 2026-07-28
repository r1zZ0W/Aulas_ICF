import { createApiClient } from './base.js';
import { LoginRequestSchema, LoginResponseSchema } from '../schemas/index.js';
import { resolveApiError, assertValidRequestPayload } from '../errors/resolveApiError.js';

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

const BASE_PATH = '/api/v1/auth';

const api = createApiClient();

// ── Endpoints ────────────────────────────────────────────────────────────────

/**
 * Authenticates a user and returns access + refresh tokens with basic profile info.
 *
 * @param {LoginRequestSchema} credentials - User credentials.
 * @returns {Promise<LoginResponseSchema>} Resolved token pair and user info on success.
 * @throws {import('../errors/ApiError.js').ApiError}
 *
 * @example
 * const session = await login({ username: '[USERNAME]', password: '[PASSWORD]' });
 * localStorage.setItem('token', session.token);
 */
export async function login(credentials) {
  const user = assertValidRequestPayload(LoginRequestSchema, credentials);
  return api.postValidated(`${BASE_PATH}/login`, user, { schema: LoginResponseSchema });
}

/**
 * Revokes the current access token and optionally a refresh token.
 * Should be called before clearing local session storage.
 *
 * @param {string} token         - The active JWT access token (without "Bearer " prefix).
 * @param {string} [refreshToken] - Optional refresh token to also invalidate.
 * @returns {Promise<void>}
 * @throws {import('../errors/ApiError.js').ApiError}
 *
 * @example
 * await logout(localStorage.getItem('token'), localStorage.getItem('refreshToken'));
 * localStorage.clear();
 */
export async function logout(token, refreshToken) {
  return api.postValidated(
    `${BASE_PATH}/logout`,
    refreshToken ? { refreshToken } : null,
    {
      headers: { Authorization: `Bearer ${token}` },
      // Prevent the 401 interceptor from firing during logout — the server may
      // return 401 if the token was already expired, but that's non-fatal here.
      skipAuthRedirect: true,
    },
  );
}

/**
 * Exchanges a valid refresh token for a new access/refresh token pair.
 * The old refresh token is rotated (invalidated) server-side.
 *
 * @param {string} refreshToken - The current refresh token.
 * @returns {Promise<object>} New token pair and user info.
 * @throws {import('../errors/ApiError.js').ApiError}
 *
 * @example
 * const renewed = await refresh(localStorage.getItem('refreshToken'));
 * localStorage.setItem('token', renewed.token);
 * localStorage.setItem('refreshToken', renewed.refreshToken);
 */
export async function refresh(refreshToken) {
  return api.postValidated(`${BASE_PATH}/refresh`, { refreshToken }, {
    overrides: {
      TOKEN_INVALID: 'La sesión ha expirado. Inicia sesión nuevamente.',
      TOKEN_REVOKED: 'El token de refresco ha sido revocado.',
    },
  });
}

/**
 * Sends a password-reset link to the given email address.
 * Always resolves successfully regardless of whether the email is registered,
 * to prevent account enumeration on the client side as well.
 *
 * @param {string} email - Account email to send the reset link to.
 * @returns {Promise<string>} Server confirmation message (success-path text, not an error).
 * @throws {import('../errors/ApiError.js').ApiError}
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
    throw resolveApiError(error);
  }
}

/**
 * Sets a new password using the one-time reset token received by email.
 * The reset token is invalidated server-side after a successful call.
 *
 * @param {string} token       - One-time reset token from the email link query param.
 * @param {string} newPassword - New password (8–128 chars).
 * @returns {Promise<string>} Server confirmation message (success-path text, not an error).
 * @throws {import('../errors/ApiError.js').ApiError}
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
    // The generic TOKEN_INVALID/TOKEN_REVOKED catalog text talks about "your session" —
    // accurate for the auth-header flow, misleading here where the token is a one-time
    // reset link, not a login session.
    throw resolveApiError(error, {
      TOKEN_INVALID: 'El enlace de restablecimiento es inválido o ha expirado.',
      TOKEN_REVOKED: 'Este enlace ya fue utilizado.',
    });
  }
}
