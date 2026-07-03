-- ============================================================
-- Migration v1.2 — Add optional title column to reserv_instances
-- ============================================================
-- Context
-- -------
-- v1.2 exposes a free-text `title` field on reservation instances so teachers
-- can label a booking (e.g. "Programación I — parcial"). The field is optional
-- and nullable; existing rows remain with title = NULL, and the frontend falls
-- back to classroomName when title is absent.
--
-- Normalization invariant: the application layer (ReservInstance.setTitle)
-- ensures blank/empty values are stored as NULL — never as an empty string.
--
-- Execution order (mandatory)
-- ----------------------------
-- 1. Run STEP 1 (ALTER TABLE) to add the column.
-- 2. Start the application with ddl-auto=validate (or update in dev).
--
-- In dev (ddl-auto=update), Hibernate adds the column automatically on the
-- first restart after pulling this change. Running STEP 1 manually is optional
-- in dev, but run it if you want to avoid a transient schema mismatch.
--
-- Verified column/table names:
--   table : reserv_instances
--   column: title VARCHAR(150) NULL
-- ============================================================

-- STEP 1 — Add the column (idempotent; safe to run multiple times)
ALTER TABLE reserv_instances
  ADD COLUMN IF NOT EXISTS title VARCHAR(150) NULL;
