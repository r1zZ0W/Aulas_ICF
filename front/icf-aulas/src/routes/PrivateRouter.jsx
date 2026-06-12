import { Routes, Route, Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';
import { PRIVATE_ROUTES } from './routeConfig';
import { getDashboardRoute } from '../utils/roles';
import PrivateLayout from '../layouts/PrivateLayout';
import RoleGuard from './RoleGuard';
// import AccessDenied from '../modules/shared/AccessDenied';
import Error401 from '../errors/Error401.jsx';
import Error403 from '../errors/Error403.jsx';
import Error404 from '../errors/Error404.jsx';

export default function PrivateRouter() {
    const { isAuthenticated, user } = useAuth();

    // Protección extra en caso de que se renderice sin sesión
    if (!isAuthenticated) {
        return <Navigate to="/401" replace />;
    }

    const defaultRoute = getDashboardRoute(user.role);

    return (
        <Routes>
            <Route element={<PrivateLayout />}>
                {/* Rutas protegidas con control de roles */}
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

                {/* Páginas de error dentro del sistema */}
                <Route path="/401" element={<Error401 />} />
                <Route path="/403" element={<Error403 />} />
                <Route path="/404" element={<Error404 />} />

                {/* Ruta principal según el rol del usuario */}
                <Route
                    path="/"
                    element={<Navigate to={defaultRoute} replace />}
                />

                {/* Cualquier ruta que no exista dentro del sistema */}
                <Route path="*" element={<Error404 />} />
            </Route>
        </Routes>
    );
}