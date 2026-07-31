-- =============================================================================
-- Migration V2: Fix status and legacy columns in reservation tables
-- =============================================================================
-- Solves "Data truncated for column 'status'" and "Field 'motivo' doesn't have a default value"
-- errors caused by legacy database schema discrepancies.

-- 1. Normalize any legacy Spanish status values in reserv_instances
UPDATE reserv_instances SET status = 'ACTIVE' WHERE status = 'ACTIVA';
UPDATE reserv_instances SET status = 'CANCELLED_BY_USER' WHERE status = 'CANCELADA_POR_MAESTRO';
UPDATE reserv_instances SET status = 'CANCELLED_BY_ADMIN' WHERE status = 'CANCELADA_POR_ADMIN';

-- 2. Normalize any legacy Spanish status values in reservation_groups
UPDATE reservation_groups SET status = 'ACTIVE' WHERE status = 'ACTIVA';
UPDATE reservation_groups SET status = 'CANCELLED' WHERE status = 'CANCELADA';

-- 3. Change status columns from narrow ENUM to VARCHAR(50) for robust inserts
ALTER TABLE reserv_instances MODIFY COLUMN status VARCHAR(50) DEFAULT 'ACTIVE';
ALTER TABLE reservation_groups MODIFY COLUMN status VARCHAR(50) DEFAULT 'ACTIVE';

-- 4. Make legacy 'motivo' column NULLABLE if present from older schemas
-- This prevents "Field 'motivo' doesn't have a default value" errors when inserting new instances.
DROP PROCEDURE IF EXISTS _fix_legacy_motivo;

CREATE PROCEDURE _fix_legacy_motivo()
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM INFORMATION_SCHEMA.COLUMNS 
        WHERE TABLE_SCHEMA = DATABASE() 
          AND TABLE_NAME = 'reserv_instances' 
          AND COLUMN_NAME = 'motivo'
    ) THEN
        ALTER TABLE reserv_instances MODIFY COLUMN motivo VARCHAR(255) NULL DEFAULT NULL;
    END IF;
END;

CALL _fix_legacy_motivo();

DROP PROCEDURE IF EXISTS _fix_legacy_motivo;
