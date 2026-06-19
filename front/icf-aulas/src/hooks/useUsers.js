/**
 * @fileoverview Custom hook that encapsulates all server-state logic for the
 * Users admin page: paginated + searchable user list, aggregated stats, roles,
 * and create/update/deactivate mutations via React Query.
 */
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import { getUsers, getUserStats, createUser, updateUser, deleteUser, getRoles } from '../api/users';
import { useApiMutation } from './useApiMutation';

/**
 * @typedef {import('../schemas/user/userResponse.js').UserResponseSchema} User
 * @typedef {{ total: number, active: number, inactive: number, admins: number }} UserStats
 */

/**
 * Encapsulates all React Query state for the users admin section.
 *
 * @param {object} [params={}]
 * @param {string}       [params.search]    - Server-side free-text filter.
 * @param {number}       [params.page=0]    - Zero-based current page.
 * @param {number}       [params.size=20]   - Page size.
 * @param {string}       [params.sort]      - Sort field.
 * @param {'asc'|'desc'} [params.direction] - Sort direction.
 *
 * @returns {{
 *   users:              User[],
 *   totalElements:      number,
 *   totalPages:         number,
 *   roles:              Array<{id: number, name: string}>,
 *   stats:              UserStats,
 *   usersLoading:       boolean,
 *   createMutation:     import('@tanstack/react-query').UseMutationResult,
 *   updateMutation:     import('@tanstack/react-query').UseMutationResult,
 *   deleteMutation:     import('@tanstack/react-query').UseMutationResult,
 * }}
 */
export function useUsers({ search, page = 0, size = 20, sort, direction } = {}) {

  // ── Paginated user list ──────────────────────────────────────────────────────
  const {
    data: pageData,
    isLoading: usersLoading,
  } = useQuery({
    queryKey:        ['users', 'list', { search, page, size, sort, direction }],
    queryFn:         () => getUsers({ search, page, size, sort, direction }),
    placeholderData: keepPreviousData,
  });

  const users         = pageData?.items         ?? [];
  const totalElements = pageData?.totalElements ?? 0;
  const totalPages    = pageData?.totalPages    ?? 1;

  // ── Aggregated stats (total / active / inactive / admins) ───────────────────
  const { data: stats = { total: 0, active: 0, inactive: 0, admins: 0 } } = useQuery({
    queryKey: ['users', 'stats'],
    queryFn:  getUserStats,
  });

  // ── Roles catalogue (small, rarely changes) ──────────────────────────────────
  const { data: roles = [] } = useQuery({
    queryKey: ['roles'],
    queryFn:  getRoles,
  });

  // ── Mutations ────────────────────────────────────────────────────────────────
  // ['users'] covers both ['users','list',…] and ['users','stats'] in one call.

  const createMutation = useApiMutation({
    mutationFn: createUser,
    invalidateKey: ['users'],
    successMessage: 'Usuario creado correctamente.',
  });

  const updateMutation = useApiMutation({
    mutationFn: ({ uuid, payload }) => updateUser(uuid, payload),
    invalidateKey: ['users'],
    successMessage: 'Usuario actualizado correctamente.',
  });

  const deleteMutation = useApiMutation({
    mutationFn: deleteUser,
    invalidateKey: ['users'],
    successMessage: 'Usuario eliminado correctamente.',
  });

  return {
    users,
    totalElements,
    totalPages,
    roles,
    stats,
    usersLoading,
    createMutation,
    updateMutation,
    deleteMutation,
  };
}
