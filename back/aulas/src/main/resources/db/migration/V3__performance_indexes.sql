-- V3: Add composite indexes to support sub-2s query performance at 50 concurrent users (NFR).
-- These are non-unique (multiple PENDIENTE rows can share the same date/classroom/user);
-- the APROBADA uniqueness constraint is enforced at application level.

-- Reservation instances: speed up date-range and classroom-based queries (reports + availability calendar).
CREATE INDEX idx_reserv_instances_date_status
    ON reserv_instances (date, status);

CREATE INDEX idx_reserv_instances_classroom_date
    ON reserv_instances (classroom_id, date);

-- Reservation slots: speed up the conflict-detection query
-- (filters by classroom_id + date + time_slot_id with instance status join).
CREATE INDEX idx_reserv_slots_classroom_date_slot
    ON reserv_slots (classroom_id, date, time_slot_id);

CREATE INDEX idx_reserv_slots_user_date_slot
    ON reserv_slots (user_id, date, time_slot_id);
