import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { logout } from '../api/auth';

/**
 * Handles the full logout flow: revokes the token on the server,
 * then clears the local session and navigates to /login regardless of
 * whether the API call succeeded.
 *
 * The explicit navigate('/login') is necessary because swapping routers in
 * App.jsx does not change the current URL — without it the old private path
 * stays in the address bar and PublicRouter's catch-all redirects to /401.
 */
export function useLogout() {
  const { user, clearSession } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    try {
      await logout(user.token, user.refreshToken);
    } catch (error) {
      console.warn("Error al cerrar sesión:", error);
      // Server-side revocation failing (e.g. expired token) is non-fatal.
      // The local session is always cleared so the user is logged out locally.
    } finally {
      clearSession();
      navigate('/login', { replace: true });
    }
  };

  return { handleLogout };
}
