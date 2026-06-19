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
 * 2. On success: invalidate `invalidateKey`, show `successMessage`, call optional `onSuccess`.
 * 3. On error: show `toast.error(err.message)` (overridable via `options`).
 *
 * Returns the full `UseMutationResult` so consumers are unchanged.
 *
 * @param {object}   params
 * @param {Function} params.mutationFn       - API call to execute.
 * @param {unknown[]} [params.invalidateKey] - Base query key to invalidate on success.
 *                                             Covers all sub-keys (e.g. `['users']` covers
 *                                             `['users','list',…]` and `['users','stats']`).
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
        queryClient.invalidateQueries({ queryKey: invalidateKey });
      }
      if (successMessage) toast.success(successMessage);
      onSuccess?.(data, variables, context);
    },
    onError: (err) => toast.error(err.message),
    ...options,
  });
}
