import { useAuth } from './context/AuthContext';
import PrivateRouter from './routes/PrivateRouter';
import PublicRouter from './routes/PublicRouter';

/**
 * Root component. Delegates routing entirely to context:
 * AuthProvider holds the source of truth for isAuthenticated, so this
 * component re-renders automatically on login and logout without
 * reading localStorage directly.
 */
export default function App() {
  const { isAuthenticated } = useAuth();
  return isAuthenticated ? <PrivateRouter /> : <PublicRouter />;
}
