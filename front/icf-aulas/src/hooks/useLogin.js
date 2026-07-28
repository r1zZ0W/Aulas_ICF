import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { login } from '../api/auth.js';
import { useZodForm } from './useZodForm.js';
import { LoginRequestSchema } from '../schemas/login/loginRequest.js';
import { useAuth } from '../context/AuthContext.jsx';
import { getDashboardRoute } from '../utils/roles.js';

// Re-exported for the few legacy callers that imported SESSION_KEYS from here.
export { SESSION_KEYS } from '../utils/session.js';

const EMPTY_LOGIN = { username: '', password: '' };

/**
 * Encapsulates all login form logic: field state, client-side validation (via useZodForm +
 * LoginRequestSchema), server call, session persistence, and role-based navigation.
 *
 * Validation strategy (mirrors useUsersForm/useResourcesForm):
 *   - onBlur per field  → makes that field's error visible immediately.
 *   - onChange on a touched field → clears the error in real time as the user types.
 *   - handleSubmit → calls validateAll() first, so clicking "Iniciar sesión" on a blank
 *     form still illuminates both required fields at once.
 *
 * `errors._form` is a global, non-field error (bad credentials, network/server issues) —
 * intentionally kept OUTSIDE the Zod-backed field errors, since it isn't a validation
 * failure of any single input.
 *
 * @returns {{
 *   form:         { username: string, password: string },
 *   errors:       Record<string, string | null>,
 *   loading:      boolean,
 *   handleChange: (field: string) => (e: React.ChangeEvent) => void,
 *   handleBlur:   (field: string) => () => void,
 *   handleSubmit: (e: React.FormEvent) => Promise<void>,
 * }}
 */
export function useLogin() {
  const zod = useZodForm(EMPTY_LOGIN, LoginRequestSchema);
  const [formError, setFormError] = useState('');
  const [loading, setLoading] = useState(false);

  const navigate = useNavigate();
  const { persistSession } = useAuth();

  const handleChange = (field) => (e) => {
    zod.handleChange(field, e.target.value);
    setFormError('');
  };

  const handleBlur = (field) => () => zod.handleBlur(field);

  /**
   * Submits credentials to the backend.
   * On success, persists the session via AuthContext and redirects to the
   * role-specific dashboard. On failure, surfaces a user-facing error.
   */
  const handleSubmit = async (e) => {
    e.preventDefault();

    // validateAll() marks every field as touched and returns false if Zod fails — this
    // illuminates both required-field errors even on a direct submit with a blank form.
    const isValid = zod.validateAll();
    if (!isValid) return;

    setLoading(true);

    try {
      const { username, password } = zod.formData;
      const session = await login({ username, password });

      const route = getDashboardRoute(session.role);
      if (route === '/login') {
        setFormError('No tienes acceso a este sistema.');
        return;
      }

      persistSession(session);
      navigate(route, { replace: true });
    } catch (err) {
      // login() always rejects with an ApiError now — resolveApiError already resolved
      // err.code (INVALID_CREDENTIALS, ACCOUNT_LOCKED, RATE_LIMITED, ...) to Spanish text
      // via the catalog, so there's nothing left to branch on here.
      setFormError(err.message ?? 'Ocurrió un error inesperado.');
    } finally {
      setLoading(false);
    }
  };

  return {
    form: zod.formData,
    errors: { ...zod.errors, _form: formError },
    loading,
    handleChange,
    handleBlur,
    handleSubmit,
  };
}
