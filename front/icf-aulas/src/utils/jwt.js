import { jwtDecode } from 'jwt-decode';

/**
 * Decodes a JWT and returns its payload claims.
 * Returns null if the token is absent, structurally malformed, or past its
 * expiration time — treating all three as "no valid session".
 *
 * SECURITY NOTE: jwtDecode only base64-decodes the payload; it does NOT verify
 * the signature. Signature verification is the backend's job on every request.
 * What we gain here is reading the role that the server encoded — the user
 * cannot forge it without the server's private key.
 *
 * @param {string | null} token - Raw JWT string (without "Bearer " prefix).
 * @returns {object | null} Decoded claims, or null.
 */
export function decodeToken(token) {
  if (!token) return null;
  try {
    const claims = jwtDecode(token);
    if (claims.exp && claims.exp * 1000 < Date.now()) return null;
    return claims;
  } catch {
    return null;
  }
}

/**
 * Extracts the user's role directly from the JWT payload claims.
 *
 * This is the single trusted source of truth for the role in the frontend.
 * Never read `localStorage.getItem('role')` for access-control decisions — that
 * string can be edited by any user in DevTools. The JWT payload cannot be
 * modified without invalidating the server's signature.
 *
 * @param {string | null} token - Raw JWT access token.
 * @returns {string | null} The role claim, or null if the token is invalid.
 *
 * @example
 * const role = getRoleFromToken(localStorage.getItem('token'));
 * // role === 'ADMIN' | 'MAESTRO' | null
 */
export function getRoleFromToken(token) {
  return decodeToken(token)?.role ?? null;
}
