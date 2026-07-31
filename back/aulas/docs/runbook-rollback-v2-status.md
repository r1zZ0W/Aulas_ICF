# Runbook: rollback de la migración V2 de status (`ACTIVE`/`ACTIVA`)

## Qué pasó

Un commit trajo, junto con cambios de UI ("modo oscuro"), una edición directa a
`V1__initial_schema.sql` (una migración Flyway **ya aplicada**) y una nueva migración
`V2__fix_reservation_status_columns.sql` que convertía las columnas `status` de
`reserv_instances` y `reservation_groups` de `ENUM` a `VARCHAR(50)`.

El motivo original era un error real:

```
JpaSystemException: could not execute statement [Data truncated for column 'status' at row 1]
```

Pero el esquema del repositorio ya era correcto — `ReservInstanceStatus` es
`ACTIVE / CANCELLED_BY_USER / CANCELLED_BY_ADMIN` y `V1__initial_schema.sql` ya declaraba
ese mismo ENUM en inglés. El *Data truncated* solo puede ocurrir contra una base de datos
que **no fue creada por Flyway**: una `test_aulas` local anterior al refactor de
`ReservInstanceStatus`, donde `status` seguía en español
(`ACTIVA`, `CANCELADA_POR_MAESTRO`, `CANCELADA_POR_ADMIN`) y posiblemente todavía tenía la
columna legacy `motivo` (ver `docs/legacy/README.md` — esos scripts en español nunca deben
ejecutarse contra el esquema actual).

Ambos archivos (V1 editado y V2) fueron revertidos: V1 volvió a su contenido original y V2
se eliminó del repo. Cualquiera que ya haya hecho `git pull` mientras estuvieron presentes
puede quedar en uno de los estados de la tabla siguiente.

## Regla para el futuro

**Una migración Flyway ya aplicada es inmutable.** Todo cambio de esquema va en un archivo
`V<n>__` nuevo, nunca editando `V1__initial_schema.sql` (ni ningún `V*` previamente
mergeado a `main`). Y un fix de base de datos no se agrupa dentro de un commit de un tema
no relacionado (UI, estilos, etc.) — así es imposible de revisar o revertir de forma aislada.

## Diagnóstico: identifica tu escenario

```sql
USE test_aulas;
SHOW COLUMNS FROM reserv_instances LIKE 'status';
SHOW COLUMNS FROM reserv_instances LIKE 'motivo';
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank;
```

| Escenario | Cómo se ve | Remedio |
|---|---|---|
| **A.** BD creada por Flyway con el V1 original, nunca corrió V2 | `flyway_schema_history` no tiene fila `version = '2'`; `status` ya es `enum(...)` | Nada. Solo `git pull`. |
| **B.** BD válida donde V2 sí llegó a aplicarse | existe la fila `version = '2'`; `SHOW COLUMNS` reporta `varchar(50)` | Reparación quirúrgica (abajo) — no se pierden datos |
| **C.** BD legacy en español y/o con columna `motivo` | `status` devuelve `ACTIVA`/`CANCELADA_POR_MAESTRO`/etc., o `motivo` existe | `DROP DATABASE` — es la única salida real |
| **D.** BD creada desde cero con el V1 ya editado | al arrancar el backend: `FlywayValidateException: Migration checksum mismatch for version 1` | `DROP DATABASE` (recrear es más barato que recalcular el checksum) |

> Este repo **no** tiene `flyway-maven-plugin` en `pom.xml` — solo las dependencias
> `spring-boot-flyway`, `flyway-core` y `flyway-mysql` — así que `./mvnw flyway:repair` no
> está disponible. Los remedios de abajo usan SQL directo o recrear la base.

## Escenario B — reparación quirúrgica (sin perder datos)

Revertir las columnas al ENUM que V1 declara, y luego borrar la fila fantasma del
historial de Flyway. **En ese orden** — si solo se borra la fila, el esquema real se queda
en VARCHAR y queda desalineado de lo que V1 declara (drift silencioso que paga la próxima
persona que compare contra V1).

```sql
ALTER TABLE reserv_instances
  MODIFY COLUMN status enum('CANCELLED_BY_ADMIN','CANCELLED_BY_USER','ACTIVE') DEFAULT NULL;
ALTER TABLE reservation_groups
  MODIFY COLUMN status enum('ACTIVE','CANCELLED') DEFAULT NULL;

DELETE FROM flyway_schema_history WHERE version = '2';
```

Después, `git pull` y arrancar el backend normalmente.

## Escenarios C y D — recrear la base de datos

```sql
DROP DATABASE test_aulas;
CREATE DATABASE test_aulas CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

Con perfil `dev` activo, al arrancar el backend Flyway aplica `V1__initial_schema.sql` +
`R__reference_data.sql` (roles y time slots), y el seeder crea automáticamente
`admin@icf.unam.mx` / `Admin@12345!` (ver `application-dev.properties`).

## Verificación tras aplicar cualquiera de los dos remedios

```bash
cd back/aulas && ./mvnw spring-boot:run
```

Debe arrancar sin `FlywayValidateException` y sin ninguna línea
`Migrating schema ... to version 2`.
