import { Navigate } from 'react-router-dom';

/**
 * Protects a route by checking whether the current user's role is in the
 * allowed list. Redirects to /access-denied if not authorized, letting the
 * user know they exist but lack permission — as opposed to a hard /login redirect.
 *
 * @param {{ allowedRoles: string[], userRole: string, children: JSX.Element }} props
 */
export default function RoleGuard({ allowedRoles, userRole, children }) {
  if (!allowedRoles.includes(userRole)) {
    return <Navigate to="/access-denied" replace />;
  }
  return children;
}
