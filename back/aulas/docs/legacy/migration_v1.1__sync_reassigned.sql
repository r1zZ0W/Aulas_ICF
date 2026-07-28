-- ============================================================
-- Migration v1.1 — Sync reassigned flag from audit log
-- ============================================================
-- Context
-- -------
-- v1.1 adds a denormalized `reassigned` boolean column to
-- `reserv_instances` so the frontend can display the contextual
-- "Reasignada" badge without an N+1 join against `reservation_history`.
-- Script location: back/aulas/docs/migration_v1.1__sync_reassigned.sql
--
-- When ddl-auto=update (dev) Hibernate creates the column automatically.
-- In production (ddl-auto=validate) run this script manually BEFORE
-- starting the application. Missing the backfill is a silent data bug:
-- every past reassignment would appear as "Activa" in the history view.
--
-- Execution order (mandatory)
-- ----------------------------
-- 1. Run STEP 1 (ALTER TABLE) to add the column with default false.
-- 2. Run STEP 2 (UPDATE backfill) to derive truth from the audit log.
-- 3. Start the application with ddl-auto=validate.
--
-- In dev, run the same two steps once after the first restart so that
-- any test data with REASSIGNED events reflects the correct badge.
--
-- Verified column/table names (do NOT substitute placeholders):
--   audit table  : reservation_history
--   event column : event_type  (VARCHAR, stored value = 'REASSIGNED')
--   FK column    : instance_id (numeric, references reserv_instances.id)
-- ============================================================

-- STEP 1 — Add the column (idempotent; safe to run multiple times)
ALTER TABLE reserv_instances
  ADD COLUMN IF NOT EXISTS reassigned BOOLEAN NOT NULL DEFAULT FALSE;

-- STEP 2 — Backfill from audit log (join on numeric id, not UUID)
UPDATE reserv_instances ri
SET    ri.reassigned = TRUE
WHERE  ri.id IN (
    SELECT DISTINCT rh.instance_id
    FROM   reservation_history rh
    WHERE  rh.event_type = 'REASSIGNED'
      AND  rh.instance_id IS NOT NULL
);
