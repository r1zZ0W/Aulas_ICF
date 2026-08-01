import { Routes, Route, Navigate, useLocation } from 'react-router-dom';
import { lazy } from 'react';
// Eager: this is the first paint for every unauthenticated visitor, and its own
// dependency graph is light (Button/Input/Modal/useLogin + Zod) — see the login
// bundle-leak plan for the audit. Loading it lazily meant a login → chunk fetch →
// render waterfall for zero benefit, since it's needed immediately anyway.
import Login from '../modules/public/pages/Login';

// These stay lazy: reachable only from an error condition, never the first paint.
const Error401 = lazy(() => import('../errors/Error401'));
const Error403 = lazy(() => import('../errors/Error403'));
const Error404 = lazy(() => import('../errors/Error404'));
import { PRIVATE_PATHS } from './routes.meta';

export default function PublicRouter() {
    const location = useLocation();

    // If the unauthenticated user attempts to reach a known private route
    // show the 401 page. For unknown public routes show the 404 page.
    // NOTE: pulls from routes.meta (paths only) rather than routeConfig, so
    // the public bundle never imports the private pages' lazy() defs/icons.
    const privatePaths = [
        '/dashboard',
        ...PRIVATE_PATHS,
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

                {/* Cualquier intento de entrar a una ruta sin sesión:
                    - Rutas privadas → /login (con motivo para mostrar el modal)
                    - Rutas desconocidas → 404
                    NOTA: usar /login aquí (no /401) elimina la carrera de prioridades del
                    logout: aunque clearSession() gane al navigate(), el catch-all ya no
                    muestra la pantalla de error porque ambos destinos son /login. */}
                <Route
                    path="*"
                    element={
                        isAttemptingPrivate
                            ? <Navigate to="/login" replace state={{ reason: 'no-session' }} />
                            : <Error404 />
                    }
                />
            </Routes>
        </div>
    );
}