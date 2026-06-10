import { useAuth } from '../context/AuthContext';
import { logout } from '../api/auth';

/**
 * Handles the full logout flow: revokes the token on the server,
 * then clears the local session regardless of whether the API call succeeded.
 * App.jsx reacts to isAuthenticated becoming false and redirects to /login
 * automatically — no explicit navigate() call needed here.
 */
export function useLogout() {
  const { user, clearSession } = useAuth();

  const handleLogout = async () => {
    try {
      await logout(user.token, user.refreshToken);
    } catch {
      // Server-side revocation failing (e.g. expired token) is non-fatal.
      // The local session is always cleared so the user is logged out locally.
    } finally {
      clearSession();
    }
  };

  return { handleLogout };
}
