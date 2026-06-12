import { Routes, Route, Navigate } from 'react-router-dom';
import Login from '../modules/public/pages/Login';
import Error401 from '../errors/Error401.jsx';
import Error403 from '../errors/Error403.jsx';
import Error404 from '../errors/Error404.jsx';

export default function PublicRouter() {
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
                <Route path="*" element={<Navigate to="/401" replace />} />
            </Routes>
        </div>
    );
}