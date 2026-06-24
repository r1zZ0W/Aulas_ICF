/**
 * @fileoverview Custom hook that encapsulates all server-state logic for the
 * Classrooms page: paginated list, aggregated stats, and create/update/delete/
 * toggle-status mutations via React Query.
 *
 * Also exports `useAllClassrooms` — a lightweight hook that fetches the full
 * classroom catalog (unpaginated, up to 500 results) for use in selectors such
 * as the parent-classroom picker and the ClassroomInfoModal children list.
 * Its query key is nested under ['classrooms', …] so any mutation automatically
 * invalidates it alongside the paginated list.
 */
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import {
  getClassrooms,
  getClassroomStats,
  createClassroom,
  updateClassroom,
  toggleClassroomStatus,
  deleteClassroom,
  setClassroomChildren,
} from '../../../../api/classrooms.js';
import { useApiMutation } from '../../../../hooks/useApiMutation.js';
import { toast } from '../../../../utils/toast.jsx';

/**
 * @param {object} [params={}]
 * @param {string}       [params.search]    - Server-side free-text filter (pending backend implementation).
 * @param {number}       [params.page=0]    - Zero-based current page.
 * @param {number}       [params.size=10]   - Page size.
 * @param {string}       [params.sort]      - Sort field (name | capacity | createdAt).
 * @param {'asc'|'desc'} [params.direction] - Sort direction.
 *
 * @returns {{
 *   classrooms:          object[],
 *   totalElements:       number,
 *   totalPages:          number,
 *   stats:               { total: number, available: number, notAvailable: number } | null,
 *   loading:             boolean,
 *   createMutation:      import('@tanstack/react-query').UseMutationResult,
 *   updateMutation:      import('@tanstack/react-query').UseMutationResult,
 *   toggleStatusMutation: import('@tanstack/react-query').UseMutationResult,
 *   deleteMutation:      import('@tanstack/react-query').UseMutationResult,
 *   setChildrenMutation: import('@tanstack/react-query').UseMutationResult,
 * }}
 */
export function useClassrooms({ search, page = 0, size = 10, sort, direction } = {}) {
  const queryClient = useQueryClient();

  // ── Paginated classroom list ─────────────────────────────────────────────────
  const {
    data: pageData,
    isFetching: loading,
  } = useQuery({
    queryKey: ['classrooms', 'list', { search, page, size, sort, direction }],
    queryFn: () => getClassrooms({ search, page, size, sort, direction }),
    placeholderData: keepPreviousData,
  });

  const classrooms = pageData?.items ?? [];
  const totalElements = pageData?.totalElements ?? 0;
  const totalPages = pageData?.totalPages ?? 1;

  // ── Aggregated stats (total / available / notAvailable) ─────────────────────
  // Returns null until the backend implements GET /api/v1/classrooms/stats;
  // the UI shows "—" for the breakdown cards in the meantime.
  const { data: stats = null } = useQuery({
    queryKey: ['classrooms', 'stats'],
    queryFn: getClassroomStats,
    retry: false, // don't retry on 404 (endpoint not yet deployed)
    staleTime: 60_000,
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
    mutationFn: (uuid) => deleteClassroom(uuid),
    invalidateKey: ['classrooms'],
    successMessage: 'Aula eliminada correctamente.',
  });

  /**
   * Toggles the active status of a classroom via a single PATCH request.
   * Replaces the separate deactivate/reactivate mutations.
   * On success the list and stats are invalidated so the table refreshes automatically.
   */
  const toggleStatusMutation = useApiMutation({
    mutationFn: (uuid) => toggleClassroomStatus(uuid),
    invalidateKey: ['classrooms'],
    successMessage: 'Estado del aula actualizado correctamente.',
  });

  /**
   * Bulk-assigns the desired set of child classrooms to a parent using a single
   * atomic PUT /api/v1/classrooms/{parentUuid}/children request.
   *
   * The backend receives the full desired set and handles the diff (link new children,
   * unlink removed ones) inside a single transaction — no partial-failure risk.
   *
   * @param {{ parentUuid: string, childUuids: string[] }} params
   *   `parentUuid` — UUID of the parent classroom
   *   `childUuids` — full desired set of child UUIDs (empty array removes all children)
   */
  const setChildrenMutation = useMutation({
    mutationFn: ({ parentUuid, childUuids }) =>
      setClassroomChildren(parentUuid, childUuids),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['classrooms'] });
      toast.success('Aulas hijas actualizadas.');
    },
    onError: async (err) => {
      // Force resync: pull real DB state before showing the error,
      // so reopening the modal reflects what actually persisted.
      await queryClient.invalidateQueries({ queryKey: ['classrooms'] });
      toast.error(
        `Error al actualizar las aulas hijas: ${err.message}. ` +
        'Vuelve a abrir el aula para ver el estado actual.'
      );
    },
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
    toggleStatusMutation,
    setChildrenMutation,
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
    queryFn: () => getClassrooms({ size: 500, sort: 'name', direction: 'asc' }),
    staleTime: 60_000,
  });

  return { allClassrooms: data?.items ?? [] };
}
