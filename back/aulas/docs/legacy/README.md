# Legacy SQL scripts — historical record, not a procedure

The scripts in this directory predate the Flyway migration in
`src/main/resources/db/migration/`. They are kept for historical reference only.

**Do not execute any of these scripts.** Their content is fully incorporated into
`V1__initial_schema.sql` (schema + constraints) and `R__reference_data.sql` (roles,
time slots). Running them against a database already managed by Flyway will conflict
with or duplicate what those migrations already applied.

## Why they can't be reused as-is

- `migration_v1.0__baseline.sql` is a raw `mysqldump` output, not a migration: it opens
  with `DROP TABLE IF EXISTS` on every table (a second run destroys the database),
  disables `FOREIGN_KEY_CHECKS`/`UNIQUE_CHECKS` instead of respecting table creation
  order, and carries `AUTO_INCREMENT` starting values and a `roles`/`time_slots` seed
  that belong to one specific local development database (`test_aulas`), not a fresh
  production install.
- `migration_v1.1` through `v1.6` and `reservations-refactor.sql` are incremental
  `ALTER`/`UPDATE` scripts written against that same baseline. Several of them
  reference status enum values in Spanish (`ACTIVA`, `CANCELADA_POR_MAESTRO`,
  `CANCELADA_POR_ADMIN`, `PENDIENTE`, `APROBADA`) that no longer exist anywhere in the
  codebase — `ReservInstanceStatus` today is `ACTIVE / CANCELLED_BY_USER /
  CANCELLED_BY_ADMIN`. Running these against the current schema would either fail
  outright or silently write values the application no longer understands.

## Where their content lives now

| Legacy script | Superseded by |
|---|---|
| `migration_v1.0__baseline.sql` (schema) | `V1__initial_schema.sql` |
| `migration_v1.0__baseline.sql` (roles, time_slots data) | `R__reference_data.sql` |
| `migration_v1.1`–`v1.6`, `reservations-refactor.sql` | Already folded into `V1__initial_schema.sql` |

If you need to trace how a particular column or index came to exist, these files are
the audit trail. For anything operational — provisioning a new environment, adding a
schema change — use Flyway migrations under `src/main/resources/db/migration/`.
