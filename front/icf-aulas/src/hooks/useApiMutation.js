/**
 * @fileoverview Factory hook that centralises the use of `useQueryClient` and
 * the standard CRUD mutation pattern across all domain hooks.
 *
 * Every module that needs to mutate server state and then invalidate a React
 * Query cache key (plus show a toast) should use this hook instead of calling
 * `useQueryClient` directly.
 */
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from '../utils/toast.jsx';

/**
 * Wraps `useMutation` with the standard CRUD lifecycle:
 * 1. Call `mutationFn`.
 * 2. On success: invalidate `invalidateKey` (one or many), show `successMessage`, call optional `onSuccess`.
 * 3. On error: route `err.fieldErrors` (if any) to the form via `setServerErrors`, and toast
 *    whatever didn't land on a control — see the field-error routing rules below.
 *
 * Returns the full `UseMutationResult` so consumers are unchanged.
 *
 * @param {object}   params
 * @param {Function} params.mutationFn       - API call to execute.
 * @param {unknown[] | unknown[][]} [params.invalidateKey]
 *   Base query key (or list of keys) to invalidate on success.
 *   Single key:  `['users']`              — covers `['users','list',…]`, `['users','stats']`, etc.
 *   Multi-key:   `[['reservations','availability'], ['reservations','history']]`
 *                — each sub-array is invalidated independently (surgical cache updates).
 *   Backward-compatible: existing callers passing a flat array (single key) are unaffected.
 * @param {string}   [params.successMessage] - Toast text shown after a successful mutation.
 * @param {Function} [params.onSuccess]      - Extra callback invoked after invalidation.
 * @param {Function} [params.setServerErrors] - The `setServerErrors` returned by this form's
 *   `useZodForm`. When given and the error carries `fieldErrors`, those are routed onto the
 *   form's inputs instead of (or in addition to) a toast — see field-error routing rules below.
 * @param {Function} [params.onError]        - Extra callback invoked after error routing.
 * @param {object}   [params.options]        - Any additional `useMutation` options;
 *                                             these are spread last and can override defaults.
 * @returns {import('@tanstack/react-query').UseMutationResult}
 */
export function useApiMutation({
  mutationFn,
  invalidateKey,
  successMessage,
  onSuccess,
  setServerErrors,
  onError,
  ...options
}) {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn,
    onSuccess: (data, variables, context) => {
      if (invalidateKey) {
        // Support both single key ['foo'] and multiple keys [['foo'], ['bar']]
        const isMulti = Array.isArray(invalidateKey[0]);
        const keys = isMulti ? invalidateKey : [invalidateKey];
        keys.forEach(key => queryClient.invalidateQueries({ queryKey: key }));
      }
      if (successMessage) toast.success(successMessage);
      onSuccess?.(data, variables, context);
    },
    // Field-error routing: a fieldErrors entry that lands on a control (via
    // setServerErrors' dtoMap) needs no toast — the red border under the input is
    // the message. But an entry with NO matching control (an "orphan" — a DTO
    // field missing from the map, or explicitly mapped to null) must still toast
    // its own text: dropping it silently means the user clicks Guardar again and
    // nothing happens, with no visible reason why. If there's no setServerErrors
    // (non-form mutations) or no fieldErrors at all, fall back to the plain
    // err.message toast — today's behavior.
    onError: (err, variables, context) => {
      let handled = false;
      if (setServerErrors && err.fieldErrors) {
        const { applied, orphanMessages } = setServerErrors(err.fieldErrors);
        orphanMessages.forEach((message) => toast.error(message));
        handled = applied.length > 0 || orphanMessages.length > 0;
      }
      if (!handled) toast.error(err.message);
      onError?.(err, variables, context);
    },
    ...options,
  });
}
