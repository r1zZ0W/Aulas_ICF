-- =============================================================================
-- R__reference_data — Application-required catalogs
-- =============================================================================
-- Flyway repeatable migration (runs on every checksum change, not just once). Owns
-- ONLY the two catalogs the application cannot function without: `roles` and
-- `time_slots`. Both are referenced by FK from live data (`users.role_id`,
-- `reserv_slots.time_slot_id`), so this script follows one hard rule:
--
--   It may only INSERT and UPDATE the properties of rows it controls. It must
--   NEVER DELETE or TRUNCATE. Removing a catalog row that historical data still
--   references is a deliberate, versioned V<n> migration — never a side effect of
--   this file changing.
--
-- Business data — classrooms, resources, semesters, users — does NOT belong here.
-- Those are managed by the application itself and change legitimately at runtime;
-- a repeatable migration re-asserting their state would fight the application for
-- ownership of its own data.
--
-- Role name note: the canonical non-admin role literal is `TEACHER` (English,
-- consistent with the rest of the domain's identifiers — see ReservInstanceStatus,
-- ReservationGroupStatus, etc.). UserService.save() looks it up by this exact name
-- when no roleId is supplied at registration.
-- =============================================================================

INSERT INTO roles (name, description, created_at, updated_at) VALUES
    ('ADMIN',   'System administrator with full access',        NOW(), NOW()),
    ('TEACHER', 'Teacher with academic management permissions', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    description = VALUES(description),
    updated_at  = NOW();

INSERT INTO time_slots (id, start_time, end_time) VALUES
    (1,  '07:00:00', '07:30:00'),
    (2,  '07:30:00', '08:00:00'),
    (3,  '08:00:00', '08:30:00'),
    (4,  '08:30:00', '09:00:00'),
    (5,  '09:00:00', '09:30:00'),
    (6,  '09:30:00', '10:00:00'),
    (7,  '10:00:00', '10:30:00'),
    (8,  '10:30:00', '11:00:00'),
    (9,  '11:00:00', '11:30:00'),
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
    (24, '18:30:00', '19:00:00')
ON DUPLICATE KEY UPDATE
    start_time = VALUES(start_time),
    end_time   = VALUES(end_time);

INSERT INTO classrooms (uuid, name, capacity, type, description, linked_room_id, is_active)
SELECT UUID_TO_BIN(UUID()), 'Aula Multimodal', 60, 'AULA', 'Espacio modular divisible en Aula I y II', NULL, 1
    WHERE NOT EXISTS (SELECT 1 FROM classrooms WHERE name = 'Aula Multimodal');

INSERT INTO classrooms (uuid, name, capacity, type, description, linked_room_id, is_active)
SELECT UUID_TO_BIN(UUID()), 'Aula III', 35, 'AULA', 'Aula para clases teóricas', NULL, 1
    WHERE NOT EXISTS (SELECT 1 FROM classrooms WHERE name = 'Aula III');

INSERT INTO classrooms (uuid, name, capacity, type, description, linked_room_id, is_active)
SELECT UUID_TO_BIN(UUID()), 'Aula IV', 35, 'AULA', 'Aula para clases teóricas', NULL, 1
    WHERE NOT EXISTS (SELECT 1 FROM classrooms WHERE name = 'Aula IV');

INSERT INTO classrooms (uuid, name, capacity, type, description, linked_room_id, is_active)
SELECT UUID_TO_BIN(UUID()), 'Aula V', 40, 'AULA', 'Aula mediana equipada con proyector', NULL, 1
    WHERE NOT EXISTS (SELECT 1 FROM classrooms WHERE name = 'Aula V');

INSERT INTO classrooms (uuid, name, capacity, type, description, linked_room_id, is_active)
SELECT UUID_TO_BIN(UUID()), 'Aula VI', 40, 'AULA', 'Aula mediana equipada con proyector', NULL, 1
    WHERE NOT EXISTS (SELECT 1 FROM classrooms WHERE name = 'Aula VI');

INSERT INTO classrooms (uuid, name, capacity, type, description, linked_room_id, is_active)
SELECT UUID_TO_BIN(UUID()), 'Auditorio Principal', 200, 'AUDITORIO', 'Auditorio para eventos y conferencias', NULL, 1
    WHERE NOT EXISTS (SELECT 1 FROM classrooms WHERE name = 'Auditorio Principal');

SET @multimodal_id = (SELECT id FROM classrooms WHERE name = 'Aula Multimodal' LIMIT 1);

INSERT INTO classrooms (uuid, name, capacity, type, description, linked_room_id, is_active)
SELECT UUID_TO_BIN(UUID()), 'Aula I', 30, 'AULA', 'Sub-espacio A de Aula Multimodal', @multimodal_id, 1
    WHERE NOT EXISTS (SELECT 1 FROM classrooms WHERE name = 'Aula I');

INSERT INTO classrooms (uuid, name, capacity, type, description, linked_room_id, is_active)
SELECT UUID_TO_BIN(UUID()), 'Aula II', 30, 'AULA', 'Sub-espacio B de Aula Multimodal', @multimodal_id, 1
    WHERE NOT EXISTS (SELECT 1 FROM classrooms WHERE name = 'Aula II');