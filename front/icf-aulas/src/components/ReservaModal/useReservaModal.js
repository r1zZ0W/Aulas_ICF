import { useState, useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';
import { useReservation } from '../../context/ReservationContext';
import { getTimeSlots } from '../../api/timeslots';
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
 */
export function getStartSlots(date) {
  const now = new Date();
  const isToday = sameDay(date, now);
  const nowMins = isToday
    ? now.getHours() * 60 + (now.getMinutes() >= 30 ? 30 : 0)
    : -1;
  const slots = [];
  for (let h = 7; h <= 19; h++) {
    for (let m = 0; m < 60; m += 30) {
      if (h === 19 && m === 30) continue;
      if (toMins(h, m) <= nowMins) continue;
      slots.push({ h, m, label: fmt(h, m) });
    }
  }
  return slots;
}

/** Returns available end time slots given a start time. */
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

/** Finds the slot matching the given Date (snapped to half-hour) or returns the first slot. */
export function snapSlot(slots, date) {
  if (!date || !slots.length) return slots[0] ?? null;
  const d = new Date(date);
  const m = d.getMinutes() >= 30 ? 30 : 0;
  return slots.find(s => s.h === d.getHours() && s.m === m) ?? slots[0];
}

export function useReservaModal({ open, onClose, initialStart, initialEnd }) {
  const { rooms, visibleRooms, createBookingMutation } = useReservation();
  const availableRooms = rooms.filter(r => visibleRooms.has(r.uuid));

  // Catalogs (static data, cached for the session)
  const { data: timeslotCatalog = [] } = useQuery({
    queryKey: ['timeslots'],
    queryFn: getTimeSlots,
    staleTime: Infinity,
  });
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

    if (initialStart) {
      const d = new Date(initialStart);
      const startSlots = getStartSlots(d);
      const defStart = snapSlot(startSlots, initialStart);
      const endSlots = defStart ? getEndSlots(defStart.h, defStart.m) : [];
      const defEnd = snapSlot(endSlots, initialEnd);
      setPickedDate(d);
      setStep(2);
      setStartLabel(defStart?.label ?? '');
      setEndLabel(defEnd?.label ?? '');
    } else {
      setStep(1);
      setPickedDate(null);
      setStartLabel('');
      setEndLabel('');
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const handleDatePick = (date) => {
    setPickedDate(date);
    const startSlots = getStartSlots(date);
    const defStart = startSlots[0] ?? null;
    const endSlots = defStart ? getEndSlots(defStart.h, defStart.m) : [];
    const defEnd = endSlots[0] ?? null;
    setStartLabel(defStart?.label ?? '');
    setEndLabel(defEnd?.label ?? '');
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

  const forDate = pickedDate ?? new Date();
  const startSlots = getStartSlots(forDate);
  const startSlot = startSlots.find(s => s.label === startLabel) ?? null;
  const endSlots = startSlot ? getEndSlots(startSlot.h, startSlot.m) : [];
  const room = rooms.find(r => r.uuid === roomId) ?? null;

  const handleRoomChange = val => { setRoomId(val); setAttendees(''); };
  const handleStartChange = val => {
    setStartLabel(val);
    const slot = startSlots.find(s => s.label === val);
    if (!slot) return;
    const eSlots = getEndSlots(slot.h, slot.m);
    if (!eSlots.find(s => s.label === endLabel)) setEndLabel(eSlots[0]?.label ?? '');
  };

  const canSubmit =
    Boolean(roomId) &&
    className.trim().length > 0 &&
    Boolean(startLabel) &&
    Boolean(endLabel) &&
    Number(attendees) >= 1 &&
    (!recurring || selectedDays.length > 0) &&
    !createBookingMutation.isPending;

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (!canSubmit) return;

    const timeSlotIds = labelsToTimeSlotIds(timeslotCatalog, startLabel, endLabel);
    if (timeSlotIds.length === 0) {
      return;
    }

    const payload = {
      classroomUuid: roomId,
      attendeeCount: Number(attendees),
      timeSlotIds,
      startDate: toDateString(forDate),
      ...(recurring && repeatUntil ? { repeatUntil } : {}),
      ...(recurring && selectedDays.length > 0 ? { daysOfWeek: selectedDays } : {}),
    };

    try {
      await createBookingMutation.mutateAsync(payload);
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
    availableRooms,
    room,
    startSlots,
    endSlots,
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
