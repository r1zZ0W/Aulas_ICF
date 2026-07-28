/**
 * @fileoverview API client for the reservations module.
 * Mirrors src/api/classrooms.js in structure.
 *
 * Key design decisions:
 *  - No client-side loops for recurring bookings; `createBooking` sends a single request and
 *    the backend generates every date occurrence atomically.
 *  - Dates are always exchanged as plain YYYY-MM-DD strings — never ISO UTC timestamps —
 *    to avoid timezone-shift bugs when constructing Date objects later.
 *  - 409 Conflict responses carry a structured `{ date, timeSlotId }` payload; 422 roster
 *    failures carry `{ expected, actual }` or `{ row, value }`. All three are humanized by
 *    `resolveApiError` (via ERROR_CATALOG's structured-payload branch), not duplicated here.
 *  - Paginated list functions (`getReservations`, `getReservationsByUser`) return the raw
 *    PagedResultDTO shape so `parsePageResponse` (queryUtils) can extract items/totalPages.
 */
import { z } from 'zod';
import { createApiClient } from './base.js';
import {
  ReservInstanceResponseSchema,
  BookingRequestSchema,
  ReassignRequestSchema,
  StudentResponseSchema,
} from '../schemas/reservation.js';
import { buildPageParams } from '../utils/queryUtils.js';
import { assertValidRequestPayload } from '../errors/resolveApiError.js';

const api = createApiClient();

/**
 * Returns a paginated list of all reservation instances (admin use).
 * GET /api/v1/reservations[?page=&size=&sort=&direction=&search=&status=&reassigned=&classroomId=&from=&to=]
 *
 * Allowed sort fields: createdAt, date, status. Default: date desc.
 * Returns the raw API response so `parsePageResponse` can extract items/totalPages.
 *
 * @param {{ page?: number, size?: number, sort?: string, direction?: string, search?: string, status?: string, reassigned?: boolean, classroomId?: string, from?: string, to?: string }} [params={}]
 * @returns {Promise<object>} Raw API response containing `items`, `totalPages`, etc.
 */
export async function getReservations({ page, size, sort = 'date', direction = 'desc', search, status, reassigned, classroomId, from, to } = {}) {
  const qs = buildPageParams({ page, size, sort, direction, search, status, reassigned, classroomId, from, to });
  const pageResult = await api.getValidated(`/api/v1/reservations${qs}`);
  if (Array.isArray(pageResult?.items)) pageResult.items = z.array(ReservInstanceResponseSchema).parse(pageResult.items);
  return pageResult;
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
 * @returns {Promise<object>} Raw API response containing `items`, `totalPages`, etc.
 */
export async function getReservationsByUser(userUuid, { page, size, sort = 'date', direction = 'desc', search, status, reassigned, classroomId, from, to } = {}) {
  const qs = buildPageParams({ page, size, sort, direction, search, status, reassigned, classroomId, from, to });
  const pageResult = await api.getValidated(`/api/v1/reservations/user/${userUuid}${qs}`, {
    overrides: { ACCESS_DENIED: 'Solo puedes consultar tu propio historial de reservas.' },
  });
  if (Array.isArray(pageResult?.items)) pageResult.items = z.array(ReservInstanceResponseSchema).parse(pageResult.items);
  return pageResult;
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
  const params = new URLSearchParams({ from, to });
  if (classroomUuid) params.set('classroomUuid', classroomUuid);
  return api.getValidated(`/api/v1/reservations/availability?${params}`, {
    schema: z.array(ReservInstanceResponseSchema),
  });
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
 * `payload` is built by the caller from transformed form state (`labelsToTimeSlotIds`, etc.),
 * not a straight pass-through of what `useZodForm` already validated, so it's the one payload
 * in this module still worth validating client-side — see `assertValidRequestPayload`.
 *
 * @param {object} payload - Matches BookingRequestSchema
 * @param {File}   file    - The roster .xlsx selected by the user
 * @returns {Promise<object[]>} Array of created ReservInstanceResponseDTO objects
 */
export async function createBooking(payload, file) {
  const body = assertValidRequestPayload(BookingRequestSchema, payload);

  const formData = new FormData();
  formData.append('data', new Blob([JSON.stringify(body)], { type: 'application/json' }));
  formData.append('file', file);

  return api.postValidated('/api/v1/reservations/booking', formData, {
    schema: z.array(ReservInstanceResponseSchema),
    overrides: { FILE_TOO_LARGE: 'El archivo excede el tamaño máximo permitido (1 MB).' },
  });
}

/**
 * Cancels a reservation as the owning teacher.
 * PATCH /api/v1/reservations/{uuid}/cancel
 *
 * @param {string} uuid - Public UUID of the reservation instance
 * @returns {Promise<object>} Updated ReservInstanceResponseDTO
 */
export async function cancelReservation(uuid) {
  return api.patchValidated(`/api/v1/reservations/${uuid}/cancel`, undefined, {
    schema: ReservInstanceResponseSchema,
    overrides: { ACCESS_DENIED: 'Solo puedes cancelar tus propias reservas.' },
  });
}

/**
 * Cancels a reservation as an administrator. Requires ADMIN role.
 * PATCH /api/v1/reservations/{uuid}/cancel-admin
 *
 * @param {string} uuid - Public UUID of the reservation instance
 * @returns {Promise<object>} Updated ReservInstanceResponseDTO
 */
export async function cancelReservationAdmin(uuid) {
  return api.patchValidated(`/api/v1/reservations/${uuid}/cancel-admin`, undefined, {
    schema: ReservInstanceResponseSchema,
  });
}

/**
 * Reassigns an active reservation to a different classroom and/or time-slot block. ADMIN only.
 * PATCH /api/v1/reservations/{uuid}/reassign
 *
 * `payload` comes from `ReasignarModal`'s own form state (`ReasignFormSchema`, not
 * `ReassignRequestSchema`) transformed into the request shape — nothing else validates the
 * transformed result, so it's kept here too.
 *
 * @param {string} uuid    - Public UUID of the reservation instance
 * @param {object} payload - Matches ReassignRequestSchema
 * @returns {Promise<object>} Updated ReservInstanceResponseDTO
 */
export async function reassignReservation(uuid, payload) {
  const body = assertValidRequestPayload(ReassignRequestSchema, payload);
  return api.patchValidated(`/api/v1/reservations/${uuid}/reassign`, body, {
    schema: ReservInstanceResponseSchema,
  });
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
  return api.getValidated(`/api/v1/reservations/groups/${groupUuid}/students`, {
    schema: z.array(StudentResponseSchema),
    overrides: { ACCESS_DENIED: 'No tienes permisos para ver la lista de estudiantes.' },
  });
}
