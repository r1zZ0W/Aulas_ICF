import { lazy, Suspense } from 'react';
import { Toaster } from 'sonner';
import { useAuth } from './context/AuthContext';
import LoadingOverlay from './components/LoadingOverlay/LoadingOverlay';
import PublicRouter from './routes/PublicRouter';

// PrivateRouter (and every page it renders) stays lazy: an unauthenticated visitor on
// /login should never pay for the dashboard bundle. PublicRouter is imported eagerly
// (see below) so the login form itself isn't gated behind an extra chunk fetch.
const PrivateRouter = lazy(() => import('./routes/PrivateRouter'));

/**
 * Root component. Delegates routing entirely to context:
 * AuthProvider holds the source of truth for isAuthenticated, so this
 * component re-renders automatically on login and logout without
 * reading localStorage directly.
 *
 * A single <Suspense> boundary at this level covers all lazy-loaded pages
 * in both PublicRouter and PrivateRouter.
 *
 * <Toaster /> lives here (not inside PrivateLayout) so public routes — login, forgot/reset
 * password — can also show a toast; those pages error out just as often as private ones and
 * previously had no toast channel at all.
 */
export default function App() {
  const { isAuthenticated } = useAuth();
  return (
    <Suspense fallback={<LoadingOverlay />}>
      {isAuthenticated ? <PrivateRouter /> : <PublicRouter />}
      <Toaster position="bottom-right" richColors={false} />
    </Suspense>
  );
}
