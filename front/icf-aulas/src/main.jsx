import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider, QueryCache } from '@tanstack/react-query';
import { AuthProvider } from './context/AuthContext';
import { ApiError } from './errors/ApiError.js';
import { toast } from './utils/toast.jsx';

import './styles/reset.css';
import './styles/utilities.css';
import './styles/theme.css';

import App from './App.jsx';

const queryClient = new QueryClient({
  // Queries render their failure (isError + <ErrorBanner>) — mutations toast, via
  // useApiMutation's onError, which needs per-hook context (setServerErrors) a global
  // handler can't have. A query opts into a toast too only by setting
  // `meta: { toastOnError: true }`; without it, a fetch failure never floats a toast on
  // top of whatever region already renders its own error state.
  queryCache: new QueryCache({
    onError: (error, query) => {
      if (query.meta?.toastOnError) toast.error(error.message ?? 'No se pudo cargar la información.');
    },
  }),
  defaultOptions: {
    queries: {
      staleTime: 5 * 60 * 1000, // 5 min
      // A 401/403 is a known, stable outcome (invalid session / no permission) — retrying
      // it wastes a request and just delays the error reaching the user. Keep the previous
      // retry:1 behavior for every other failure.
      retry: (failureCount, error) => {
        if (error instanceof ApiError && (error.status === 401 || error.status === 403)) return false;
        return failureCount < 1;
      },
    },
  },
});

createRoot(document.getElementById('root')).render(
  <BrowserRouter basename={import.meta.env.VITE_ROUTER_BASENAME}>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </QueryClientProvider>
  </BrowserRouter>,
);
