/**
 * @fileoverview Server-state hook for a classroom's equipment resources: the full equipment
 * catalog (for the "add resource" picker) and the classroom's current allocations, plus the
 * assign (upsert) and remove mutations. Mirrors the useClassrooms.js pattern.
 *
 * Query keys:
 *   ['resources', 'catalog']                      — full equipment catalog, shared across all
 *                                                    classrooms (see api/resources.js for why the
 *                                                    whole paginated catalog is walked up front).
 *   ['classrooms', classroomUuid, 'resources']     — nested under ['classrooms', …] so it is also
 *                                                    invalidated by any broader ['classrooms']
 *                                                    invalidation (e.g. deleting the classroom).
 */
import { useQuery } from '@tanstack/react-query';
import {
  getResourceCatalog,
  getClassroomResources,
  assignClassroomResource,
  removeClassroomResource,
} from '../../../../api/resources.js';
import { useApiMutation } from '../../../../hooks/useApiMutation.js';

/**
 * @param {string|null} classroomUuid - UUID of the classroom whose resources are being managed.
 *   Pass `null` while no classroom is targeted (e.g. modal closed) to keep the allocations query
 *   disabled.
 *
 * @returns {{
 *   catalog: object[], catalogLoading: boolean, catalogError: boolean, refetchCatalog: Function,
 *   resources: object[], resourcesLoading: boolean,
 *   assignMutation: import('@tanstack/react-query').UseMutationResult,
 *   removeMutation: import('@tanstack/react-query').UseMutationResult,
 * }}
 */
export function useClassroomResources(classroomUuid) {
  // ── Full equipment catalog ────────────────────────────────────────────────────
  const catalogQuery = useQuery({
    queryKey: ['resources', 'catalog'],
    queryFn: getResourceCatalog,
    staleTime: 60_000,
  });

  // ── Current allocations for this classroom ────────────────────────────────────
  const resourcesQuery = useQuery({
    queryKey: ['classrooms', classroomUuid, 'resources'],
    queryFn: () => getClassroomResources(classroomUuid),
    enabled: !!classroomUuid,
  });

  // ── Mutations ──────────────────────────────────────────────────────────────────
  const assignMutation = useApiMutation({
    mutationFn: ({ classroomUuid: uuid, resourceUuid, quantity }) =>
      assignClassroomResource(uuid, { resourceUuid, quantity }),
    invalidateKey: ['classrooms', classroomUuid, 'resources'],
    successMessage: 'Recurso del aula actualizado correctamente.',
  });

  const removeMutation = useApiMutation({
    mutationFn: ({ classroomUuid: uuid, resourceUuid }) =>
      removeClassroomResource(uuid, resourceUuid),
    invalidateKey: ['classrooms', classroomUuid, 'resources'],
    successMessage: 'Recurso eliminado del aula.',
  });

  return {
    catalog: catalogQuery.data ?? [],
    catalogLoading: catalogQuery.isLoading,
    catalogError: catalogQuery.isError,
    refetchCatalog: catalogQuery.refetch,
    resources: resourcesQuery.data ?? [],
    resourcesLoading: resourcesQuery.isLoading,
    assignMutation,
    removeMutation,
  };
}
