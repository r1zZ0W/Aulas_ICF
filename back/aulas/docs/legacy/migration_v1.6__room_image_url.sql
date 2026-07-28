-- ============================================================
-- Migration v1.6 — Widen / ensure classrooms.room_image_url
-- ============================================================
-- Context
-- -------
-- The classroom detail view now renders an image from `roomImageUrl`
-- (an external link the admin pastes in — no file upload, no storage on
-- this system). The column is sized VARCHAR(512), not VARCHAR(150):
-- signed URLs from S3/Firebase Storage/Cloudinary routinely exceed 150
-- characters once access tokens and hashed paths are included, and a
-- narrower column would silently truncate (or throw a data-truncation
-- error on write) for otherwise legitimate URLs. VARCHAR(512) costs
-- nothing extra at read/write time in MySQL/InnoDB for a nullable
-- variable-length column.
--
-- This script both creates the column (for environments where it never
-- existed) and widens it (for environments where an earlier, narrower
-- version of it was already created via ddl-auto=update in dev).
--
-- Portability note: the ADD COLUMN is guarded via information_schema +
-- PREPARE/EXECUTE instead of `ADD COLUMN IF NOT EXISTS` — that clause was
-- verified NOT to work against a real MySQL 8.4.9 server (`ERROR 1064`).
-- See migration_v1.5 for the same pattern and the full explanation.
--
-- Verified column/table names:
--   table : classrooms
--   column: room_image_url VARCHAR(512) NULL
-- ============================================================

-- STEP 1 — create the column if it doesn't exist yet (guarded)
SET @img_col_exists = (
    SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'classrooms' AND COLUMN_NAME = 'room_image_url'
);
SET @add_img_sql = IF(@img_col_exists = 0,
    'ALTER TABLE classrooms ADD COLUMN room_image_url VARCHAR(512) NULL',
    'SELECT 1');
PREPARE stmt FROM @add_img_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- STEP 2 — widen it in case it was already created narrower (e.g. 150).
-- MODIFY re-declaring the same definition is a harmless no-op in MySQL, so
-- this is safe to run unconditionally on every re-run.
ALTER TABLE classrooms
  MODIFY room_image_url VARCHAR(512) NULL;
