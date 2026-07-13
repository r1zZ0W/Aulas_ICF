import { useState, useEffect, useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useReservation } from '../../context/ReservationContext';
import { getAvailableTimeSlots } from '../../api/timeslots';
import { getActiveSemester } from '../../api/semesters';
import { labelsToTimeSlotIds, toDateString } from '../../utils/reservations';

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

export function useReservaModal({ open, onClose, initialStart, initialEnd }) {
  const { rooms, visibleRooms, createBookingMutation } = useReservation();
  const availableRooms = rooms.filter(r => visibleRooms.has(r.uuid));

  const { data: activeSemester } = useQuery({
    queryKey: ['semesters', 'active'],
    queryFn: getActiveSemester,
    staleTime: 60_000,
  });

  // Form state
  const [step, setStep] = useState(1);
  const [pickedDate, setPickedDate] = useState(null);
  const [roomId, setRoomId] = useState('');
  const [className, setClassName] = useState('');
  const [startLabel, setStartLabel] = useState('');
  const [endLabel, setEndLabel] = useState('');
  const [attendees, setAttendees] = useState('');
  const [recurring, setRecurring] = useState(false);
  const [repeatUntil, setRepeatUntil] = useState('');
  const [selectedDays, setSelectedDays] = useState([]);
  // Mandatory student roster (.xlsx). React only holds the File object — the content is
  // never parsed client-side; the backend validates format, duplicates, and row count.
  const [file, setFile] = useState(null);
  const [fileError, setFileError] = useState('');

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

  // Reset room selection when the room gets hidden from the sidebar
  useEffect(() => {
    if (roomId && !visibleRooms.has(roomId)) {
      setRoomId('');
      setAttendees('');
    }
  }, [visibleRooms, roomId]);

  useEffect(() => {
    if (!open) return;

    setRoomId('');
    setClassName('');
    setAttendees('');
    setRecurring(false);
    setRepeatUntil('');
    setSelectedDays([]);
    setFile(null);
    setFileError('');

    if (initialStart) {
      // Display-only hint (see snapToHalfHourHms) — no room is known yet at this point,
      // so it can't be validated against real availability. handleRoomChange discards it
      // as soon as a room is picked.
      const d = new Date(initialStart);
      const defaultEnd = initialEnd ?? new Date(d.getTime() + 30 * 60 * 1000);
      setPickedDate(d);
      setStep(2);
      setStartLabel(snapToHalfHourHms(d));
      setEndLabel(snapToHalfHourHms(defaultEnd));
    } else {
      setStep(1);
      setPickedDate(null);
      setStartLabel('');
      setEndLabel('');
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
    setRecurring(r => {
      if (!r && pickedDate) {
        const day = jsWeekdayToEnum(pickedDate);
        setSelectedDays([day]);
      } else if (r) {
        setSelectedDays([]);
        setRepeatUntil('');
      }
      return !r;
    });
  };

  /**
   * Toggles a day of the week in the selected days array.
   * @param {string} val - The day of the week to toggle (e.g., 'MON').
   */
  const toggleDay = (val) => {
    setSelectedDays(prev =>
      prev.includes(val) ? prev.filter(d => d !== val) : [...prev, val]
    );
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
    setRoomId(val);
    setAttendees('');
    // A different room has a wholly different availability picture — discard any prior time
    // choice (including the initialStart display hint) and let the reset effects below repopulate
    // once this room's real availability loads.
    setStartLabel('');
    setEndLabel('');
  };

  const handleStartChange = (value) => {
    setStartLabel(value);
    const slot = availableSlots.find(s => s.startTime === value);
    // Default to the shortest valid block (the chosen start slot's own end) — always a legal
    // option since it's guaranteed to be `endSlots[0]` for this same start.
    setEndLabel(slot?.endTime ?? '');
  };

  /**
   * Validates only what the browser can know without reading the file — extension and
   * size (the backend's 1 MB multipart limit). Content validation (OOXML magic number,
   * duplicates, row count vs attendees) is exclusively the backend's job.
   *
   * @param {File|null} selected - The file chosen in the input, or null to clear it.
   */
  const handleFileChange = (selected) => {
    if (!selected) {
      setFile(null);
      setFileError('');
      return;
    }
    if (!selected.name.toLowerCase().endsWith('.xlsx')) {
      setFile(null);
      setFileError('El archivo debe ser un Excel (.xlsx).');
      return;
    }
    if (selected.size > 1024 * 1024) {
      setFile(null);
      setFileError('El archivo no debe exceder 1 MB.');
      return;
    }
    setFile(selected);
    setFileError('');
  };

  // ── Reset/default effects ─────────────────────────────────────────────────
  // Both effects gate on `queryEnabled` (no room/date picked yet → nothing to validate against,
  // don't touch the current value) AND `!slotsLoading` (fetch in flight → don't clobber the
  // current selection with a stale/empty read before the real result lands; this matters when
  // the date changes while a room is already selected — see handleDatePick).
  //
  // IMPORTANT: an empty `startSlots`/`endSlots` result AFTER loading has finished is a legitimate
  // outcome (the room is fully booked that day) and must still clear an invalid stale label —
  // the guard is intentionally `slotsLoading`, not `availableSlots.length === 0`.
  useEffect(() => {
    if (!queryEnabled || slotsLoading) return;
    const stillValid = startSlots.some(s => s.startTime === startLabel);
    if (!stillValid) {
      const next = startSlots[0]?.startTime ?? '';
      setStartLabel(next);
      setEndLabel(next ? (availableSlots.find(s => s.startTime === next)?.endTime ?? '') : '');
    }
  }, [queryEnabled, slotsLoading, startSlots, startLabel, availableSlots]);

  useEffect(() => {
    if (!queryEnabled || slotsLoading) return;
    if (!endLabel) return;
    const stillValid = endSlots.some(s => s.value === endLabel);
    if (!stillValid) setEndLabel(endSlots[0]?.value ?? '');
  }, [queryEnabled, slotsLoading, endSlots, endLabel]);

  const canSubmit =
    Boolean(roomId) &&
    className.trim().length > 0 &&
    Boolean(startLabel) &&
    Boolean(endLabel) &&
    Number(attendees) >= 1 &&
    Boolean(file) &&
    (!recurring || selectedDays.length > 0) &&
    !createBookingMutation.isPending;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!canSubmit) return;

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
    };

    try {
      await createBookingMutation.mutateAsync({ payload, file });
      onClose();
    } catch (_) {
      // toast already shown by useApiMutation's onError handler
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
    setClassName,
    startLabel,
    endLabel,
    setEndLabel,
    attendees,
    setAttendees,
    recurring,
    repeatUntil,
    setRepeatUntil,
    selectedDays,
    file,
    fileError,
    handleFileChange,
    availableRooms,
    room,
    startSlots,
    endSlots,
    slotsLoading,
    canSubmit,
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
  };
}
