-- ============================================================
-- Migration v1.3 — Add (group_id, status) index to reserv_instances
-- ============================================================
-- Context
-- -------
-- The "Reportes y Estadísticas" recurrence check (ReportStatisticsRepository
-- .countRecurrentInstances) now uses an EXISTS subquery that looks up sibling
-- instances by (group_id, status) to determine whether a booking is
-- structurally recurring. JPA does not create an index for a @ManyToOne
-- foreign key automatically, so without this index the EXISTS check would
-- fall back to a full table scan per row as reserv_instances grows.
--
-- Execution order (mandatory)
-- ----------------------------
-- 1. Run STEP 1 (CREATE INDEX) to add the index.
-- 2. Start the application with ddl-auto=validate (or update in dev).
--
-- In dev (ddl-auto=update), Hibernate creates the index automatically on the
-- first restart after pulling this change (declared via @Table(indexes = ...)
-- on ReservInstance). Running STEP 1 manually is optional in dev, but run it
-- if you want to avoid a transient schema mismatch.
--
-- Verified table/column names:
--   table  : reserv_instances
--   columns: group_id, status
-- ============================================================

-- STEP 1 — Add the index (idempotent; safe to run multiple times)
CREATE INDEX IF NOT EXISTS idx_reserv_instances_group_status
  ON reserv_instances (group_id, status);
