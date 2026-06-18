-- V5: Seed additional classrooms requested by product
-- Adds Aula V, Aula VI and Aula VII if they do not already exist

INSERT IGNORE INTO classrooms (uuid, name, capacity, type, description, is_active, created_at, updated_at)
VALUES
    (UNHEX(REPLACE(UUID(), '-', '')), 'Aula V', 40, 'AULA', 'Aula adicional agregada por seeding', TRUE, NOW(), NOW()),
    (UNHEX(REPLACE(UUID(), '-', '')), 'Aula VI', 30, 'AULA', 'Aula adicional agregada por seeding', TRUE, NOW(), NOW()),
    (UNHEX(REPLACE(UUID(), '-', '')), 'Aula VII', 25, 'AULA', 'Aula adicional agregada por seeding', TRUE, NOW(), NOW());

