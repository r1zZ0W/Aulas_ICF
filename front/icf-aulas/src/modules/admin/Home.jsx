import { useAuth } from '../../context/AuthContext';

export default function AdminHome() {
  const { user } = useAuth();

  return (
    <div>
      <h1 className="fw-semibold mb-1">Dashboard</h1>
      <p className="text-secondary">Bienvenido, {user.name}</p>
    </div>
  );
}
