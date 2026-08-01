/**
 * @fileoverview API client for the /api/v1/users and /api/v1/roles endpoints.
 *
 * All functions follow the same pattern as the other api/*.js modules:
 *  - Use createApiClient with the project base URL.
 *  - Call the *Validated methods, which unwrap the ApiResponse envelope, validate the
 *    response with a Zod schema, and route any failure through resolveApiError.
 */
import { createApiClient } from './base.js';
import UserResponseSchema from '../schemas/user/userResponse.js';
import UserStatsSchema from '../schemas/user/userStats.js';
import RoleResponseSchema from '../schemas/role.js';
import { PagedResultSchema } from '../schemas/pagedResult.js';
import { buildPageParams } from '../utils/queryUtils.js';

const api = createApiClient();

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
  const qs = buildPageParams({ search, page, size, sort, direction });
  return api.getValidated(`/api/v1/users${qs}`, { schema: PagedResultSchema(UserResponseSchema) });
}

/**
 * Fetches aggregated user statistics for the admin dashboard. ADMIN only.
 * GET /api/v1/users/stats
 *
 * @returns {Promise<{ total: number, active: number, inactive: number, admins: number }>}
 */
export async function getUserStats() {
  return api.getValidated('/api/v1/users/stats', { schema: UserStatsSchema });
}

/**
 * Creates a new user account. ADMIN only.
 *
 * `payload` is not re-validated here: the caller's `useZodForm(UserCreateSchema)` already
 * ran `validateAll()` before submit — parsing again would just duplicate that check.
 *
 * @param {object} payload - matches UserCreateSchema
 * @returns {Promise<void>}
 */
export async function createUser(payload) {
  return api.postValidated('/api/v1/users/register', payload);
}

/**
 * Updates a user's profile. ADMIN only.
 * @param {string} uuid
 * @param {object} payload - matches UserUpdateSchema (already validated by the caller's useZodForm)
 * @returns {Promise<object>} Updated UserResponseDTO
 */
export async function updateUser(uuid, payload) {
  return api.putValidated(`/api/v1/users/${uuid}`, payload, {
    schema: UserResponseSchema,
    overrides: { ACCESS_DENIED: 'No puedes modificar tu propia cuenta desde aquí.' },
  });
}

/**
 * Fetches the authenticated user's own profile.
 * GET /api/v1/users/me
 *
 * @returns {Promise<object>} Current user profile.
 */
export async function getMyProfile() {
  return api.getValidated('/api/v1/users/me', { schema: UserResponseSchema });
}

/**
 * Updates the authenticated user's own profile.
 * PUT /api/v1/users/me
 *
 * @param {object} payload - matches UserSelfEditSchema (already validated by the caller's useZodForm)
 * @returns {Promise<object>} Updated user profile.
 */
export async function updateMyProfile(payload) {
  return api.putValidated('/api/v1/users/me', payload, {
    schema: UserResponseSchema,
    overrides: { USER_PASSWORD_CHANGE_FORBIDDEN: 'No puedes cambiar tu contraseña desde este perfil.' },
  });
}

/**
 * Permanently deletes a user account along with all their reservation data. ADMIN only.
 * This operation is irreversible.
 * @param {string} uuid
 * @returns {Promise<void>}
 */
export async function deleteUser(uuid) {
  return api.deleteValidated(`/api/v1/users/${uuid}`);
}

// ── Roles ─────────────────────────────────────────────────────────────────────

/**
 * Fetches all available roles (id + name). ADMIN only.
 * @returns {Promise<Array<{id: number, name: string}>>}
 */
export async function getRoles() {
  return api.getValidated('/api/v1/roles', { schema: RoleResponseSchema.array() });
}
