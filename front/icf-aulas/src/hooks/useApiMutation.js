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
 * 3. On error: show `toast.error(err.message)` (overridable via `options`).
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
 * @param {object}   [params.options]        - Any additional `useMutation` options;
 *                                             these are spread last and can override defaults.
 * @returns {import('@tanstack/react-query').UseMutationResult}
 */
export function useApiMutation({
  mutationFn,
  invalidateKey,
  successMessage,
  onSuccess,
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
    onError: (err) => toast.error(err.message),
    ...options,
  });
}
