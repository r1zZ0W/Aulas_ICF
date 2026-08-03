/**
 * @fileoverview Server-state hook for the academic semesters module.
 * Provides the active semester, the full semester list, and create/update mutations.
 *
 * Query keys:
 *  ['semesters', 'active'] — single active semester (can be null on 404)
 *  ['semesters', 'list']   — full semester catalogue (past, active, future)
 *
 * Both queries are invalidated together via ['semesters'] on any mutation.
 */
import { useQuery } from '@tanstack/react-query';
import {
  getActiveSemester,
  getSemesters,
  createSemester,
  updateSemester,
} from '../../../../api/semesters';
import { useApiMutation } from '../../../../hooks/useApiMutation';

/**
 * @returns {{
 *   activeSemester: object | null,
 *   semesters:      object[],
 *   loading:        boolean,
 *   createMutation: import('@tanstack/react-query').UseMutationResult,
 *   updateMutation: import('@tanstack/react-query').UseMutationResult,
 * }}
 */
export function useSemesters({ listEnabled = true } = {}) {
  // ── Active semester (single object or null on 404) ───────────────────────────
  const { data: activeSemester = null, isFetching: loading } = useQuery({
    queryKey: ['semesters', 'active'],
    queryFn: getActiveSemester,
    retry: false,   // don't retry 404 (expected when no semester is active)
    staleTime: 60_000,
  });

  // ── Full semester list (for the split-button secondary menu) ─────────────────
  // Intentionally NOT used to decide create-vs-edit on the primary action,
  // to avoid blocking the admin from creating new periods when history exists.
  const { data: semesters = [] } = useQuery({
    queryKey: ['semesters', 'list'],
    queryFn: getSemesters,
    staleTime: 60_000,
    enabled: listEnabled,
  });

  // ── Mutations ────────────────────────────────────────────────────────────────
  // ['semesters'] as invalidateKey covers both 'active' and 'list' in one call.

  const createMutation = useApiMutation({
    mutationFn: createSemester,
    invalidateKey: ['semesters'],
    successMessage: 'Semestre creado correctamente.',
  });

  const updateMutation = useApiMutation({
    mutationFn: ({ uuid, payload }) => updateSemester(uuid, payload),
    invalidateKey: ['semesters'],
    successMessage: 'Semestre actualizado correctamente.',
  });

  return {
    activeSemester,
    semesters,
    loading,
    createMutation,
    updateMutation,
  };
}
