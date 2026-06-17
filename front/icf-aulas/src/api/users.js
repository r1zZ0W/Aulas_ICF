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
import { UserResponseSchema, UserCreateSchema, UserUpdateSchema, RoleResponseSchema } from '../schemas/index.js';
import { z } from 'zod';

const api = createApiClient({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: { Accept: 'application/json' },
});

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
    400: 'Los datos enviados no son válidos.',
    401: 'No autorizado.',
    403: 'No tienes permisos para realizar esta acción.',
    404: 'El usuario solicitado no existe.',
    409: 'Ya existe un usuario con ese correo o nombre de usuario.',
    422: 'Los datos enviados no pudieron ser procesados.',
    500: 'Error interno del servidor. Intenta de nuevo más tarde.',
  };
  return serverMessage || defaults[error.status] || `Error inesperado (${error.status}).`;
}

// ── Users ─────────────────────────────────────────────────────────────────────

/**
 * Fetches all users. ADMIN only.
 * @returns {Promise<import('../schemas/user/userResponse.js').UserResponseSchema[]>}
 */
export async function getUsers() {
  try {
    const { data } = await api.get('/api/v1/users');
    return z.array(UserResponseSchema).parse(data.data ?? data);
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
 * Soft-deactivates a user account. ADMIN only.
 * @param {string} uuid
 * @returns {Promise<void>}
 */
export async function deactivateUser(uuid) {
  try {
    await api.patch(`/api/v1/users/${uuid}/deactivate`);
  } catch (error) {
    if (error instanceof HttpError) {
      throw new Error(resolveErrorMessage(error, {
        403: 'No puedes desactivar tu propia cuenta.',
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
    return z.array(RoleResponseSchema).parse(data.data ?? data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}
