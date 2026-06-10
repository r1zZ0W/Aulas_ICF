import { Routes, Route, Navigate } from 'react-router-dom';
import Login from '../modules/public/pages/Login';

/** Router for unauthenticated pages. */
export default function PublicRouter() {
  return (
    <div className="page-overflow-wrapper">
      <Routes>
        <Route path="/" element={<Navigate to="/login" replace />} />
        <Route path="/login" element={<Login />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    </div>
  );
}
