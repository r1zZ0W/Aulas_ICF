import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { PRIVATE_ROUTES } from './routeConfig';
import { getDashboardRoute } from '../utils/roles';
import PrivateLayout from '../layouts/PrivateLayout';
import RoleGuard from './RoleGuard';

import Error401 from '../errors/Error401.jsx';
import Error403 from '../errors/Error403.jsx';
import Error404 from '../errors/Error404.jsx';

export default function PrivateRouter() {
    const { isAuthenticated, user } = useAuth();

    // Extra protection in case it renders without a session
    if (!isAuthenticated)
        return <Navigate to="/401" replace />;

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
                <Route path="/404" element={<Error404 />} />

                {/* Main route according to user role */}
                <Route
                    path="/"
                    element={<Navigate to={defaultRoute} replace />}
                />

                {/* Any route that doesn't exist inside the system */}
                <Route path="*" element={<Error404 />} />
            </Route>
        </Routes>
    );
}