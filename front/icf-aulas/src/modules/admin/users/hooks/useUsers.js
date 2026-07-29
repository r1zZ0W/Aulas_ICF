/**
 * @fileoverview Custom hook that encapsulates all server-state logic for the
 * Users admin page: paginated + searchable user list, aggregated stats, roles,
 * and create/update/deactivate mutations via React Query.
 */
import { useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import { getUsers, getUserStats, createUser, updateUser, deleteUser, getRoles } from '../../../../api/users.js';
import { useApiMutation } from '../../../../hooks/useApiMutation.js';
import { toast } from '../../../../utils/toast.jsx';

/** Above this, BCrypt's own ~200-500ms cost (see AuthConfig) would make the "Creando
 *  usuario…" toast fire on nearly every successful create, stacking redundantly right
 *  before the success toast. It only exists as a safety net for outlier latency. */
const SLOW_CREATE_TOAST_DELAY_MS = 600;

/**
 * @typedef {import('../../../../schemas/user/userResponse.js').UserResponseSchema} User
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
  const queryClient = useQueryClient();
  const listKey = ['users', 'list', { search, page, size, sort, direction }];

  // ── Paginated user list ──────────────────────────────────────────────────────
  const {
    data: pageData,
    isLoading: usersLoading,
    isError: usersError,
    refetch: refetchUsers,
  } = useQuery({
    queryKey: listKey,
    queryFn: () => getUsers({ search, page, size, sort, direction }),
    placeholderData: keepPreviousData,
  });

  const users = pageData?.items ?? [];
  const totalElements = pageData?.totalElements ?? 0;
  const totalPages = pageData?.totalPages ?? 1;

  // ── Aggregated stats (total / active / inactive / admins) ───────────────────
  const { data: stats = { total: 0, active: 0, inactive: 0, admins: 0 } } = useQuery({
    queryKey: ['users', 'stats'],
    queryFn: getUserStats,
  });

  // ── Roles catalogue (small, rarely changes) ──────────────────────────────────
  const { data: roles = [] } = useQuery({
    queryKey: ['roles'],
    queryFn: getRoles,
  });

  // ── Mutations ────────────────────────────────────────────────────────────────
  // ['users'] covers both ['users','list',…] and ['users','stats'] in one call.

  // Optimistic create: the modal closes instantly (see useUsersForm.handleCreateSubmit)
  // instead of waiting for BCrypt. Only page 0 with no active search gets a fake row —
  // that's the one case where the new user's position (top, createdAt desc) is
  // deterministic; any other page/filter is left untouched to avoid a phantom total or
  // a row that doesn't match the active search. Stats are NOT bumped optimistically —
  // they represent confirmed server state, and the UX win isn't worth a flicker on error.
  const createMutation = useApiMutation({
    mutationFn: createUser,
    invalidateKey: ['users'],
    successMessage: 'Usuario creado correctamente.',
    onMutate: async (payload) => {
      await queryClient.cancelQueries({ queryKey: listKey });
      const previousList = queryClient.getQueryData(listKey);

      if (page === 0 && !search) {
        const roleName = roles.find((r) => r.id === payload.roleId)?.name ?? 'TEACHER';
        const optimisticRow = {
          uuid: `optimistic-${crypto.randomUUID()}`,
          institutionalId: null,
          firstName: payload.firstName,
          lastNames: payload.lastNames,
          username: payload.username,
          email: payload.email,
          extension: null,
          roleName,
          createdAt: new Date().toISOString(),
          pending: true,
        };
        queryClient.setQueryData(listKey, (old) => old && {
          ...old,
          items: [optimisticRow, ...old.items].slice(0, size),
          totalElements: old.totalElements + 1,
        });
      }

      // Safety net for outlier latency (server under load, slow network) — see
      // SLOW_CREATE_TOAST_DELAY_MS doc comment above.
      const toastRef = { current: null };
      const slowTimer = setTimeout(() => {
        toastRef.current = toast.info('Creando usuario…');
      }, SLOW_CREATE_TOAST_DELAY_MS);

      return { previousList, slowTimer, toastRef };
    },
    onSuccess: (data, payload, context) => {
      clearTimeout(context?.slowTimer);
      if (context?.toastRef?.current) toast.dismiss(context.toastRef.current);
    },
    onError: (err, payload, context) => {
      clearTimeout(context?.slowTimer);
      if (context?.toastRef?.current) toast.dismiss(context.toastRef.current);
      if (context?.previousList) queryClient.setQueryData(listKey, context.previousList);
    },
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
    usersError,
    refetchUsers,
    createMutation,
    updateMutation,
    deleteMutation,
  };
}
