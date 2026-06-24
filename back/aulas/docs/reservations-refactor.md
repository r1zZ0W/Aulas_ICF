# Reservations Module Refactor

## Why this refactor

The original design required an administrator to explicitly approve each reservation
before it occupied a classroom. This created an unnecessary queue: teachers had to
wait for approval even when the room was obviously free, and admins had to process a
backlog of pending reservations before the calendar was accurate.

The new rule is simpler: **a reservation is active the moment it is created**. The
classroom is occupied immediately. Availability is determined solely by whether a time
slot is already taken — there is no approval or waiting state.

This document describes the resulting lifecycle, API surface, validation rules, and how
to apply the accompanying database migration.

---

## Reservation instance lifecycle

```
POST /api/v1/reservations
        │
        ▼
    ┌────────┐
    │ ACTIVA │   ← created here; classroom slot is reserved immediately
    └────────┘
        │
   cancel (user or admin)
        │
        ├──→ CANCELADA_POR_MAESTRO   (PATCH /{uuid}/cancel)
        └──→ CANCELADA_POR_ADMIN     (PATCH /{uuid}/cancel-admin)

Cancellation physically removes the ReservSlot rows so the classroom
slot is released and can be rebooked immediately.
```

`ReservationGroupStatus` (ACTIVE / CANCELLED) is a separate concept that tracks the
recurring group, not individual instances — it is unchanged.

---

## Endpoints

### Reservation instances — `POST /api/v1/reservations`

Creates an **active** reservation (status `ACTIVA`). No admin action required.

### Availability calendar — `GET /api/v1/reservations/availability`

Returns **active** reservations for a classroom within a date range. Because only
active reservations hold `ReservSlot` rows, the response is always accurate without
a status filter.

### Cancel (teacher) — `PATCH /api/v1/reservations/{uuid}/cancel`

Cancels as the owning teacher. Sets status to `CANCELADA_POR_MAESTRO` and frees slots.

### Cancel (admin) — `PATCH /api/v1/reservations/{uuid}/cancel-admin`

Cancels as an administrator. Sets status to `CANCELADA_POR_ADMIN` and frees slots.

### Reassign — `PATCH /api/v1/reservations/{uuid}/reassign`

Admin-only. Moves an active reservation to a different classroom and/or time-slot
block. Conflict checks run before any slot mutation.

### Removed endpoints

| Endpoint | Reason |
|---|---|
| `GET /api/v1/reservations/pending` | No more pending queue |
| `PATCH /api/v1/reservations/{uuid}/approve` | No approval step |
| `PATCH /api/v1/reservations/{uuid}/reject` | No rejection step |

---

## Validation rules (enforced on creation)

| Rule | Detail |
|---|---|
| Classroom active | `isActive` must be explicitly `true` (null treated as inactive) |
| Date not in the past | Standard guard |
| No Sundays | No reservations on Sundays |
| Date within semester | Date must fall within `[semester.startDate, semester.endDate]` (inclusive) |
| Date matches group days | Day-of-week of the date must be in `group.daysOfWeek` |
| 15-minute lead time | Same-day reservations must start ≥ 15 min from now |
| Classroom conflict | No other active slot for the same classroom/date/time |
| User self-conflict | The teacher cannot hold two slots at the same date/time |

---

## Conflict detection and DB uniqueness

**Application layer:** `ReservInstanceRepository.existsConflict` and `existsUserConflict`
query `reserv_slots` directly, with no status predicate. Because cancelled reservations
no longer hold slot rows, the presence of any row means the slot is taken.

**Database layer:** `docs/reservations-refactor.sql` (step 3) replaces the old
non-unique indexes with:
- `uk_reserv_slots_classroom_time (classroom_id, date, time_slot_id)` — enforces no
  double-booking of a classroom slot.
- `uk_reserv_slots_user_time (user_id, date, time_slot_id)` — enforces no teacher
  self-overlap.

The application checks run first to give friendly error messages. The DB constraints
are the hard backstop against race conditions.

**Reassign ordering:** conflict checks → `slotRepository.deleteByInstance` →
`slotRepository.flush()` (explicit flush required) → insert new slots. The flush
forces Hibernate to send the DELETEs to the DB before the INSERTs, preventing a
transient UNIQUE constraint violation on the instance's own old rows.

---

## Classroom `toggleStatus`

### Endpoint — `PATCH /api/v1/classrooms/{uuid}/toggle-status`

Requires ADMIN role. Flips `isActive`:
- `true` → `false`: classroom hidden from the Maestro catalog; no new reservations allowed.
- `false` or `null` → `true`: classroom visible and bookable again.

Child classrooms that reference this classroom as their parent are **not affected** — the
`linkedRoom` FK is preserved regardless of the parent's status.

Returns the updated `ClassroomResponseDTO` with the new `isActive` value so the frontend
can update its local state without a separate GET.

### Removed endpoints

| Endpoint | Reason |
|---|---|
| `PATCH /api/v1/classrooms/{uuid}/deactivate` | Replaced by toggle-status |
| `PATCH /api/v1/classrooms/{uuid}/reactivate` | Replaced by toggle-status |

---

## Database migration

Apply **`docs/reservations-refactor.sql`** once on every environment before deploying
the updated JAR.

### What the script does

1. **Status migration** — converts existing rows:
   - `PENDIENTE` / `APROBADA` → `ACTIVA`
   - `RECHAZADA` → `CANCELADA_POR_ADMIN` (slots freed first)
   - Frees slots of all cancelled rows
2. **De-duplication** — cancels "loser" instances that share a slot key with a
   lower-id winner, so no UNIQUE violation occurs in step 3
3. **UNIQUE indexes** — drops the old non-unique indexes and creates
   `uk_reserv_slots_classroom_time` and `uk_reserv_slots_user_time`
4. **Classroom normalization** — sets `is_active = 1` where null and applies
   `NOT NULL DEFAULT 1`

### Pre-deploy checklist

- [ ] Back up the database before running the script.
- [ ] Run the script on staging first and verify with the queries at the bottom.
- [ ] Start the app with `ddl-auto=validate` on a clean copy to confirm Hibernate
      agrees with the resulting schema (no startup errors).
- [ ] Deploy the JAR.

### Flyway removal

Flyway has been removed from the project (`flyway-core` / `flyway-mysql` deps deleted,
`spring.flyway.*` config removed, `src/main/resources/db/migration/` deleted).
The `ddl-auto=update` setting in dev and `ddl-auto=validate` in prod are the only
Hibernate DDL controls. Future schema changes should be delivered as documented SQL
scripts under `docs/`.

---

## Frontend-driven additions (post-refactor)

These three backend additions were made to close gaps discovered when wiring the
frontend calendar to the real API.

### A1. Enriched `ReservInstanceResponseDTO` (v3.0)

The calendar requires slot times to place events in the hour grid.

- Added `String classroomName` — avoids a secondary lookup in the frontend.
- Added `List<TimeSlotDTO> timeSlots` — ordered by `time_slot_id ASC` (guaranteed by
  `@org.hibernate.annotations.OrderBy(clause = "time_slot_id ASC")` on
  `ReservInstance.slots`). The first slot's `startTime` and last slot's `endTime`
  define the event block.
- Fixed critical mapper bug: `reason ↔ motivo` and `attendeeCount ↔ numAsistentes`
  were not mapped, causing all responses to return `null` for those fields.
- Availability queries now use `JOIN FETCH ri.slots s LEFT JOIN FETCH s.timeSlot ts`
  to load the slot list eagerly (no N+1).

### A2. Optional `classroomUuid` on `/availability`

`GET /api/v1/reservations/availability` now accepts an optional `classroomUuid`
query parameter. When omitted, all active reservations across all classrooms in the
requested date window are returned. This allows the calendar to show every room at
once without a separate request per classroom.

### A3. Atomic booking endpoint

`POST /api/v1/reservations/booking` accepts a single `BookingRequestDTO` and creates
the `ReservationGroup` plus all `ReservInstance` / `ReservSlot` rows in one
transaction. No frontend loop is needed.

**Request body** (`BookingRequestDTO`):

| Field | Required | Description |
|---|---|---|
| `classroomUuid` | ✓ | UUID of the target classroom |
| `motivo` | ✓ | Purpose / class name (≤ 500 chars) |
| `numAsistentes` | ✓ | Expected attendee count |
| `timeSlotIds` | ✓ | List of slot IDs (1–24) to book |
| `startDate` | ✓ | First (or only) date, `YYYY-MM-DD`, not in the past |
| `repeatUntil` | — | Last date of recurrence (inclusive); null = single |
| `daysOfWeek` | — | Weekdays to repeat on; null/empty = derive from `startDate` |

**Validation rules** (400 if violated):
- Classroom must be active.
- A semester must be active for `startDate`.
- `repeatUntil` must not exceed `semester.endDate` (no silent truncation).
- No date may be a Sunday or in the past.

**Conflict detection** (409 if violated):
- Two bulk queries across all target dates (not one per date):
  classroom slot conflicts and user schedule conflicts.
- Returns `ApiResponse<ConflictDetailDTO>` with `{ date, timeSlotId }` identifying
  the first conflict for a human-readable frontend error message.
