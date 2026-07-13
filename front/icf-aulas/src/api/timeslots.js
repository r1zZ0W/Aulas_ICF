/**
 * @fileoverview API client for the time slots catalog.
 * The catalog (IDs 1–24, 07:00–19:30 in 30-min blocks) is static per deployment.
 * Clients should cache aggressively — it never changes unless the backend is redeployed.
 */
import { z } from 'zod';
import { createApiClient, HttpError } from './base.js';
import { TimeSlotSchema } from '../schemas/timeSlot.js';

const api = createApiClient({
  baseURL: import.meta.env.VITE_API_URL ?? 'http://localhost:8080',
  headers: { Accept: 'application/json' },
});

/**
 * Retrieves the full time-slot catalog.
 * Returns 24 blocks covering 07:00–19:30 in 30-minute increments.
 * GET /api/v1/timeslots
 *
 * @returns {Promise<import('../schemas/timeSlot.js').TimeSlot[]>}
 */
export async function getTimeSlots() {
  try {
    const { data } = await api.get('/api/v1/timeslots');
    return z.array(TimeSlotSchema).parse(data.data);
  } catch (error) {
    if (error instanceof HttpError)
      throw new Error('No se pudo cargar el catálogo de horarios. Intenta de nuevo.');
    throw error;
  }
}

/**
 * Retrieves the time slots currently available (not yet booked) for a classroom on a given date.
 * Computed server-side as `full catalog − occupied slots`, ordered by `startTime` ascending.
 * GET /api/v1/reservations/available-slots?classroomUuid=&date=
 *
 * Lives here (not in api/reservations.js) because it returns the same `TimeSlotDTO[]` shape as
 * {@link getTimeSlots}; the URL living under `/reservations` is a backend implementation detail
 * (the endpoint reuses ReservInstanceService's classroom/slot repositories).
 *
 * @param {{ classroomUuid: string, date: string }} params - date as YYYY-MM-DD
 * @returns {Promise<import('../schemas/timeSlot.js').TimeSlot[]>} available slots, chronologically ordered
 */
export async function getAvailableTimeSlots({ classroomUuid, date }) {
  try {
    const params = new URLSearchParams({ classroomUuid, date });
    const { data } = await api.get(`/api/v1/reservations/available-slots?${params}`);
    return z.array(TimeSlotSchema).parse(data.data);
  } catch (error) {
    if (error instanceof HttpError)
      throw new Error('No se pudo cargar la disponibilidad de horarios. Intenta de nuevo.');
    throw error;
  }
}
