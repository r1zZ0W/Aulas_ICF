-- =============================================================================
-- V1 — Initial schema
-- =============================================================================
-- Derived from docs/legacy/migration_v1.0__baseline.sql (a raw `mysqldump` of the
-- `test_aulas` development database), with the following changes applied to make it
-- a safe, portable, one-time migration instead of a database snapshot:
--
--   - Removed: `DROP TABLE IF EXISTS` (destroys data on re-run), `LOCK/UNLOCK TABLES`,
--     `SET FOREIGN_KEY_CHECKS=0` / `SET UNIQUE_CHECKS=0` (masked the true FK dependency
--     order instead of respecting it), `AUTO_INCREMENT=<n>` starting values inherited
--     from local dev data, the mysqldump header, and the `test_aulas`-specific comments.
--   - Reference data (`roles`, `time_slots` rows) moved to R__reference_data.sql —
--     schema and reference data are managed by two different Flyway mechanisms
--     (versioned vs. repeatable) with different lifecycles.
--   - Tables ordered by FK dependency instead of disabling the check:
--       roles -> semesters -> classrooms -> resources -> time_slots -> users
--       -> reservation_groups -> reserv_instances -> reservation_group_days
--       -> classroom_resources -> reserv_slots -> reservation_history
--   - Constraint names given stable, readable identifiers (uk_/fk_/idx_ prefixes)
--     instead of Hibernate's non-deterministic hash names (e.g. `FKa32h9xyoubo2bn07v1jqay37c`).
--     `ddl-auto=validate` never checks constraint names, so this rename is safe.
--   - `semesters.uuid` is `binary(16)` (the dump had `varbinary(255)`, a mapping bug —
--     Semester.java was the only UUID entity missing `columnDefinition = "BINARY(16)"`,
--     now fixed alongside this migration). The bytes stored were already 16, so this is
--     a type narrowing with no data loss.
--   - `reservation_group_days` gains an explicit composite PRIMARY KEY (group_id,
--     day_of_week); the dump had no PK at all on this @ElementCollection join table.
--     A `Set<DayOfWeek>` never produces duplicate rows, so this changes no behavior.
--   - `users.institutional_id` gains a UNIQUE constraint. The dump did not have one,
--     but User.java declares `@Column(..., unique = true)` — this migration corrects
--     the schema to match the entity contract it is supposed to satisfy.
--   - No `COLLATE` is pinned on any table: only `DEFAULT CHARSET=utf8mb4` is declared, so
--     each table inherits the collation of the target database. The dump used
--     `utf8mb4_0900_ai_ci`, exclusive to MySQL 8.0+; omitting it keeps this migration
--     portable to MySQL 5.7 and MariaDB, which the ICF's server may be running. Pin the
--     desired collation once, in the `CREATE DATABASE` statement (see README).
--   - `ROW_FORMAT=DYNAMIC` is declared explicitly on every table. It is already the
--     server default since MySQL 5.7.9 / MariaDB 10.2, so this has no effect where the
--     default holds — but `innodb_default_row_format` is server-configurable, and this
--     schema needs it: the UNIQUE indexes on `varchar(255)` name columns (roles,
--     classrooms, semesters) need up to 1020 bytes in utf8mb4, over the 767-byte limit
--     InnoDB enforces under `ROW_FORMAT=COMPACT`.
--
-- Safety: this migration is meant to run exactly once, against an empty database.
-- MySQL/MariaDB DDL is not transactional (CREATE TABLE issues an implicit commit), so a
-- failure partway through leaves the preceding tables created and Flyway's history marked
-- `failed`. See docs/legacy/README.md for the recovery procedure.
-- =============================================================================

CREATE TABLE roles (
  id         bigint       NOT NULL AUTO_INCREMENT,
  created_at datetime(6)  DEFAULT NULL,
  updated_at datetime(6)  DEFAULT NULL,
  description varchar(255) DEFAULT NULL,
  name       varchar(255) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_roles_name UNIQUE (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE semesters (
  id         bigint       NOT NULL AUTO_INCREMENT,
  created_at datetime(6)  DEFAULT NULL,
  updated_at datetime(6)  DEFAULT NULL,
  end_date   date         NOT NULL,
  name       varchar(255) NOT NULL,
  start_date date         NOT NULL,
  uuid       binary(16)   NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_semesters_name UNIQUE (name),
  CONSTRAINT uk_semesters_uuid UNIQUE (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE classrooms (
  id             bigint       NOT NULL AUTO_INCREMENT,
  created_at     datetime(6)  DEFAULT NULL,
  updated_at     datetime(6)  DEFAULT NULL,
  capacity       bigint       NOT NULL,
  is_active      tinyint(1)   NOT NULL DEFAULT '1',
  name           varchar(255) NOT NULL,
  uuid           binary(16)   NOT NULL,
  linked_room_id bigint       DEFAULT NULL,
  description    varchar(500) DEFAULT NULL,
  type           enum('AUDITORIO','AULA','LABORATORIO','SALA_SEMINARIOS') NOT NULL,
  room_image_url varchar(512) DEFAULT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_classrooms_name UNIQUE (name),
  CONSTRAINT uk_classrooms_uuid UNIQUE (uuid),
  KEY idx_classrooms_linked_room (linked_room_id),
  CONSTRAINT fk_classrooms_linked_room FOREIGN KEY (linked_room_id) REFERENCES classrooms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE resources (
  id          int          NOT NULL AUTO_INCREMENT,
  description varchar(255) DEFAULT NULL,
  name        varchar(50)  NOT NULL,
  uuid        binary(16)   NOT NULL,
  quantity    int          NOT NULL DEFAULT '1',
  PRIMARY KEY (id),
  CONSTRAINT uk_resources_name UNIQUE (name),
  CONSTRAINT uk_resources_uuid UNIQUE (uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE time_slots (
  id         int  NOT NULL,
  end_time   time NOT NULL,
  start_time time NOT NULL,
  PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE users (
  id               bigint       NOT NULL AUTO_INCREMENT,
  created_at       datetime(6)  DEFAULT NULL,
  updated_at       datetime(6)  DEFAULT NULL,
  email            varchar(150) NOT NULL,
  first_name       varchar(100) NOT NULL,
  last_names       varchar(100) NOT NULL,
  password_hash    varchar(255) NOT NULL,
  username         varchar(100) NOT NULL,
  uuid             binary(16)   NOT NULL,
  role_id          bigint       NOT NULL,
  institutional_id varchar(20)  NOT NULL,
  extension        varchar(20)  DEFAULT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_users_email UNIQUE (email),
  CONSTRAINT uk_users_username UNIQUE (username),
  CONSTRAINT uk_users_uuid UNIQUE (uuid),
  CONSTRAINT uk_users_institutional_id UNIQUE (institutional_id),
  KEY idx_users_role (role_id),
  CONSTRAINT fk_users_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE reservation_groups (
  id          bigint     NOT NULL AUTO_INCREMENT,
  created_at  datetime(6) DEFAULT NULL,
  updated_at  datetime(6) DEFAULT NULL,
  status      enum('ACTIVE','CANCELLED') DEFAULT NULL,
  uuid        binary(16) NOT NULL,
  semester_id bigint     NOT NULL,
  user_id     bigint     NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_reservation_groups_uuid UNIQUE (uuid),
  KEY idx_reservation_groups_semester (semester_id),
  KEY idx_reservation_groups_user (user_id),
  CONSTRAINT fk_reservation_groups_semester FOREIGN KEY (semester_id) REFERENCES semesters (id),
  CONSTRAINT fk_reservation_groups_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE reserv_instances (
  id             bigint      NOT NULL AUTO_INCREMENT,
  created_at     datetime(6) DEFAULT NULL,
  updated_at     datetime(6) DEFAULT NULL,
  date           date        NOT NULL,
  status         enum('CANCELLED_BY_ADMIN','CANCELLED_BY_USER','ACTIVE') DEFAULT NULL,
  uuid           binary(16)  NOT NULL,
  classroom_id   bigint      NOT NULL,
  group_id       bigint      NOT NULL,
  attendee_count int         NOT NULL,
  reassigned     bit(1)      NOT NULL,
  title          varchar(150) DEFAULT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_reserv_instances_uuid UNIQUE (uuid),
  KEY idx_reserv_instances_classroom (classroom_id),
  KEY idx_reserv_instances_group_status (group_id, status),
  KEY idx_reserv_instances_status_date_group (status, date, group_id),
  CONSTRAINT fk_reserv_instances_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms (id),
  CONSTRAINT fk_reserv_instances_group FOREIGN KEY (group_id) REFERENCES reservation_groups (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

-- Join table for ReservationGroup.daysOfWeek (@ElementCollection<DayOfWeek>).
-- PRIMARY KEY added (absent in the source dump): MySQL implicitly makes PK columns
-- NOT NULL, which is correct here since a Set<DayOfWeek> never holds a null element.
CREATE TABLE reservation_group_days (
  group_id    bigint NOT NULL,
  day_of_week enum('FRIDAY','MONDAY','SATURDAY','SUNDAY','THURSDAY','TUESDAY','WEDNESDAY') NOT NULL,
  PRIMARY KEY (group_id, day_of_week),
  CONSTRAINT fk_reservation_group_days_group FOREIGN KEY (group_id) REFERENCES reservation_groups (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

-- ON DELETE CASCADE on resource_id only (not classroom_id) is intentional — see
-- docs/legacy/migration_v1.5__resource_uuid_quantity.sql for the original rationale:
-- deleting a resource from the catalog must not orphan allocation rows, and
-- ResourceService.delete relies on this single cascade mechanism.
CREATE TABLE classroom_resources (
  quantity     int    NOT NULL,
  classroom_id bigint NOT NULL,
  resource_id  int    NOT NULL,
  PRIMARY KEY (classroom_id, resource_id),
  KEY idx_classroom_resources_resource (resource_id),
  CONSTRAINT fk_classroom_resources_resource FOREIGN KEY (resource_id) REFERENCES resources (id) ON DELETE CASCADE,
  CONSTRAINT fk_classroom_resources_classroom FOREIGN KEY (classroom_id) REFERENCES classrooms (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE reserv_slots (
  classroom_id bigint NOT NULL,
  date         date   NOT NULL,
  user_id      bigint NOT NULL,
  instance_id  bigint NOT NULL,
  time_slot_id int    NOT NULL,
  PRIMARY KEY (instance_id, time_slot_id),
  CONSTRAINT uk_reserv_slots_classroom_time UNIQUE (classroom_id, date, time_slot_id),
  CONSTRAINT uk_reserv_slots_user_time UNIQUE (user_id, date, time_slot_id),
  KEY idx_reserv_slots_time_slot (time_slot_id),
  CONSTRAINT fk_reserv_slots_instance FOREIGN KEY (instance_id) REFERENCES reserv_instances (id),
  CONSTRAINT fk_reserv_slots_time_slot FOREIGN KEY (time_slot_id) REFERENCES time_slots (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;

CREATE TABLE reservation_history (
  id                   bigint      NOT NULL AUTO_INCREMENT,
  created_at           datetime(6) DEFAULT NULL,
  updated_at           datetime(6) DEFAULT NULL,
  details              varchar(500) DEFAULT NULL,
  event_type           enum('CANCELLED_BY_ADMIN','CANCELLED_BY_USER','CREATED','REASSIGNED','UPDATED') NOT NULL,
  uuid                 binary(16)  NOT NULL,
  group_id             bigint      DEFAULT NULL,
  instance_id          bigint      DEFAULT NULL,
  performed_by_user_id bigint      DEFAULT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_reservation_history_uuid UNIQUE (uuid),
  KEY idx_reservation_history_group (group_id),
  KEY idx_reservation_history_instance (instance_id),
  KEY idx_reservation_history_performed_by (performed_by_user_id),
  CONSTRAINT fk_reservation_history_group FOREIGN KEY (group_id) REFERENCES reservation_groups (id),
  CONSTRAINT fk_reservation_history_instance FOREIGN KEY (instance_id) REFERENCES reserv_instances (id),
  CONSTRAINT fk_reservation_history_performed_by FOREIGN KEY (performed_by_user_id) REFERENCES users (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC;
