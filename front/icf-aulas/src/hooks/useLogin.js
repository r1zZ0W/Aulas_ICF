import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { login } from '../api/auth.js';
import { validateLoginUsername, validatePasswordLogin } from '../utils/validations.js';
import { useAuth } from '../context/AuthContext.jsx';
import { getDashboardRoute } from '../utils/roles.js';

// Re-exported for the few legacy callers that imported SESSION_KEYS from here.
export { SESSION_KEYS } from '../utils/session.js';

/**
 * Encapsulates all login form logic: field state, client-side validation,
 * server call, session persistence, and role-based navigation.
 *
 * @returns {{
 *   form:         { username: string, password: string },
 *   errors:       Record<string, string | null>,
 *   loading:      boolean,
 *   handleChange: (field: string) => (e: React.ChangeEvent) => void,
 *   handleSubmit: (e: React.FormEvent) => Promise<void>,
 * }}
 */
export function useLogin() {
  const [form, setForm] = useState({ username: '', password: '' });
  const [errors, setErrors] = useState({});
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();
  const { persistSession } = useAuth();

  const handleChange = (field) => (e) => {
    const value = e.target.value;
    setForm((prev) => ({ ...prev, [field]: value }));
    setErrors((prev) => ({ ...prev, [field]: null, _form: null }));
  };

  const validateAll = () => {
    const fieldErrors = {
      username: validateLoginUsername(form.username),
      password: validatePasswordLogin(form.password),
    };
    setErrors(fieldErrors);
    return !Object.values(fieldErrors).some(Boolean);
  };

  /**
   * Submits credentials to the backend.
   * On success, persists the session via AuthContext and redirects to the
   * role-specific dashboard. On failure, surfaces a user-facing error.
   */
  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!validateAll()) return;

    setLoading(true);

    try {
      const session = await login({ username: form.username, password: form.password });

      const route = getDashboardRoute(session.role);
      if (route === '/login') {
        setErrors((prev) => ({ ...prev, _form: 'No tienes acceso a este sistema.' }));
        return;
      }

      persistSession(session);
      navigate(route, { replace: true });
    } catch (err) {

      const msg = (err instanceof Error && err.name === 'ZodError')
        ? 'Ocurrió un problema de compatibilidad. Intenta de nuevo.'
        : (err.message ?? 'Ocurrió un error inesperado.');

      setErrors((prev) => ({
        ...prev,
        _form: msg,
      }));

    } finally {
      setLoading(false);
    }
  };

  return { form, errors, loading, handleChange, handleSubmit };
}
