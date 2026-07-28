-- ============================================================
-- Migration v1.5 — Resource catalog: public UUID + global quantity,
--                   cascade delete on classroom_resources.resource_id
-- ============================================================
-- Context
-- -------
-- The "Gestión de Recursos" admin screen exposes the equipment catalog
-- (`resources`) through the API. To keep the whole application UUID-only
-- (never leaking the internal numeric `id`), `resources` gains a public
-- `uuid` column mirroring the pattern already used by `classrooms`. It also
-- gains a `quantity` column: the total number of units of that equipment
-- type in the global catalog (independent from the per-classroom
-- `classroom_resources.quantity`, by design — no cross-validation).
--
-- Deleting a resource from the catalog must not leave orphan rows in
-- `classroom_resources`. This is enforced by a single mechanism —
-- `ON DELETE CASCADE` on the `resource_id` foreign key — so the
-- application layer (`ResourceService.delete`) can stay a plain
-- `repository.delete(resource)` with no manual cleanup query. Do NOT add a
-- second, application-level cascade (e.g. a `@Modifying` bulk delete) on
-- top of this: two cascade mechanisms racing on the same delete is
-- redundant and risks desynchronizing Hibernate's persistence context if
-- allocation rows are already loaded in the same transaction.
--
-- Portability note — verified against a real server
-- ---------------------------------------------------
-- Every ADD/DROP COLUMN below is guarded with an information_schema check
-- + PREPARE/EXECUTE instead of `ADD COLUMN IF NOT EXISTS` / `DROP COLUMN
-- IF EXISTS`. This project's earlier migrations (v1.1–v1.4) use the
-- `IF [NOT] EXISTS` column clause, but that syntax was verified NOT to work
-- against a real MySQL 8.4.9 server (`ERROR 1064 (42000)` — syntax error) —
-- so this migration does not rely on it. `ADD CONSTRAINT`/`ADD UNIQUE KEY`
-- have no `IF NOT EXISTS` form in MySQL either way, hence the same guarded
-- pattern is used uniformly for every structural change in this file.
--
-- UUID backfill safety
-- ---------------------
-- `resources` is a small pre-seeded catalog (a handful of rows), but the
-- backfill is still done in three separate, verifiable steps rather than a
-- single "add NOT NULL UNIQUE column with a default expression" statement:
-- some MySQL/MariaDB configurations only evaluate a volatile function like
-- UUID() ONCE for an entire batch UPDATE (rather than once per row), which
-- would silently assign the SAME uuid to every existing row and make the
-- UNIQUE constraint in step 3 fail. Verify uniqueness after step 2 before
-- running step 3; if the count check fails, backfill row-by-row instead
-- (e.g. a small script iterating `id`s and issuing one UPDATE per row).
--
-- Execution order (mandatory)
-- ----------------------------
-- 1. STEP 1 — add `uuid` as a nullable column.
-- 2. STEP 2 — backfill existing rows, then verify uniqueness (STEP 2b).
-- 3. STEP 3 — enforce NOT NULL + UNIQUE on `uuid`.
-- 4. STEP 4 — add the `quantity` column.
-- 5. STEP 5 — recreate the `classroom_resources.resource_id` FK with
--    ON DELETE CASCADE (looks up the current auto-generated constraint
--    name via information_schema — Hibernate-created FK names are not
--    predictable, so this must NOT be hardcoded).
--
-- Verified table/column names:
--   table : resources        (columns: id INT PK, name, description, uuid, quantity)
--   table : classroom_resources (columns: classroom_id, resource_id — composite PK)
-- ============================================================

-- STEP 1 — add the column, nullable (guarded; safe to run multiple times)
SET @uuid_col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resources' AND COLUMN_NAME = 'uuid'
);
SET @add_uuid_sql = IF(@uuid_col_exists = 0,
    'ALTER TABLE resources ADD COLUMN uuid BINARY(16) NULL',
    'SELECT 1');
PREPARE stmt FROM @add_uuid_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- STEP 2 — backfill existing rows (only touches rows still missing a uuid,
-- so re-running this script is a no-op once every row has one)
UPDATE resources
SET    uuid = UNHEX(REPLACE(UUID(), '-', ''))
WHERE  uuid IS NULL;

-- STEP 2b — verification query (run manually, do NOT proceed to STEP 3
-- unless this returns 0): duplicate uuids would make the UNIQUE constraint
-- below fail, or worse, silently collapse distinct resources.
-- SELECT COUNT(*) - COUNT(DISTINCT uuid) AS duplicate_uuids FROM resources;

-- STEP 3 — enforce NOT NULL (run only after STEP 2b returns 0).
-- MODIFY re-declaring the same definition is a harmless no-op in MySQL, so
-- this is safe to run unconditionally on every re-run.
ALTER TABLE resources
  MODIFY uuid BINARY(16) NOT NULL;

-- Add the UNIQUE key only if it doesn't already exist (MySQL has no
-- `ADD CONSTRAINT IF NOT EXISTS`, so this is guarded procedurally).
SET @uk_exists = (
    SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'resources'
      AND INDEX_NAME = 'uk_resources_uuid'
);
SET @uk_sql = IF(@uk_exists = 0,
    'ALTER TABLE resources ADD UNIQUE KEY uk_resources_uuid (uuid)',
    'SELECT 1');
PREPARE stmt FROM @uk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- STEP 4 — add the quantity column (guarded; safe to run multiple times)
SET @qty_col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'resources' AND COLUMN_NAME = 'quantity'
);
SET @add_qty_sql = IF(@qty_col_exists = 0,
    'ALTER TABLE resources ADD COLUMN quantity INT NOT NULL DEFAULT 1',
    'SELECT 1');
PREPARE stmt FROM @add_qty_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- STEP 5 — recreate classroom_resources.resource_id FK with ON DELETE CASCADE.
-- Hibernate auto-generates FK names (e.g. FKxxxxxxxxxxxxxxxxxxxxxxxxx), so the
-- current name is looked up dynamically instead of being hardcoded, and its
-- DELETE_RULE is checked so the drop+recreate only happens once — re-running
-- this script after the FK is already CASCADE must be a no-op (see NOTE below
-- for why the check has to happen BEFORE dropping, not after).
SET @fk_name = (
    SELECT CONSTRAINT_NAME FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'classroom_resources'
      AND COLUMN_NAME = 'resource_id'
      AND REFERENCED_TABLE_NAME = 'resources'
    LIMIT 1
);

SET @already_cascade = (
    SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE()
      AND TABLE_NAME = 'classroom_resources'
      AND CONSTRAINT_NAME = @fk_name
      AND DELETE_RULE = 'CASCADE'
);

-- Drop the existing FK (whatever its auto-generated name is) — but only when
-- it isn't already CASCADE. NOTE: @already_cascade is computed once, before
-- either statement runs, precisely so the "add" step below still fires when
-- "drop" fires — evaluating it fresh after the drop would find no FK at all
-- and skip re-adding it, leaving resource_id with no foreign key.
SET @drop_sql = IF(@fk_name IS NOT NULL AND @already_cascade = 0,
    CONCAT('ALTER TABLE classroom_resources DROP FOREIGN KEY ', @fk_name),
    'SELECT 1');
PREPARE stmt FROM @drop_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- Recreate it under a stable, known name with ON DELETE CASCADE.
SET @add_sql = IF(@fk_name IS NOT NULL AND @already_cascade = 0,
    'ALTER TABLE classroom_resources
       ADD CONSTRAINT fk_classroom_resources_resource
       FOREIGN KEY (resource_id) REFERENCES resources(id)
       ON DELETE CASCADE',
    'SELECT 1');
PREPARE stmt FROM @add_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
