import { createContext, useContext, useState, useCallback } from 'react';
import { SESSION_KEYS } from '../utils/session';
import { getRoleFromToken } from '../utils/jwt';

const AuthContext = createContext(null);


/**
 * Reads persisted fields from localStorage and derives the role from the JWT.
 * The role is NEVER read as a raw localStorage string — it always comes from
 * the signed token payload, which the client cannot forge without the server key.
 */
function readPersistedSession() {
  const base = Object.fromEntries(
    Object.values(SESSION_KEYS).map((key) => [key, localStorage.getItem(key)])
  );
  return { ...base, role: getRoleFromToken(base.token) };
}

/** Builds an empty session object with all keys set to null. 
 * @returns {import('../api/auth.js').LoginResponseSchema} An empty session object.
*/
function emptySession() {
  return {
    ...Object.fromEntries(Object.values(SESSION_KEYS).map((key) => [key, null])),
    role: null,
  };
}

/**
 * Provides authentication state and session management to the entire app.
 * Reads the initial session from localStorage so a page refresh keeps the user logged in.
 */
export function AuthProvider({ children }) {
  const [session, setSession] = useState(readPersistedSession);

  /**
   * Writes session fields to localStorage and React state.
   * The role is intentionally excluded from localStorage — it is always
   * re-derived from the JWT so the client string cannot be tampered with.
   *
   * @param {import('../api/auth.js').LoginResponseSchema} sessionData
   */
  const persistSession = useCallback((sessionData) => {
    Object.values(SESSION_KEYS).forEach((key) => {
      localStorage.setItem(key, sessionData[key]);
    });
    setSession({ ...sessionData, role: getRoleFromToken(sessionData.token) });
  }, []);

  /**
   * Updates the persisted session with a partial payload (e.g. name/email after profile edits).
   * Token values are left untouched unless explicitly provided.
   */
  const updateSession = useCallback((sessionPatch) => {
    Object.values(SESSION_KEYS).forEach((key) => {
      if (Object.prototype.hasOwnProperty.call(sessionPatch, key) && sessionPatch[key] !== undefined) {
        localStorage.setItem(key, sessionPatch[key]);
      }
    });
    setSession((current) => ({
      ...current,
      ...sessionPatch,
      role: getRoleFromToken(sessionPatch.token || current.token),
    }));
  }, []);

  /** Removes all session data from localStorage and resets React state. */
  const clearSession = useCallback(() => {
    Object.values(SESSION_KEYS).forEach((key) => localStorage.removeItem(key));
    setSession(emptySession());
  }, []);

  return (
    <AuthContext.Provider
      value={{
        user: session,
        isAuthenticated: !!session.token,
        persistSession,
        updateSession,
        clearSession,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

/**
 * Hook to consume the auth context.
 * Must be used inside an AuthProvider — throws otherwise to catch misconfiguration early.
 *
 * @returns {{ user: object, isAuthenticated: boolean, persistSession: Function, clearSession: Function }}
 */
export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used within AuthProvider');
  return ctx;
}
