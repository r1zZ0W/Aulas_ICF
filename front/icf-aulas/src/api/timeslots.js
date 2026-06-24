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
