/**
 * @fileoverview Custom hook that encapsulates all server-state logic for the
 * Classrooms page: paginated list, aggregated stats, and create/update/delete
 * mutations via React Query.
 *
 * Also exports `useAllClassrooms` — a lightweight hook that fetches the full
 * classroom catalog (unpaginated, up to 500 results) for use in selectors such
 * as the parent-classroom picker and the ClassroomInfoModal children list.
 * Its query key is nested under ['classrooms', …] so any mutation automatically
 * invalidates it alongside the paginated list.
 */
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import {
  getClassrooms,
  getClassroomStats,
  createClassroom,
  updateClassroom,
  deleteClassroom,
} from '../api/classrooms';
import { useApiMutation } from './useApiMutation';

/**
 * @param {object} [params={}]
 * @param {string}       [params.search]    - Server-side free-text filter (pending backend implementation).
 * @param {number}       [params.page=0]    - Zero-based current page.
 * @param {number}       [params.size=10]   - Page size.
 * @param {string}       [params.sort]      - Sort field (name | capacity | createdAt).
 * @param {'asc'|'desc'} [params.direction] - Sort direction.
 *
 * @returns {{
 *   classrooms:     object[],
 *   totalElements:  number,
 *   totalPages:     number,
 *   stats:          { total: number, available: number, notAvailable: number } | null,
 *   loading:        boolean,
 *   createMutation: import('@tanstack/react-query').UseMutationResult,
 *   updateMutation: import('@tanstack/react-query').UseMutationResult,
 *   deleteMutation: import('@tanstack/react-query').UseMutationResult,
 * }}
 */
export function useClassrooms({ search, page = 0, size = 10, sort, direction } = {}) {

  // ── Paginated classroom list ─────────────────────────────────────────────────
  const {
    data: pageData,
    isFetching: loading,
  } = useQuery({
    queryKey:        ['classrooms', 'list', { search, page, size, sort, direction }],
    queryFn:         () => getClassrooms({ search, page, size, sort, direction }),
    placeholderData: keepPreviousData,
  });

  const classrooms    = pageData?.items         ?? [];
  const totalElements = pageData?.totalElements ?? 0;
  const totalPages    = pageData?.totalPages    ?? 1;

  // ── Aggregated stats (total / available / notAvailable) ─────────────────────
  // Returns null until the backend implements GET /api/v1/classrooms/stats;
  // the UI shows "—" for the breakdown cards in the meantime.
  const { data: stats = null } = useQuery({
    queryKey:     ['classrooms', 'stats'],
    queryFn:      getClassroomStats,
    retry:        false, // don't retry on 404 (endpoint not yet deployed)
    staleTime:    60_000,
  });

  // ── Mutations ────────────────────────────────────────────────────────────────
  // ['classrooms'] covers both ['classrooms','list',…] and ['classrooms','stats'] in one call.

  const createMutation = useApiMutation({
    mutationFn: createClassroom,
    invalidateKey: ['classrooms'],
    successMessage: 'Aula creada correctamente.',
  });

  const updateMutation = useApiMutation({
    mutationFn: ({ uuid, payload }) => updateClassroom(uuid, payload),
    invalidateKey: ['classrooms'],
    successMessage: 'Aula actualizada correctamente.',
  });

  const deleteMutation = useApiMutation({
    mutationFn: deleteClassroom,
    invalidateKey: ['classrooms'],
    successMessage: 'Aula dada de baja correctamente.',
  });

  return {
    classrooms,
    totalElements,
    totalPages,
    stats,
    loading,
    createMutation,
    updateMutation,
    deleteMutation,
  };
}

/**
 * Fetches the full (unpaginated) classroom catalog — used by the parent-
 * classroom selector and the ClassroomInfoModal to resolve parent name and
 * children list without mixing concerns with the paginated table query.
 *
 * Query key is under ['classrooms', …] so create/update/delete mutations
 * invalidate it automatically and parent/children lists stay fresh.
 *
 * @returns {{ allClassrooms: object[] }}
 */
export function useAllClassrooms() {
  const { data } = useQuery({
    queryKey: ['classrooms', 'all'],
    queryFn:  () => getClassrooms({ size: 500, sort: 'name', direction: 'asc' }),
    staleTime: 60_000,
  });

  return { allClassrooms: data?.items ?? [] };
}
