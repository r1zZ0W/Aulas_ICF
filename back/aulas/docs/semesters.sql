-- Semester module: drop the physical is_active column.
--
-- Semester activity is now a DERIVED state computed from [start_date, end_date]
-- against a reference date in the service/mapper (see SemesterMapper.isActiveOn /
-- SemesterRepository.findCurrent). The column is no longer mapped by the Java entity,
-- so Hibernate ignores it safely after the new code is deployed.
--
-- ⚠️  WHEN TO RUN:
--     Only after the new backend code (which no longer maps is_active) is fully deployed.
--     Hibernate validate-mode tolerates the still-present unused column on the new code,
--     so it is safe to deploy the code first and run this script afterward.
--     Running it while old replicas (that still map is_active) are alive will break those
--     replicas during a rolling update.

ALTER TABLE semesters
    DROP COLUMN is_active;
