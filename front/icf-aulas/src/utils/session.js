/**
 * LocalStorage keys for the authenticated session.
 * Values double as property names on the AuthTokens response object,
 * so they can be used to both read from localStorage and index into the
 * session data returned by the login API.
 */
export const SESSION_KEYS = {
  TOKEN: 'token',
  REFRESH_TOKEN: 'refreshToken',
  UUID: 'uuid',
  NAME: 'name',
  EMAIL: 'email',
  // ROLE is intentionally absent — it is always derived from JWT claims at
  // runtime and never stored as a plain string that the client could tamper with.
};
