import { Navigate } from 'react-router-dom';

/**
 * Protects a route by checking whether the current user's role is in the
 * allowed list. If the user lacks permission we show the standard 403 page
 * for "Acceso denegado". Historically the app used `/access-denied` which
 * wasn't declared in the router, causing a 404. We redirect to `/403` which
 * is mapped in `PrivateRouter`.
 *
 * @param {{ allowedRoles: string[], userRole: string, children: JSX.Element }} props
 */
export default function RoleGuard({ allowedRoles, userRole, children }) {
  if (!allowedRoles.includes(userRole))
    return <Navigate to="/403" replace />;

  return children;
}
