-- V4: Seed catalog data required for the system to be operational out of the box.
-- Uses INSERT IGNORE so this migration is safe to re-run (idempotent).
-- Roles and time slots are static reference data; they are never modified at runtime.

-- ---- Roles ----------------------------------------------------------------
INSERT IGNORE INTO roles (uuid, name, description, created_at, updated_at)
VALUES
    (UNHEX(REPLACE(UUID(), '-', '')), 'ADMIN',   'System administrator with full access',            NOW(), NOW()),
    (UNHEX(REPLACE(UUID(), '-', '')), 'MAESTRO', 'Teacher with access to create and manage their own reservations', NOW(), NOW());

-- ---- Time slots (07:00 – 20:00, 30-minute blocks, IDs 1–26) ---------------
INSERT IGNORE INTO time_slots (id, start_time, end_time) VALUES
    ( 1, '07:00:00', '07:30:00'),
    ( 2, '07:30:00', '08:00:00'),
    ( 3, '08:00:00', '08:30:00'),
    ( 4, '08:30:00', '09:00:00'),
    ( 5, '09:00:00', '09:30:00'),
    ( 6, '09:30:00', '10:00:00'),
    ( 7, '10:00:00', '10:30:00'),
    ( 8, '10:30:00', '11:00:00'),
    ( 9, '11:00:00', '11:30:00'),
    (10, '11:30:00', '12:00:00'),
    (11, '12:00:00', '12:30:00'),
    (12, '12:30:00', '13:00:00'),
    (13, '13:00:00', '13:30:00'),
    (14, '13:30:00', '14:00:00'),
    (15, '14:00:00', '14:30:00'),
    (16, '14:30:00', '15:00:00'),
    (17, '15:00:00', '15:30:00'),
    (18, '15:30:00', '16:00:00'),
    (19, '16:00:00', '16:30:00'),
    (20, '16:30:00', '17:00:00'),
    (21, '17:00:00', '17:30:00'),
    (22, '17:30:00', '18:00:00'),
    (23, '18:00:00', '18:30:00'),
    (24, '18:30:00', '19:00:00'),
    (25, '19:00:00', '19:30:00'),
    (26, '19:30:00', '20:00:00');
