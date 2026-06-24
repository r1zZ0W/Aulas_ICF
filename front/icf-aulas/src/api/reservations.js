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
 */
import { z } from 'zod';
import { createApiClient, HttpError } from './base.js';
import {
  ReservInstanceResponseSchema,
  BookingRequestSchema,
  ReassignRequestSchema,
} from '../schemas/reservation.js';

const api = createApiClient({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: { Accept: 'application/json' },
});

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
 * Atomically creates a reservation group and all its instances.
 * One call → one transaction → N instances (one per target date in the recurrence).
 * POST /api/v1/reservations/booking
 *
 * On 409 Conflict the backend returns `{ data: { date, timeSlotId } }`;
 * this function formats it as a human-readable error message.
 *
 * @param {object} payload - Matches BookingRequestSchema
 * @returns {Promise<object[]>} Array of created ReservInstanceResponseDTO objects
 */
export async function createBooking(payload) {
  try {
    const body = BookingRequestSchema.parse(payload);
    const { data } = await api.post('/api/v1/reservations/booking', body);
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
      throw new Error(resolveErrorMessage(error, {
        400: error.data?.message || 'Los datos enviados no son válidos.',
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
