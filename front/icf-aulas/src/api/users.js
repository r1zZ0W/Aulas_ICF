/**
 * @fileoverview API client for the /api/v1/users and /api/v1/roles endpoints.
 *
 * All functions follow the same pattern as api/auth.js:
 *  - Use createApiClient with the project base URL.
 *  - Unwrap the ApiResponse envelope (data.data).
 *  - Validate with Zod schemas.
 *  - Map HttpErrors to user-facing Spanish messages.
 */
import { createApiClient, HttpError } from './base.js';
import {
  UserResponseSchema,
  UserCreateSchema,
  UserUpdateSchema,
  UserSelfEditSchema,
  UserStatsSchema,
  RoleResponseSchema,
  PagedResultSchema,
} from '../schemas/index.js';
import { buildPageParams } from '../utils/queryUtils.js';

const api = createApiClient();

/**
 * Maps an HttpError to a user-facing Spanish message.
 * @param {HttpError} error
 * @param {Record<number,string>} [overrides]
 * @returns {string}
 */
function resolveErrorMessage(error, overrides = {}) {
  if (overrides[error.status]) return overrides[error.status];
  const serverMessage = error.data?.message;
  const defaults = {
    0:   'No se pudo conectar con el servidor. Verifica tu conexión.',
    400: serverMessage || 'Los datos enviados no son válidos.',
    401: 'No autorizado.',
    403: 'No tienes permisos para realizar esta acción.',
    404: 'El usuario solicitado no existe.',
    409: 'Ya existe un usuario con ese correo o nombre de usuario.',
    422: 'Los datos enviados no pudieron ser procesados.',
    500: 'Error interno del servidor. Intenta de nuevo más tarde.',
  };
  return defaults[error.status] || serverMessage || `Error inesperado (${error.status}).`;
}

// ── Users ─────────────────────────────────────────────────────────────────────

/**
 * Fetches a paginated, optionally-filtered list of users. ADMIN only.
 *
 * When called with no args the backend returns all users in a single large page
 * (the same PagedResultDTO contract applies).
 *
 * @param {object} [params={}]
 * @param {string}       [params.search]    - Free-text filter over name/email/username/matricula.
 * @param {number}       [params.page]      - Zero-based page index.
 * @param {number}       [params.size]      - Page size (1–100).
 * @param {string}       [params.sort]      - Sort field. Allowed: createdAt, email, username, matricula, firstName.
 * @param {'asc'|'desc'} [params.direction] - Sort direction.
 * @returns {Promise<import('../schemas/pagedResult.js').PagedResultSchema>} Parsed paged result.
 */
export async function getUsers({ search, page, size, sort, direction } = {}) {
  try {
    const qs = buildPageParams({ search, page, size, sort, direction });
    const { data } = await api.get(`/api/v1/users${qs}`);
    return PagedResultSchema(UserResponseSchema).parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Fetches aggregated user statistics for the admin dashboard. ADMIN only.
 * GET /api/v1/users/stats
 *
 * @returns {Promise<{ total: number, active: number, inactive: number, admins: number }>}
 */
export async function getUserStats() {
  try {
    const { data } = await api.get('/api/v1/users/stats');
    return UserStatsSchema.parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Creates a new user account. ADMIN only.
 * @param {z.infer<typeof UserCreateSchema>} payload
 * @returns {Promise<void>}
 */
export async function createUser(payload) {
  try {
    const body = UserCreateSchema.parse(payload);
    await api.post('/api/v1/users/register', body);
  } catch (error) {
    if (error instanceof HttpError) {
      throw new Error(resolveErrorMessage(error, {
        409: 'Ya existe un usuario con ese correo o nombre de usuario.',
      }));
    }
    throw error;
  }
}

/**
 * Updates a user's profile. ADMIN only.
 * @param {string} uuid
 * @param {z.infer<typeof UserUpdateSchema>} payload
 * @returns {Promise<object>} Updated UserResponseDTO
 */
export async function updateUser(uuid, payload) {
  try {
    const body = UserUpdateSchema.parse(payload);
    const { data } = await api.put(`/api/v1/users/${uuid}`, body);
    return UserResponseSchema.parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) {
      throw new Error(resolveErrorMessage(error, {
        409: 'Ya existe un usuario con ese correo o nombre de usuario.',
        403: 'No puedes modificar tu propia cuenta desde aquí.',
      }));
    }
    throw error;
  }
}

/**
 * Fetches the authenticated user's own profile.
 * GET /api/v1/users/me
 *
 * @returns {Promise<object>} Current user profile.
 */
export async function getMyProfile() {
  try {
    const { data } = await api.get('/api/v1/users/me');
    return UserResponseSchema.parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Updates the authenticated user's own profile.
 * PUT /api/v1/users/me
 *
 * @param {z.infer<typeof UserSelfEditSchema>} payload
 * @returns {Promise<object>} Updated user profile.
 */
export async function updateMyProfile(payload) {
  try {
    const body = UserSelfEditSchema.parse(payload);
    const { data } = await api.put('/api/v1/users/me', body);
    return UserResponseSchema.parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) {
      throw new Error(resolveErrorMessage(error, {
        409: 'Ya existe un usuario con ese correo o nombre de usuario.',
        403: 'No puedes cambiar tu contraseña desde este perfil.',
      }));
    }
    throw error;
  }
}

/**
 * Permanently deletes a user account along with all their reservation data. ADMIN only.
 * This operation is irreversible.
 * @param {string} uuid
 * @returns {Promise<void>}
 */
export async function deleteUser(uuid) {
  try {
    await api.delete(`/api/v1/users/${uuid}`);
  } catch (error) {
    if (error instanceof HttpError) {
      throw new Error(resolveErrorMessage(error, {
        403: 'No puedes eliminar tu propia cuenta.',
        404: 'El usuario solicitado no existe.',
      }));
    }
    throw error;
  }
}

// ── Roles ─────────────────────────────────────────────────────────────────────

/**
 * Fetches all available roles (id + name). ADMIN only.
 * @returns {Promise<Array<{id: number, name: string}>>}
 */
export async function getRoles() {
  try {
    const { data } = await api.get('/api/v1/roles');
    return RoleResponseSchema.array().parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}
