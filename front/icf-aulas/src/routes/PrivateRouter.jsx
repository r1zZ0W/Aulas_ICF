import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { PRIVATE_ROUTES } from './routeConfig';
import { getDashboardRoute } from '../utils/roles';
import PrivateLayout from '../layouts/PrivateLayout';
import RoleGuard from './RoleGuard';
import AccessDenied from '../modules/shared/AccessDenied';

/**
 * Router for authenticated users.
 *
 * Responsibilities:
 *   1. Redirects to /login if the session is missing (defensive check —
 *      App.jsx already gates this component, but keeping it here makes
 *      PrivateRouter self-contained and safe to render in isolation).
 *   2. Renders the private layout shell (Sidebar + main area) via PrivateLayout.
 *   3. Maps every entry in PRIVATE_ROUTES to a React Router Route, wrapping
 *      each in RoleGuard so unauthorized access is blocked at the route level.
 *   4. Falls back to the role-specific dashboard for unknown paths.
 *
 * To add a protected route, edit routeConfig.jsx — this file stays unchanged.
 */
export default function PrivateRouter() {
  const { isAuthenticated, user } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  const defaultRoute = getDashboardRoute(user.role);

  return (
    <Routes>
      <Route element={<PrivateLayout />}>
        {PRIVATE_ROUTES.map(({ path, element, allowedRoles }) => (
          <Route
            key={path}
            path={path}
            element={
              <RoleGuard allowedRoles={allowedRoles} userRole={user.role}>
                {element}
              </RoleGuard>
            }
          />
        ))}

        <Route path="/access-denied" element={<AccessDenied />} />

        {/* Redirect root and unknown paths to the role's default dashboard */}
        <Route path="/" element={<Navigate to={defaultRoute} replace />} />
        <Route path="*" element={<Navigate to={defaultRoute} replace />} />
      </Route>
    </Routes>
  );
}
