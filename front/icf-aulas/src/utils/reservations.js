/**
 * @fileoverview Timezone-safe utilities for the reservations calendar and history table.
 *
 * ## Why timezone matters
 * `new Date("2026-06-23")` parses as UTC midnight and can shift to the PREVIOUS evening
 * in local time (e.g. "Mon Jun 22 19:00:00 CDT 2026" in UTC-5), moving a Tuesday
 * reservation to Monday on the calendar. All date construction uses `new Date(y, m-1, d)`
 * (local-time constructor) instead.
 */

/**
 * Formats a local Date as a YYYY-MM-DD string without UTC conversion.
 * Use this whenever sending a date to the API or building a React Query key.
 *
 * @param {Date} date
 * @returns {string} e.g. "2026-06-23"
 */
export function toDateString(date) {
  const y = date.getFullYear();
  const m = String(date.getMonth() + 1).padStart(2, '0');
  const d = String(date.getDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/**
 * Converts a YYYY-MM-DD date string and an ordered list of time-slot DTOs into
 * `{ start, end }` Date objects suitable for FullCalendar's `events` array.
 *
 * The first slot's `startTime` and the last slot's `endTime` delimit the block.
 * Slots are expected already ordered by `time_slot_id ASC` (guaranteed by the backend's
 * `@OrderBy` annotation on `ReservInstance.slots`).
 *
 * @param {string} dateStr  - YYYY-MM-DD date string
 * @param {Array<{ startTime: string, endTime: string }>} timeSlots - ordered slot DTOs
 * @returns {{ start: Date|null, end: Date|null }}
 */
export function slotsToRange(dateStr, timeSlots) {
  if (!timeSlots || timeSlots.length === 0) return { start: null, end: null };

  // Parse YYYY-MM-DD into local-time integers — never use new Date("YYYY-MM-DD")
  const [year, month, day] = dateStr.split('-').map(Number);

  const first = timeSlots[0];
  const last  = timeSlots[timeSlots.length - 1];

  const [fh, fm] = first.startTime.split(':').map(Number);
  const [lh, lm] = last.endTime.split(':').map(Number);

  return {
    start: new Date(year, month - 1, day, fh, fm),
    end:   new Date(year, month - 1, day, lh, lm),
  };
}

/**
 * Maps a time-slot catalog to the subset of slot IDs whose block falls within
 * [startLabel, endLabel) in wall-clock minutes. Used to translate the modal's
 * time dropdowns ("10:0" / "12:0") into the `timeSlotIds` array the API expects.
 *
 * A slot is included when `slotStart >= startMins AND slotEnd <= endMins`.
 *
 * @param {Array<{ id: number, startTime: string, endTime: string }>} catalog
 * @param {string} startLabel - Wall-clock label from the dropdown, e.g. "10:0" or "10:00"
 * @param {string} endLabel   - Wall-clock label from the dropdown, e.g. "12:0" or "12:00"
 * @returns {number[]} Ordered list of time-slot IDs
 */
export function labelsToTimeSlotIds(catalog, startLabel, endLabel) {
  if (!catalog?.length || !startLabel || !endLabel) return [];

  const [sh, sm] = startLabel.split(':').map(Number);
  const [eh, em] = endLabel.split(':').map(Number);
  const startMins = sh * 60 + sm;
  const endMins   = eh * 60 + em;

  return catalog
    .filter(slot => {
      const [slH, slM] = slot.startTime.split(':').map(Number);
      const [elH, elM] = slot.endTime.split(':').map(Number);
      const slotStart = slH * 60 + slM;
      const slotEnd   = elH * 60 + elM;
      return slotStart >= startMins && slotEnd <= endMins;
    })
    .map(slot => slot.id);
}

// ── Cancellation guard ───────────────────────────────────────────────────────

/**
 * Returns `true` when the reservation's end time has already passed — i.e. whether it can
 * still be cancelled. This is an *operational* rule, precise to the minute (last time-slot's
 * `endTime`), and answers a different question than the backend-derived `timeframe` field
 * (`ReservInstanceResponseDTO.timeframe`, `UPCOMING`/`PAST`), which classifies by calendar day
 * for the history badge/filter. A reservation happening right now is `timeframe: 'UPCOMING'`
 * (today hasn't ended) but `hasReservationEnded(...) === true` once its last slot is over — so
 * the "Cancelar" button and the "Finalizada" badge can disagree for a few hours on the same day,
 * by design: one gates an action, the other classifies a record.
 *
 * Uses the last time-slot's `endTime` combined with `date` to build a local-time
 * Date, then compares against `Date.now()`. Returns `false` (i.e. still actionable)
 * when the date or slots are missing, to avoid accidentally hiding buttons.
 *
 * @param {{ date: string, timeSlots: Array<{ endTime: string }> }} reservation
 * @returns {boolean}
 */
export function hasReservationEnded(reservation) {
  if (!reservation?.date || !reservation?.timeSlots?.length) return false;
  const [year, month, day] = reservation.date.split('-').map(Number);
  const last = reservation.timeSlots[reservation.timeSlots.length - 1];
  const [lh, lm] = last.endTime.split(':').map(Number);
  const endDate = new Date(year, month - 1, day, lh, lm);
  return endDate < new Date();
}

// ── History table helpers ──────────────────────────────────────────────────────

/**
 * Formats a local Date as "H:MM" (no seconds, no leading zero on hour).
 * Exported so both ReservaInfoModal and the history table share the same format.
 *
 * @param {Date|null} date
 * @returns {string} e.g. "9:00" or "—" when date is null
 */
export function fmtTime(date) {
  if (!date) return '—';
  return `${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`;
}

/**
 * Derives the badge variant and label for a reservation instance.
 *
 * Precedence — a cancelled reservation never shows "Finalizada", and "Finalizada" takes
 * priority over "Reasignada" (both are just display hints on top of an ACTIVE status):
 *  1. CANCELLED_BY_USER                          → danger  / "Cancelada"
 *  2. CANCELLED_BY_ADMIN                          → danger  / "Cancelada por administrador"
 *  3. timeframe === 'PAST' (status still ACTIVE)  → neutral / "Finalizada"
 *  4. reassigned === true (status still ACTIVE)   → primary / "Reasignada"
 *  5. Otherwise (ACTIVE, upcoming, not reassigned) → success / "Activa"
 *
 * `timeframe` is read verbatim from the backend (`ReservInstanceResponseDTO.timeframe`) —
 * this function never recomputes "has this date passed?" itself, so the badge and the
 * `timeframe` filter in HistoryPage can never disagree about the same row.
 *
 * @param {{ status: string, reassigned?: boolean, timeframe?: 'UPCOMING'|'PAST' }} reservation
 * @returns {{ variant: 'success'|'danger'|'primary'|'neutral', label: string }}
 */
export function reservationBadge({ status, reassigned, timeframe } = {}) {
  if (status === 'CANCELLED_BY_USER') {
    return { variant: 'danger', label: 'Cancelada' };
  }
  if (status === 'CANCELLED_BY_ADMIN') {
    return { variant: 'danger', label: 'Cancelada por administrador' };
  }
  if (timeframe === 'PAST') {
    return { variant: 'neutral', label: 'Finalizada' };
  }
  if (reassigned) {
    return { variant: 'primary', label: 'Reasignada' };
  }
  return { variant: 'success', label: 'Activa' };
}

/**
 * Converts a `ReservInstanceResponseDTO` into a FullCalendar event object.
 * The full instance is stored in `extendedProps.instance` so the info modal can
 * read all fields without an extra lookup.
 *
 * @param {object} instance - ReservInstanceResponseDTO from the API
 * @param {string} color    - Hex color for this classroom (from roomById map)
 * @returns {object} FullCalendar event
 */
export function instanceToEvent(instance, color = '#64748b') {
  const { start, end } = slotsToRange(instance.date, instance.timeSlots);
  const instanceTitle = instance.title ?? instance.classroomName;
  const title = instance.userFullName
    ? `${instanceTitle || '(Sin nombre)'} - ${instance.userFullName}`
    : (instanceTitle || '(Sin nombre)');
  return {
    id:              instance.uuid,
    title,
    start,
    end,
    backgroundColor: color,
    borderColor:     color,
    extendedProps:   { instance },
  };
}
