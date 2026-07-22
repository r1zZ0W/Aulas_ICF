/**
 * @fileoverview Custom hook that encapsulates all server-state logic for the global
 * "Gestión de Recursos" admin page: paginated + searchable resource catalog, aggregated
 * stats, and create/update/delete mutations via React Query.
 *
 * `invalidateKey: ['resources']` is a single prefix that covers `['resources','list',…]`,
 * `['resources','stats']`, AND `['resources','catalog']` (the full-catalog query used by
 * ClassroomResourcesModal's "add resource" picker) — so any CRUD here keeps that picker's
 * option list in sync too.
 */
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import {
  getResources,
  getResourceStats,
  createResource,
  updateResource,
  deleteResource,
} from '../../../../api/resources.js';
import { useApiMutation } from '../../../../hooks/useApiMutation.js';

/**
 * @typedef {import('../../../../schemas/resource/resourceResponse.js').ResourceResponseSchema} Resource
 * @typedef {{ totalTypes: number, totalUnits: number }} ResourceStats
 */

/**
 * @param {object} [params={}]
 * @param {string}       [params.search]    - Server-side free-text filter.
 * @param {number}       [params.page=0]    - Zero-based current page.
 * @param {number}       [params.size=20]   - Page size.
 * @param {string}       [params.sort]      - Sort field.
 * @param {'asc'|'desc'} [params.direction] - Sort direction.
 *
 * @returns {{
 *   resources:          Resource[],
 *   totalElements:      number,
 *   totalPages:         number,
 *   stats:              ResourceStats,
 *   resourcesLoading:   boolean,
 *   createMutation:     import('@tanstack/react-query').UseMutationResult,
 *   updateMutation:     import('@tanstack/react-query').UseMutationResult,
 *   deleteMutation:     import('@tanstack/react-query').UseMutationResult,
 * }}
 */
export function useResources({ search, page = 0, size = 20, sort, direction } = {}) {

  // ── Paginated resource list ──────────────────────────────────────────────────
  const {
    data: pageData,
    isLoading: resourcesLoading,
  } = useQuery({
    queryKey: ['resources', 'list', { search, page, size, sort, direction }],
    queryFn: () => getResources({ search, page, size, sort, direction }),
    placeholderData: keepPreviousData,
  });

  const resources = pageData?.items ?? [];
  const totalElements = pageData?.totalElements ?? 0;
  const totalPages = pageData?.totalPages ?? 1;

  // ── Aggregated stats (total types / total units) ─────────────────────────────
  const { data: stats = { totalTypes: 0, totalUnits: 0 } } = useQuery({
    queryKey: ['resources', 'stats'],
    queryFn: getResourceStats,
  });

  // ── Mutations ────────────────────────────────────────────────────────────────
  const createMutation = useApiMutation({
    mutationFn: createResource,
    invalidateKey: ['resources'],
    successMessage: 'Recurso creado correctamente.',
  });

  const updateMutation = useApiMutation({
    mutationFn: ({ uuid, payload }) => updateResource(uuid, payload),
    invalidateKey: ['resources'],
    successMessage: 'Recurso actualizado correctamente.',
  });

  const deleteMutation = useApiMutation({
    mutationFn: deleteResource,
    invalidateKey: ['resources'],
    successMessage: 'Recurso eliminado correctamente.',
  });

  return {
    resources,
    totalElements,
    totalPages,
    stats,
    resourcesLoading,
    createMutation,
    updateMutation,
    deleteMutation,
  };
}
