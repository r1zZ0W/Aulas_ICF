import { useState, useEffect, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useReservation } from '../../context/ReservationContext';
import { useAuth } from '../../context/AuthContext';
import { ROLES } from '../../utils/roles';
import { getAvailableTimeSlots } from '../../api/timeslots';
import { getActiveSemester } from '../../api/semesters';
import { labelsToTimeSlotIds, toDateString } from '../../utils/reservations';
import { useZodForm } from '../../hooks/useZodForm';
import { ReservaFormSchema, BOOKING_DTO_MAP } from '../../schemas/reservation/reservaForm.js';

/**
 * Empty/default state for the Zod-backed form fields (user-authored inputs only).
 * Non-input state (`step`, `pickedDate`, query/derived state) lives in separate
 * `useState`s below — see the file header note in useZodForm.js for why only
 * authored fields belong in the Zod form.
 */
const EMPTY_RESERVA = {
  roomId: '',
  className: '',
  startLabel: '',
  endLabel: '',
  attendees: '',
  file: null,
  recurring: false,
  repeatUntil: '',
  selectedDays: [],
  reserveForOther: false,
  selectedUser: null,
};

/** Day-of-week names as the backend enum expects them. */
export const WEEKDAY_OPTIONS = [
  { value: 'MONDAY', label: 'Lun' },
  { value: 'TUESDAY', label: 'Mar' },
  { value: 'WEDNESDAY', label: 'Mié' },
  { value: 'THURSDAY', label: 'Jue' },
  { value: 'FRIDAY', label: 'Vie' },
  { value: 'SATURDAY', label: 'Sáb' },
];

/** Returns the backend DayOfWeek enum value for a JS Date's weekday. */
function jsWeekdayToEnum(date) {
  const map = ['SUNDAY', 'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY'];
  return map[date.getDay()];
}

/** @param {Date} a @param {Date} b @returns {boolean} */
function sameDay(a, b) {
  return (
    a.getFullYear() === b.getFullYear() &&
    a.getMonth() === b.getMonth() &&
    a.getDate() === b.getDate()
  );
}

/** @param {number} h @param {number} m @returns {number} */
const toMins = (h, m) => h * 60 + m;

/** @param {number} h @param {number} m @returns {string} */
const fmt = (h, m) => `${h}:${String(m).padStart(2, '0')}`;

/**
 * Returns available start time slots for the given date.
 * Slots before the current half-hour are excluded when the date is today.
 *
 * @deprecated No longer used internally by {@link useReservaModal} — start/end options now come
 * from {@link getAvailableTimeSlots} (real per-classroom availability). Kept exported in case
 * another consumer relies on the fixed 07:00–19:30 schedule this produces.
 */
export function getStartSlots(date) {
  const now = new Date();
  const isToday = sameDay(date, now);
  const nowMins = now.getHours() * 60 + now.getMinutes();
  const slots = [];
  for (let h = 7; h <= 19; h++) {
    for (let m = 0; m < 60; m += 30) {
      if (h === 19 && m === 30) continue;
      if (isToday && toMins(h, m) - nowMins <= 15) continue;
      slots.push({ h, m, label: fmt(h, m) });
    }
  }
  return slots;
}

/**
 * Returns available end time slots given a start time.
 * @deprecated see {@link getStartSlots}.
 */
export function getEndSlots(startH, startM) {
  const startMins = toMins(startH, startM);
  const slots = [];
  for (let h = 7; h <= 20; h++) {
    for (let m = 0; m < 60; m += 30) {
      if (h === 20 && m > 0) break;
      if (toMins(h, m) <= startMins) continue;
      slots.push({ h, m, label: fmt(h, m) });
    }
  }
  return slots;
}

/**
 * Finds the slot matching the given Date (snapped to half-hour) or returns the first slot.
 * @deprecated see {@link getStartSlots}.
 */
export function snapSlot(slots, date) {
  if (!date || !slots.length) return slots[0] ?? null;
  const d = new Date(date);
  const m = d.getMinutes() >= 30 ? 30 : 0;
  return slots.find(s => s.h === d.getHours() && s.m === m) ?? slots[0];
}

/**
 * Snaps a Date's minutes down to the nearest half-hour boundary and formats it as "HH:MM:SS",
 * matching the wire format of {@link import('../../schemas/timeSlot.js').TimeSlot}.
 *
 * Used only to seed a transient display hint for `startLabel`/`endLabel` when the modal opens
 * from a calendar click (`initialStart`/`initialEnd`) — at that point no classroom has been
 * picked yet (the calendar overlays every room, it never carries room context), so there is
 * nothing to validate this hint against. It is superseded the moment a room is chosen:
 * `handleRoomChange` clears both labels and the real availability query takes over.
 *
 * @param {Date|null} date
 * @returns {string} e.g. "07:30:00", or '' when `date` is falsy
 */
function snapToHalfHourHms(date) {
  if (!date) return '';
  const d = new Date(date);
  const h = d.getHours();
  const m = d.getMinutes() >= 30 ? 30 : 0;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:00`;
}

function isRangeAvailable(availableSlots, startLabel, endLabel) {
  if (!startLabel || !endLabel || availableSlots.length === 0) return false;
  const toMinutes = (str) => {
    const [h, m] = str.split(':').map(Number);
    return h * 60 + m;
  };
  const startMins = toMinutes(startLabel);
  const endMins = toMinutes(endLabel);
  if (startMins >= endMins) return false;

  const availableStarts = new Set(
    availableSlots.map(s => toMinutes(s.startTime))
  );

  for (let mins = startMins; mins < endMins; mins += 30) {
    if (!availableStarts.has(mins)) {
      return false;
    }
  }
  return true;
}

function findClosestAvailableRange(availableSlots, startSlots, plannedStart, plannedEnd) {
  if (!plannedStart || !plannedEnd || startSlots.length === 0) return null;

  const toMinutes = (str) => {
    const [h, m] = str.split(':').map(Number);
    return h * 60 + m;
  };

  const plannedStartMins = toMinutes(plannedStart);
  const plannedEndMins = toMinutes(plannedEnd);
  const plannedDuration = plannedEndMins - plannedStartMins;

  let bestCandidate = null;

  for (const slot of startSlots) {
    const S_mins = toMinutes(slot.startTime);
    
    // Find all contiguous slots starting from this one
    const startIdx = availableSlots.findIndex(s => s.startTime === slot.startTime);
    if (startIdx === -1) continue;

    let contiguousCount = 1;
    let i = startIdx;
    while (i < availableSlots.length - 1) {
      const cur = availableSlots[i];
      const next = availableSlots[i + 1];
      if (cur.endTime !== next.startTime) break;
      contiguousCount++;
      i++;
    }

    const targetNumSlots = Math.round(plannedDuration / 30);
    const actualNumSlots = Math.min(contiguousCount, targetNumSlots);
    
    const actualEnd = availableSlots[startIdx + actualNumSlots - 1].endTime;
    const actualEndMins = toMinutes(actualEnd);
    const actualDuration = actualEndMins - S_mins;

    const startDiff = Math.abs(S_mins - plannedStartMins);
    const durationDiff = Math.abs(actualDuration - plannedDuration);

    const candidate = {
      start: slot.startTime,
      end: actualEnd,
      startDiff,
      durationDiff,
      startMins: S_mins
    };

    if (!bestCandidate) {
      bestCandidate = candidate;
    } else {
      if (candidate.startDiff < bestCandidate.startDiff) {
        bestCandidate = candidate;
      } else if (candidate.startDiff === bestCandidate.startDiff) {
        if (candidate.durationDiff < bestCandidate.durationDiff) {
          bestCandidate = candidate;
        } else if (candidate.durationDiff === bestCandidate.durationDiff) {
          if (candidate.startMins < bestCandidate.startMins) {
            bestCandidate = candidate;
          }
        }
      }
    }
  }

  return bestCandidate;
}


export function useReservaModal({ open, onClose, initialStart, initialEnd }) {
  const { rooms, visibleRooms, createBookingMutation } = useReservation();
  const availableRooms = rooms.filter(r => visibleRooms.has(r.uuid));

  const { user } = useAuth();
  const isAdmin = user?.role === ROLES.ADMIN;

  const { data: activeSemester } = useQuery({
    queryKey: ['semesters', 'active'],
    queryFn: getActiveSemester,
    staleTime: 60_000,
  });

  // Form state
  const [step, setStep] = useState(1);
  const [pickedDate, setPickedDate] = useState(null);

  // Zod-backed form instance — single source of truth for every user-authored field.
  // File required/size/format rules live in the schema too (see reservaForm.js), so
  // there is no separate fileError state: errors.file is the only source of file errors.
  const zod = useZodForm(EMPTY_RESERVA, ReservaFormSchema, { dtoMap: BOOKING_DTO_MAP });
  const {
    roomId, className, startLabel, endLabel, attendees, file,
    recurring, repeatUntil, selectedDays, reserveForOther, selectedUser,
  } = zod.formData;

  const [plannedStart, setPlannedStart] = useState('');
  const [plannedEnd, setPlannedEnd] = useState('');
  const [lastAutoSelected, setLastAutoSelected] = useState(null); // { roomId, dateStr }

  const forDate = pickedDate ?? new Date();
  const dateStr = pickedDate ? toDateString(pickedDate) : null;

  // ── Real availability for the picked room + date ─────────────────────────
  // `available = full catalog − occupied`, ordered by startTime ASC (backend contract).
  const queryEnabled = Boolean(roomId && dateStr);
  const { data: availableSlots = [], isLoading: slotsLoading } = useQuery({
    queryKey: ['timeslots', 'available', roomId, dateStr],
    queryFn: () => getAvailableTimeSlots({ classroomUuid: roomId, date: dateStr }),
    enabled: queryEnabled,
    staleTime: 30_000,
  });

  // Reset room selection when the room gets hidden from the sidebar. Programmatic — not a
  // user edit — so it must not clear a server error still shown on another field; see
  // useZodForm.js's syncFieldValue vs handleChange doc comment.
  useEffect(() => {
    if (roomId && !visibleRooms.has(roomId)) {
      zod.syncFieldValue('roomId', '');
      zod.syncFieldValue('attendees', '');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visibleRooms, roomId]);

  useEffect(() => {
    if (!open) return;

    setLastAutoSelected(null);

    if (initialStart) {
      // Display-only hint (see snapToHalfHourHms) — no room is known yet at this point,
      // so it can't be validated against real availability. handleRoomChange discards it
      // as soon as a room is picked.
      const d = new Date(initialStart);
      const defaultEnd = initialEnd ?? new Date(d.getTime() + 30 * 60 * 1000);
      const startHms = snapToHalfHourHms(d);
      const endHms = snapToHalfHourHms(defaultEnd);
      zod.reset({ ...EMPTY_RESERVA, startLabel: startHms, endLabel: endHms });
      setPickedDate(d);
      setStep(2);
      setPlannedStart(startHms);
      setPlannedEnd(endHms);
    } else {
      zod.reset(EMPTY_RESERVA);
      setStep(1);
      setPickedDate(null);
      setPlannedStart('');
      setPlannedEnd('');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  /**
   * Picking a date does NOT clear startLabel/endLabel: if a room was already selected in an
   * earlier step (user went back via the "←" button to change the date), the previous time
   * choice is worth preserving — it's re-validated against the new date's real availability by
   * the reset effects below once the query for the new date resolves. If no room is selected
   * yet, the selects render empty/disabled until one is picked (see queryEnabled).
   */
  const handleDatePick = (date) => {
    setPickedDate(date);
    setStep(2);
  };

  // When recurring is toggled ON, pre-check the picked date's weekday
  const handleRecurringToggle = () => {
    const next = !recurring;
    zod.handleChange('recurring', next);
    if (next && pickedDate) {
      zod.handleChange('selectedDays', [jsWeekdayToEnum(pickedDate)]);
    } else {
      zod.handleChange('selectedDays', []);
      zod.handleChange('repeatUntil', '');
    }
  };

  /**
   * Toggles the "reservar para otro usuario" option (admin-only). Turning it off
   * clears any previously selected user — mirrors handleRecurringToggle's reset of
   * dependent state above.
   */
  const handleReserveForOtherToggle = () => {
    const next = !reserveForOther;
    zod.handleChange('reserveForOther', next);
    if (!next) zod.handleChange('selectedUser', null);
  };

  /**
   * Toggles a day of the week in the selected days array.
   * @param {string} val - The day of the week to toggle (e.g., 'MON').
   */
  const toggleDay = (val) => {
    const next = selectedDays.includes(val)
      ? selectedDays.filter(d => d !== val)
      : [...selectedDays, val];
    zod.handleChange('selectedDays', next);
  };

  const room = rooms.find(r => r.uuid === roomId) ?? null;

  // ── Start options: available slots, filtered by the "≥15 min from now" guard for today ────
  const startSlots = useMemo(() => {
    if (!pickedDate) return [];
    const now = new Date();
    const isToday = sameDay(pickedDate, now);
    const nowMins = now.getHours() * 60 + now.getMinutes();
    return availableSlots.filter(slot => {
      if (!isToday) return true;
      const [h, m] = slot.startTime.split(':').map(Number);
      return toMins(h, m) - nowMins > 15;
    });
  }, [availableSlots, pickedDate]);

  // ── End options: strict chronological contiguity walk over `availableSlots` (NOT `startSlots`) ──
  // `availableSlots` already has occupied slots removed, so two array-adjacent entries are not
  // necessarily time-adjacent (a booked slot may sit between them). Walking by array index alone
  // would silently offer an end time that reaches across an occupied block. Comparing
  // `cur.endTime === next.startTime` and stopping at the first mismatch prevents that:
  // e.g. catalog A(07:00–08:00), B(08:00–09:00, booked), C(09:00–10:00) → available = [A, C].
  // Picking A as start must stop at endSlots = [08:00] (A's own end), never reach C's 10:00.
  const endSlots = useMemo(() => {
    if (!startLabel || availableSlots.length === 0) return [];
    const startIdx = availableSlots.findIndex(s => s.startTime === startLabel);
    if (startIdx === -1) return [];

    const ends = [{ id: availableSlots[startIdx].id, value: availableSlots[startIdx].endTime }];
    let i = startIdx;
    while (i < availableSlots.length - 1) {
      const cur = availableSlots[i];
      const next = availableSlots[i + 1];
      if (cur.endTime !== next.startTime) break; // gap: an occupied slot sits in between
      ends.push({ id: next.id, value: next.endTime });
      i++;
    }
    return ends;
  }, [startLabel, availableSlots]);

  const handleRoomChange = (val) => {
    zod.handleChange('roomId', val);
    zod.handleChange('attendees', '');
  };

  const handleStartChange = (value) => {
    zod.handleChange('startLabel', value);
    const slot = availableSlots.find(s => s.startTime === value);
    // Default to the shortest valid block (the chosen start slot's own end) — always a legal
    // option since it's guaranteed to be `endSlots[0]` for this same start.
    zod.handleChange('endLabel', slot?.endTime ?? '');
  };

  /**
   * Sets (or clears) the selected roster file. Extension/size validation and the
   * required check all live in ReservaFormSchema's `file` field (see reservaForm.js) —
   * errors.file is the single source of truth for file errors, no local fileError state.
   * Content validation (OOXML magic number, duplicates, row count vs attendees) is
   * exclusively the backend's job.
   *
   * @param {File|null} selected - The file chosen in the input, or null to clear it.
   */
  const handleFileChange = (selected) => {
    zod.handleChange('file', selected);
  };

  // ── Reset/default and auto-selection effects ──────────────────────────────
  const isPlannedTimeUnavailable = useMemo(() => {
    if (!plannedStart || !plannedEnd || !roomId || slotsLoading) return false;
    return !isRangeAvailable(availableSlots, plannedStart, plannedEnd);
  }, [plannedStart, plannedEnd, roomId, slotsLoading, availableSlots]);

  // Auto-selection and closest range search when room/date changes
  useEffect(() => {
    if (!roomId || !dateStr || slotsLoading) return;

    if (
      lastAutoSelected &&
      lastAutoSelected.roomId === roomId &&
      lastAutoSelected.dateStr === dateStr
    ) {
      return;
    }

    setLastAutoSelected({ roomId, dateStr });

    // Programmatic reconciliation, not a user edit — syncFieldValue, so a server error
    // shown on startLabel/endLabel from a prior submit survives this recompute.
    if (plannedStart && plannedEnd) {
      if (isRangeAvailable(availableSlots, plannedStart, plannedEnd)) {
        zod.syncFieldValue('startLabel', plannedStart);
        zod.syncFieldValue('endLabel', plannedEnd);
      } else {
        const closest = findClosestAvailableRange(availableSlots, startSlots, plannedStart, plannedEnd);
        if (closest) {
          zod.syncFieldValue('startLabel', closest.start);
          zod.syncFieldValue('endLabel', closest.end);
        } else {
          zod.syncFieldValue('startLabel', '');
          zod.syncFieldValue('endLabel', '');
        }
      }
    } else {
      const next = startSlots[0]?.startTime ?? '';
      zod.syncFieldValue('startLabel', next);
      zod.syncFieldValue('endLabel', next ? (availableSlots.find(s => s.startTime === next)?.endTime ?? '') : '');
    }
  }, [roomId, dateStr, slotsLoading, availableSlots, startSlots, plannedStart, plannedEnd, lastAutoSelected]);

  // Reconciles startLabel/endLabel against freshly (re)computed availability — runs whenever
  // the derived slot lists change, not directly from a user event, so it's programmatic too.
  useEffect(() => {
    if (!queryEnabled || slotsLoading) return;

    if (startLabel) {
      const startValid = startSlots.some(s => s.startTime === startLabel);
      if (!startValid) {
        const next = startSlots[0]?.startTime ?? '';
        zod.syncFieldValue('startLabel', next);
        zod.syncFieldValue('endLabel', next ? (availableSlots.find(s => s.startTime === next)?.endTime ?? '') : '');
        return;
      }
    }

    if (endLabel) {
      const endValid = endSlots.some(s => s.value === endLabel);
      if (!endValid) {
        zod.syncFieldValue('endLabel', endSlots[0]?.value ?? '');
      }
    }
  }, [queryEnabled, slotsLoading, startSlots, endSlots, startLabel, endLabel, availableSlots]);

  // Readiness of ASYNC infrastructure only (never input validity — that's validateAll()'s job
  // inside handleSubmit). While slots are still loading or the mutation is in flight, a
  // "valid" form must still not submit: timeSlotIds could resolve empty/stale otherwise.
  const isReadyToSubmit = !slotsLoading && !createBookingMutation.isPending;

  const handleSubmit = async (e) => {
    e.preventDefault();

    // validateAll() marks every field as touched and returns false if Zod fails — this
    // illuminates all required-field errors even on a direct "Reservar Aula" click with
    // no prior field interaction.
    const isValid = zod.validateAll();
    if (!isValid) return;

    const timeSlotIds = labelsToTimeSlotIds(availableSlots, startLabel, endLabel);
    if (timeSlotIds.length === 0) {
      return;
    }

    const payload = {
      classroomUuid: roomId,
      attendeeCount: Number(attendees),
      timeSlotIds,
      startDate: toDateString(forDate),
      title: className,
      ...(recurring && repeatUntil ? { repeatUntil } : {}),
      ...(recurring && selectedDays.length > 0 ? { daysOfWeek: selectedDays } : {}),
      ...(reserveForOther && selectedUser ? { userUuid: selectedUser.uuid } : {}),
    };

    try {
      await createBookingMutation.mutateAsync({ payload, file });
      onClose();
    } catch (err) {
      // toast already shown by useApiMutation's onError handler; additionally highlight
      // the offending input(s) — e.g. a slot conflict marks both startLabel and endLabel.
      if (err.fieldErrors) zod.setServerErrors(err.fieldErrors);
    }
  };

  const maxAttendees = room?.capacity ?? 0;
  const semesterEnd = activeSemester?.endDate ?? '';

  const formatDate = d =>
    d.toLocaleDateString('es-MX', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });

  return {
    step,
    setStep,
    pickedDate,
    roomId,
    className,
    setClassName: (v) => zod.handleChange('className', v),
    startLabel,
    endLabel,
    setEndLabel: (v) => zod.handleChange('endLabel', v),
    attendees,
    setAttendees: (v) => zod.handleChange('attendees', v),
    recurring,
    repeatUntil,
    setRepeatUntil: (v) => zod.handleChange('repeatUntil', v),
    selectedDays,
    file,
    handleFileChange,
    availableRooms,
    room,
    startSlots,
    endSlots,
    slotsLoading,
    isReadyToSubmit,
    maxAttendees,
    semesterEnd,
    formatDate,
    handleDatePick,
    handleRecurringToggle,
    toggleDay,
    handleRoomChange,
    handleStartChange,
    handleSubmit,
    createBookingMutation,
    // Admin-only: reserve on behalf of another user
    isAdmin,
    reserveForOther,
    selectedUser,
    setSelectedUser: (v) => zod.handleChange('selectedUser', v),
    handleReserveForOtherToggle,
    isPlannedTimeUnavailable,
    plannedStart,
    // Zod-backed validation surface — errors only visible for touched fields (see useZodForm.js)
    errors: zod.errors,
    handleBlur: zod.handleBlur,
  };
}
