import { Routes, Route, Navigate } from 'react-router-dom';
import { lazy } from 'react';

import { useAuth } from '../context/AuthContext';
import { PRIVATE_ROUTES } from './routeConfig';
import { getDashboardRoute } from '../utils/roles';
import PrivateLayout from '../layouts/PrivateLayout';
import RoleGuard from './RoleGuard';

const Error401 = lazy(() => import('../errors/Error401.jsx'));
const Error403 = lazy(() => import('../errors/Error403.jsx'));
const Error404 = lazy(() => import('../errors/Error404.jsx'));

export default function PrivateRouter() {
    const { isAuthenticated, user } = useAuth();

    // Extra protection in case it renders without a session.
    // Redirects to /login (not /401) for consistency with the public router catch-all.
    if (!isAuthenticated)
        return <Navigate to="/login" replace state={{ reason: 'no-session' }} />;

    const defaultRoute = getDashboardRoute(user.role);

    return (
        <Routes>
            <Route element={<PrivateLayout />}>
                {/* Protected routes with role control */}
                {PRIVATE_ROUTES.map(({ path, element, allowedRoles }) => (
                    <Route
                        key={path}
                        path={path}
                        element={
                            <RoleGuard
                                allowedRoles={allowedRoles}
                                userRole={user.role}
                            >
                                {element}
                            </RoleGuard>
                        }
                    />
                ))}

                {/* Error pages inside the system */}
                <Route path="/401" element={<Error401 />} />
                <Route path="/403" element={<Error403 />} />
                {/* Backwards-compatible alias used in some guards/components */}
                <Route path="/access-denied" element={<Error403 />} />
                <Route path="/404" element={<Error404 />} />

                {/* Main route according to user role */}
                <Route
                    path="/"
                    element={<Navigate to={defaultRoute} replace />}
                />

                {/* Dashboard alias: redirect to role-specific landing */}
                <Route path="/dashboard" element={<Navigate to={defaultRoute} replace />} />

                {/* Any route that doesn't exist inside the system */}
                <Route path="*" element={<Error404 />} />
            </Route>
        </Routes>
    );
}