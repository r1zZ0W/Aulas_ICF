/**
 * @fileoverview Validation schema for available months.
 */
import { z } from 'zod';

/**
 * Zod schema for validating the list of distinct months containing active reservations.
 * Expects an array of strings in YYYY-MM format, ordered from newest to oldest.
 */
export const AvailableMonthsSchema = z.array(z.string().regex(/^\d{4}-\d{2}$/));

export default AvailableMonthsSchema;
