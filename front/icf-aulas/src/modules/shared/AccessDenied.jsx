import { Link } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext';
import { getDashboardRoute } from '../../utils/roles';

export default function AccessDenied() {
  const { user } = useAuth();
  const dashboardRoute = getDashboardRoute(user.role);

  return (
    <div className="d-flex flex-column align-items-center justify-content-center" style={{ minHeight: '60vh' }}>
      <i className="bi bi-shield-lock fs-1 text-secondary mb-3" />
      <h2 className="fw-semibold mb-1">Acceso denegado</h2>
      <p className="text-secondary mb-4">No tienes permiso para ver esta página.</p>
      <Link to={dashboardRoute} className="btn btn-primary px-4">
        Ir al dashboard
      </Link>
    </div>
  );
}
