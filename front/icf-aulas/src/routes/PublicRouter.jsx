import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import Login from '../modules/public/pages/Login';
import Error401 from '../errors/Error401.jsx';
import Error403 from '../errors/Error403.jsx';
import Error404 from '../errors/Error404.jsx';
import { PRIVATE_ROUTES } from './routeConfig';

export default function PublicRouter() {
    const location = useLocation();

    // If the unauthenticated user attempts to reach a known private route
    // show the 401 page. For unknown public routes show the 404 page.
    const privatePaths = [
        '/dashboard',
        // include all private routes from the central config
        ...PRIVATE_ROUTES.map((r) => r.path),
    ];

    const isAttemptingPrivate = privatePaths.some((p) =>
        location.pathname === p || location.pathname.startsWith(`${p}/`)
    );

    return (
        <div className="page-overflow-wrapper">
            <Routes>
                {/* Redirección inicial al login */}
                <Route path="/" element={<Navigate to="/login" replace />} />

                {/* Página de inicio de sesión */}
                <Route path="/login" element={<Login />} />

                {/* Páginas de error accesibles sin sesión */}
                <Route path="/401" element={<Error401 />} />
                <Route path="/403" element={<Error403 />} />
                <Route path="/404" element={<Error404 />} />

                {/* Cualquier intento de entrar a una ruta sin sesión */}
                <Route
                    path="*"
                    element={isAttemptingPrivate ? <Navigate to="/401" replace /> : <Error404 />}
                />
            </Routes>
        </div>
    );
}