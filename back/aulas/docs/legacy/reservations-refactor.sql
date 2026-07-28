-- =============================================================================
-- Reservations Refactor — Data Migration Script
-- =============================================================================
-- Purpose : Migrate an existing database to match the post-refactor schema:
--             • Remove the approval workflow (PENDIENTE / APROBADA / RECHAZADA).
--             • Normalize reservation status to ACTIVA / CANCELADA_POR_MAESTRO /
--               CANCELADA_POR_ADMIN.
--             • Free slots held by cancelled/rejected reservations.
--             • Replace non-unique slot indexes with UNIQUE constraints.
--             • Normalize classrooms.is_active to NOT NULL DEFAULT 1.
--
-- Prerequisites : The database must already contain the schema produced by the
--                 V1–V5 migrations (tables, base columns, old non-unique indexes).
--                 Run this script once, in order, on every environment before
--                 deploying the updated application JAR.
--
-- Safe to run in a transaction; wrap in BEGIN / COMMIT if your client supports it.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- STEP 1 — Status data migration
-- -----------------------------------------------------------------------------
-- Promote PENDIENTE and APROBADA rows to ACTIVA (immediately active, no queue).
UPDATE reserv_instances
SET    status = 'ACTIVA'
WHERE  status IN ('PENDIENTE', 'APROBADA');

-- Free all slots held by already-cancelled reservations.
-- Slots of CANCELADA_* rows block re-booking because the unique constraints
-- added in Step 3 are unconditional.
DELETE FROM reserv_slots
WHERE instance_id IN (
    SELECT id FROM reserv_instances
    WHERE  status IN ('CANCELADA_POR_MAESTRO', 'CANCELADA_POR_ADMIN')
);

-- Free slots of rejected reservations before converting their status.
DELETE FROM reserv_slots
WHERE instance_id IN (
    SELECT id FROM reserv_instances
    WHERE  status = 'RECHAZADA'
);

-- Map RECHAZADA to CANCELADA_POR_ADMIN (administrative decision, closest semantic).
UPDATE reserv_instances
SET    status = 'CANCELADA_POR_ADMIN'
WHERE  status = 'RECHAZADA';

-- -----------------------------------------------------------------------------
-- STEP 2 — Instance-level de-duplication (collision resolution)
-- -----------------------------------------------------------------------------
-- Today's indexes are non-unique, so duplicate slot keys may exist.  Blindly
-- removing duplicate SLOT rows would orphan the parent INSTANCE (leaving an
-- ACTIVA reservation with no time slots — a logically corrupt state).
--
-- Strategy: identify "loser" instances — any instance that is NOT the lowest
-- instance_id for a shared (classroom_id, date, time_slot_id) key OR a shared
-- (user_id, date, time_slot_id) key.  Cancel losers cleanly (zero slots, proper
-- status) so that after all losers are resolved the remaining slot keys are unique
-- by construction, and Step 3's CREATE UNIQUE INDEX cannot fail.

-- 2a. Classroom-slot losers — not the minimum instance_id per classroom/date/slot.
CREATE TEMPORARY TABLE IF NOT EXISTS _loser_instances AS
SELECT DISTINCT rs.instance_id
FROM   reserv_slots rs
WHERE  rs.instance_id > (
    SELECT MIN(rs2.instance_id)
    FROM   reserv_slots rs2
    WHERE  rs2.classroom_id  = rs.classroom_id
    AND    rs2.date          = rs.date
    AND    rs2.time_slot_id  = rs.time_slot_id
);

-- 2b. User-slot losers — not the minimum instance_id per user/date/slot.
INSERT IGNORE INTO _loser_instances
SELECT DISTINCT rs.instance_id
FROM   reserv_slots rs
WHERE  rs.instance_id > (
    SELECT MIN(rs2.instance_id)
    FROM   reserv_slots rs2
    WHERE  rs2.user_id      = rs.user_id
    AND    rs2.date         = rs.date
    AND    rs2.time_slot_id = rs.time_slot_id
);

-- 2c. Remove all slots belonging to loser instances.
DELETE FROM reserv_slots
WHERE instance_id IN (SELECT instance_id FROM _loser_instances);

-- 2d. Cancel loser instances cleanly (zero slots, cancelled by admin).
UPDATE reserv_instances
SET    status = 'CANCELADA_POR_ADMIN'
WHERE  id IN (SELECT instance_id FROM _loser_instances)
AND    status = 'ACTIVA';

DROP TEMPORARY TABLE IF EXISTS _loser_instances;

-- -----------------------------------------------------------------------------
-- STEP 3 — Replace non-unique slot indexes with UNIQUE constraints
-- -----------------------------------------------------------------------------
-- The old indexes were explicitly non-unique (see V3 comment: "the APROBADA
-- uniqueness constraint is enforced at application level").  After this step, the
-- DB itself enforces the invariants and the application conflict checks become a
-- user-friendly layer on top of a hard DB guarantee.

DROP INDEX IF EXISTS idx_reserv_slots_classroom_date_slot ON reserv_slots;
CREATE UNIQUE INDEX uk_reserv_slots_classroom_time
    ON reserv_slots (classroom_id, date, time_slot_id);

DROP INDEX IF EXISTS idx_reserv_slots_user_date_slot ON reserv_slots;
CREATE UNIQUE INDEX uk_reserv_slots_user_time
    ON reserv_slots (user_id, date, time_slot_id);

-- Informational: instance indexes are not changed (still useful for date-range scans).
-- idx_reserv_instances_date_status, idx_reserv_instances_classroom_date stay as-is.

-- -----------------------------------------------------------------------------
-- STEP 4 — Normalize classrooms.is_active (eliminate NULL ambiguity)
-- -----------------------------------------------------------------------------
-- The service now interprets NULL as inactive (!Boolean.TRUE.equals(isActive)).
-- toggleStatus flips NULL → true, which is fine, but leaving nulls in the column
-- means the column has three effective values (true / false / null≡false).
-- Make the default explicit and add a NOT NULL constraint so the column has exactly
-- two values and Hibernate validate agrees with the schema.

UPDATE classrooms
SET    is_active = 1
WHERE  is_active IS NULL;

ALTER TABLE classrooms
    MODIFY COLUMN is_active TINYINT(1) NOT NULL DEFAULT 1;

-- =============================================================================
-- Post-run verification queries (run manually to confirm success)
-- =============================================================================
-- SELECT status, COUNT(*) FROM reserv_instances GROUP BY status;
--   Expected: only ACTIVA, CANCELADA_POR_MAESTRO, CANCELADA_POR_ADMIN.
--
-- SELECT COUNT(*) FROM reserv_slots rs
-- JOIN reserv_instances ri ON ri.id = rs.instance_id
-- WHERE ri.status IN ('CANCELADA_POR_MAESTRO','CANCELADA_POR_ADMIN');
--   Expected: 0  (cancelled reservations hold no slots).
--
-- SHOW INDEX FROM reserv_slots WHERE Key_name IN
--   ('uk_reserv_slots_classroom_time','uk_reserv_slots_user_time');
--   Expected: 2 rows, Non_unique = 0.
--
-- SELECT COUNT(*) FROM classrooms WHERE is_active IS NULL;
--   Expected: 0.
-- =============================================================================
