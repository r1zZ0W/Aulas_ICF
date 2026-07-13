/**
 * @fileoverview API client for the reservations module.
 * Mirrors src/api/classrooms.js in structure.
 *
 * Key design decisions:
 *  - No client-side loops for recurring bookings; `createBooking` sends a single request and
 *    the backend generates every date occurrence atomically.
 *  - Dates are always exchanged as plain YYYY-MM-DD strings — never ISO UTC timestamps —
 *    to avoid timezone-shift bugs when constructing Date objects later.
 *  - 409 Conflict responses carry a structured `{ date, timeSlotId }` payload; the error
 *    message is humanised here (in the API layer) so all callers get a ready-to-toast string.
 *  - Paginated list functions (`getReservations`, `getReservationsByUser`) return the raw
 *    PagedResultDTO shape so `parsePageResponse` (queryUtils) can extract items/totalPages.
 */
import { z } from 'zod';
import { createApiClient, HttpError } from './base.js';
import {
  ReservInstanceResponseSchema,
  BookingRequestSchema,
  ReassignRequestSchema,
  StudentResponseSchema,
} from '../schemas/reservation.js';
import { buildPageParams } from '../utils/queryUtils.js';

const api = createApiClient({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: { Accept: 'application/json' },
});

/**
 * Resolves an error message based on the HTTP status code.
 * @param {HttpError} error
 * @param {Object} overrides
 * @returns {string}
 */
function resolveErrorMessage(error, overrides = {}) {
  if (overrides[error.status]) return overrides[error.status];
  const serverMessage = error.data?.message;
  const defaults = {
    0: 'No se pudo conectar con el servidor. Verifica tu conexión.',
    400: serverMessage || 'Los datos enviados no son válidos.',
    401: 'No autorizado.',
    403: 'No tienes permisos para realizar esta acción.',
    404: 'La reserva solicitada no existe.',
    500: serverMessage || 'Error interno del servidor. Intenta de nuevo más tarde.',
  };
  return defaults[error.status] || serverMessage || `Error inesperado (${error.status}).`;
}

/**
 * Returns a paginated list of all reservation instances (admin use).
 * GET /api/v1/reservations[?page=&size=&sort=&direction=&search=&status=&reassigned=&classroomId=&from=&to=]
 *
 * Allowed sort fields: createdAt, date, status. Default: date desc.
 * Returns the raw API response so `parsePageResponse` can extract items/totalPages.
 *
 * @param {{ page?: number, size?: number, sort?: string, direction?: string, search?: string, status?: string, reassigned?: boolean, classroomId?: string, from?: string, to?: string }} [params={}]
 * @returns {Promise<object>} Raw API response containing `data.items`, `data.totalPages`, etc.
 */
export async function getReservations({ page, size, sort = 'date', direction = 'desc', search, status, reassigned, classroomId, from, to } = {}) {
  try {
    const qs = buildPageParams({ page, size, sort, direction, search, status, reassigned, classroomId, from, to });
    const { data } = await api.get(`/api/v1/reservations${qs}`);
    // Validate each item in the page without stripping the envelope
    if (Array.isArray(data?.data?.items)) {
      data.data.items = z.array(ReservInstanceResponseSchema).parse(data.data.items);
    }
    return data;
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Returns a paginated list of reservation instances belonging to a specific user.
 * A Maestro may only query their own UUID; an admin can query any user.
 * GET /api/v1/reservations/user/{userUuid}[?page=&size=&sort=&direction=&search=&status=&reassigned=&classroomId=&from=&to=]
 *
 * Allowed sort fields: createdAt, date, status. Default: date desc.
 * Returns the raw API response so `parsePageResponse` can extract items/totalPages.
 *
 * @param {string} userUuid
 * @param {{ page?: number, size?: number, sort?: string, direction?: string, search?: string, status?: string, reassigned?: boolean, classroomId?: string, from?: string, to?: string }} [params={}]
 * @returns {Promise<object>} Raw API response containing `data.items`, `data.totalPages`, etc.
 */
export async function getReservationsByUser(userUuid, { page, size, sort = 'date', direction = 'desc', search, status, reassigned, classroomId, from, to } = {}) {
  try {
    const qs = buildPageParams({ page, size, sort, direction, search, status, reassigned, classroomId, from, to });
    const { data } = await api.get(`/api/v1/reservations/user/${userUuid}${qs}`);
    if (Array.isArray(data?.data?.items)) {
      data.data.items = z.array(ReservInstanceResponseSchema).parse(data.data.items);
    }
    return data;
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error, {
      403: 'Solo puedes consultar tu propio historial de reservas.',
    }));
    throw error;
  }
}

/**
 * Returns active reservation instances within a date range.
 * When `classroomUuid` is omitted, all classrooms are included.
 * GET /api/v1/reservations/availability?from=&to=[&classroomUuid=]
 *
 * @param {{ from: string, to: string, classroomUuid?: string }} params
 * @returns {Promise<object[]>} Array of ReservInstanceResponseDTO objects
 */
export async function getAvailability({ from, to, classroomUuid } = {}) {
  try {
    const params = new URLSearchParams({ from, to });
    if (classroomUuid) params.set('classroomUuid', classroomUuid);
    const { data } = await api.get(`/api/v1/reservations/availability?${params}`);
    return z.array(ReservInstanceResponseSchema).parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Atomically creates a reservation group and all its instances, sending the mandatory
 * student roster in the same request.
 * One call → one transaction → N instances (one per target date in the recurrence).
 * POST /api/v1/reservations/booking (multipart/form-data)
 *
 * The request has two parts: `data` — the JSON booking intent, appended as a Blob with
 * `application/json` type so Spring binds it to the DTO — and `file` — the roster `.xlsx`.
 * React never parses the Excel content; the backend validates it (format, duplicates, and
 * that the row count equals `attendeeCount`) before creating anything. `base.js` detects
 * the FormData body and lets the browser set the multipart boundary header.
 *
 * On 409 Conflict the backend returns `{ data: { date, timeSlotId } }`;
 * on 422 it returns either `{ expected, actual }` (count mismatch) or `{ row, value }`
 * (duplicate/empty roster) — each is formatted here as a human-readable message.
 *
 * @param {object} payload - Matches BookingRequestSchema
 * @param {File}   file    - The roster .xlsx selected by the user
 * @returns {Promise<object[]>} Array of created ReservInstanceResponseDTO objects
 */
export async function createBooking(payload, file) {
  try {
    const body = BookingRequestSchema.parse(payload);

    const formData = new FormData();
    formData.append('data', new Blob([JSON.stringify(body)], { type: 'application/json' }));
    formData.append('file', file);

    const { data } = await api.post('/api/v1/reservations/booking', formData);
    return z.array(ReservInstanceResponseSchema).parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) {
      // 409: structured conflict payload → friendly message
      if (error.status === 409 && error.data?.data) {
        const { date, timeSlotId } = error.data.data;
        throw new Error(
          `Ya existe una reserva el ${date} en el bloque de horario ${timeSlotId}. ` +
          'Elige otro horario o fecha.'
        );
      }
      // 422: roster validation — count mismatch carries { expected, actual },
      // duplicate/empty carries { row, value }
      if (error.status === 422) {
        const detail = error.data?.data;
        if (detail && typeof detail.expected === 'number') {
          throw new Error(
            `El Excel tiene ${detail.actual} alumnos pero indicaste ${detail.expected} asistentes. ` +
            'Corrige la lista o el número de asistentes.'
          );
        }
        if (detail?.row != null) {
          throw new Error(
            `El alumno "${detail.value}" está repetido en la fila ${detail.row} del Excel.`
          );
        }
        throw new Error('La lista de alumnos está vacía o no es válida.');
      }
      throw new Error(resolveErrorMessage(error, {
        400: error.data?.message || 'Los datos enviados no son válidos.',
        413: 'El archivo excede el tamaño máximo permitido (1 MB).',
      }));
    }
    throw error;
  }
}

/**
 * Cancels a reservation as the owning teacher.
 * PATCH /api/v1/reservations/{uuid}/cancel
 *
 * @param {string} uuid - Public UUID of the reservation instance
 * @returns {Promise<object>} Updated ReservInstanceResponseDTO
 */
export async function cancelReservation(uuid) {
  try {
    const { data } = await api.patch(`/api/v1/reservations/${uuid}/cancel`);
    return ReservInstanceResponseSchema.parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error, {
      403: 'Solo puedes cancelar tus propias reservas.',
      400: error.data?.message || 'La reserva no puede ser cancelada.',
    }));
    throw error;
  }
}

/**
 * Cancels a reservation as an administrator. Requires ADMIN role.
 * PATCH /api/v1/reservations/{uuid}/cancel-admin
 *
 * @param {string} uuid - Public UUID of the reservation instance
 * @returns {Promise<object>} Updated ReservInstanceResponseDTO
 */
export async function cancelReservationAdmin(uuid) {
  try {
    const { data } = await api.patch(`/api/v1/reservations/${uuid}/cancel-admin`);
    return ReservInstanceResponseSchema.parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error, {
      400: error.data?.message || 'La reserva no puede ser cancelada.',
    }));
    throw error;
  }
}

/**
 * Reassigns an active reservation to a different classroom and/or time-slot block. ADMIN only.
 * PATCH /api/v1/reservations/{uuid}/reassign
 *
 * @param {string} uuid    - Public UUID of the reservation instance
 * @param {object} payload - Matches ReassignRequestSchema
 * @returns {Promise<object>} Updated ReservInstanceResponseDTO
 */
export async function reassignReservation(uuid, payload) {
  try {
    const body = ReassignRequestSchema.parse(payload);
    const { data } = await api.patch(`/api/v1/reservations/${uuid}/reassign`, body);
    return ReservInstanceResponseSchema.parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error, {
      400: error.data?.message || 'La reasignación no es válida.',
      409: error.data?.message || 'El aula o horario destino ya está ocupado.',
    }));
    throw error;
  }
}

/**
 * Returns the student roster of a reservation group. ADMIN only.
 * The roster is stored as an .xlsx file per group (not per date occurrence), so this
 * is keyed by `groupUuid`, not the reservation instance UUID.
 * GET /api/v1/reservations/groups/{groupUuid}/students
 *
 * Returns an empty array (not an error) when no roster has been uploaded for the group —
 * that is a legitimate state for legacy groups, distinguished by the backend from a genuine
 * read failure (which surfaces as a thrown error here).
 *
 * @param {string} groupUuid - Public UUID of the reservation group
 * @returns {Promise<object[]>} Array of { firstName, lastName, email }
 */
export async function getReservationStudents(groupUuid) {
  try {
    const { data } = await api.get(`/api/v1/reservations/groups/${groupUuid}/students`);
    return z.array(StudentResponseSchema).parse(data.data);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error, {
      403: 'No tienes permisos para ver la lista de estudiantes.',
    }));
    throw error;
  }
}
