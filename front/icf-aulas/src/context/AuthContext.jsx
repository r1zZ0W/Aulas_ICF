import { createContext, useContext, useState, useCallback } from 'react';
import { SESSION_KEYS } from '../utils/session';

const AuthContext = createContext(null);

/** Reads all session fields from localStorage into a plain object. */
function readPersistedSession() {
  return Object.fromEntries(
    Object.values(SESSION_KEYS).map((key) => [key, localStorage.getItem(key)])
  );
}

/** Builds an empty session object with all keys set to null. */
function emptySession() {
  return Object.fromEntries(Object.values(SESSION_KEYS).map((key) => [key, null]));
}

/**
 * Provides authentication state and session management to the entire app.
 * Reads the initial session from localStorage so a page refresh keeps the user logged in.
 */
export function AuthProvider({ children }) {
  const [session, setSession] = useState(readPersistedSession);

  /**
   * Writes all session fields to both localStorage and React state.
   * SESSION_KEYS values are identical to the property names on AuthTokens,
   * so iterating over them covers every field without manual mapping.
   *
   * @param {import('../api/auth.js').AuthTokens} sessionData
   */
  const persistSession = useCallback((sessionData) => {
    Object.values(SESSION_KEYS).forEach((key) => {
      localStorage.setItem(key, sessionData[key]);
    });
    setSession(sessionData);
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
