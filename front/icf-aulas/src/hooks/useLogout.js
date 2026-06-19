import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { logout } from '../api/auth';

/**
 * Handles the full logout flow: revokes the token on the server,
 * then clears the local session and navigates to /login regardless of
 * whether the API call succeeded.
 *
 * The explicit navigate('/login') is necessary because swapping routers in
 * App.jsx does not change the current URL — the PublicRouter catch-all would
 * otherwise try to match the stale private path. Both destinations now point to
 * /login so the redirect is deterministic regardless of which update wins the
 * React render race (clearSession vs navigate).
 *
 * `loggingOut` is exposed so the Sidebar can render the same LoadingOverlay
 * that the Login page shows during its own async call.
 *
 * `sessionStorage.authReason = 'logout'` marks an intentional logout so the
 * Login page suppresses the "session expired" modal for this case.
 */
export function useLogout() {
  const { user, clearSession } = useAuth();
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);

  const handleLogout = async () => {
    setLoggingOut(true);
    // Mark intent BEFORE the await so the Login modal is suppressed even if
    // the API call fails and we fall through to finally.
    sessionStorage.setItem('authReason', 'logout');

    try {
      await logout(user.token, user.refreshToken);
    } catch (error) {
      console.warn("Error al cerrar sesión:", error);
      // Server-side revocation failing (e.g. expired token) is non-fatal.
      // The local session is always cleared so the user is logged out locally.
    } finally {
      clearSession();
      navigate('/login', { replace: true });
      // loggingOut stays true — the component unmounts on navigate so there
      // is no state-update-on-unmounted-component warning.
    }
  };

  return { handleLogout, loggingOut };
}
