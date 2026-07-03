# Brechas — Módulo Historial de Reservas

Detectadas durante el diseño e implementación del módulo "Historial de Reservas"
(frontend) contra los diseños Figma `node 92:1501` (Maestro) y `node 235:44` (Admin).

Las brechas están ordenadas por prioridad de impacto en el producto.

---

## Brecha 1 (PRIORIDAD #1) — Falta de filtros server-side en los endpoints de listado

**Descripción**
Los endpoints `GET /api/v1/reservations` y `GET /api/v1/reservations/user/{userUuid}`
no aceptan parámetros de filtro. Solo exponen paginación y ordenamiento
(`?page=`, `?size=`, `?sort=`, `?direction=`).

**Por qué bloquea**
Un historial de reservas sin buscador ni filtros se vuelve inservible en producción
al cabo de pocas semanas de uso intensivo: un administrador tendría que navegar
decenas de páginas para localizar una reserva específica. El diseño Figma muestra
explícitamente controles de búsqueda y filtros de estado/aula/fecha.

**Mitigación temporal en el frontend**
Se implementó un filtro *en memoria sobre la página actual* (no server-side) con
un aviso visible al usuario sobre su alcance limitado. Los controles de filtro del
diseño están omitidos con comentarios `TODO` que apuntan a esta brecha.

**Solución requerida (próximo sprint)**
Implementar `org.springframework.data.jpa.domain.Specification` sobre
`ReservInstanceRepository` extendiendo `JpaSpecificationExecutor<ReservInstance>`.

Parámetros de query a soportar:

| Parámetro       | Tipo     | Filtro aplicado                                     |
|----------------|----------|-----------------------------------------------------|
| `?search=`     | String   | LIKE sobre `classroom.name` y `group.user.fullName` |
| `?status=`     | String   | Enum `ACTIVE \| CANCELLED_BY_USER \| CANCELLED_BY_ADMIN` |
| `?classroomId=`| UUID     | Igualdad sobre `classroom.uuid`                     |
| `?from=`       | LocalDate| `date >= from`                                      |
| `?to=`         | LocalDate| `date <= to`                                        |

**Archivos afectados**
- `ReservInstanceController.java` — nuevos `@RequestParam` opcionales
- `ReservInstanceService.java` — delegar a Specification
- `ReservInstanceRepository.java` — extender `JpaSpecificationExecutor`
- (nuevo) `ReservInstanceSpecification.java` — predicados JPA

---

## Brecha 2 — `reservations/history` no puede alimentar el listado

**Descripción**
El módulo `modules/reservations/history` es un log de auditoría de eventos
(`CREATED`, `UPDATED`, `CANCELLED_BY_USER`, `CANCELLED_BY_ADMIN`, `REASSIGNED`).
Su `ReservationHistoryResponseDTO` no contiene campos de aula, fecha, horario,
ni nombre de usuario, por lo que **no puede** alimentar la tabla de historial del
diseño Figma.

**Por qué bloquea**
Si se usa el endpoint del módulo `history` como fuente de datos, la tabla quedaría
vacía de información esencial (aula, fecha, hora) y requeriría un join N+1 extra
por cada fila para obtenerla.

**Solución implementada**
El módulo Historial de Reservas usa los endpoints de **instances**:
- Admin "Todas": `GET /api/v1/reservations` (findAll paginado)
- Maestro y admin "Mis Reservas": `GET /api/v1/reservations/user/{userUuid}`

El módulo `reservations/history` sigue siendo la fuente de verdad para el backfill
de la bandera `reassigned` (ver Brecha 3 y `migration_v1.1__sync_reassigned.sql`).

**Archivos afectados**
- No requiere cambios en el backend; el frontend consume los endpoints correctos.

---

## Brecha 3 — Estado "Reasignada" inexistente como estado de instancia

**Descripción**
`ReservInstanceStatus` solo tiene tres valores: `ACTIVE`, `CANCELLED_BY_USER`,
`CANCELLED_BY_ADMIN`. Tras una reasignación el estado de la instancia permanece
`ACTIVE`; "Reasignada" solo existe como evento de auditoría `REASSIGNED` en
`reservation_history`.

**Por qué dificulta**
El diseño Figma exige mostrar el badge "Reasignada" en el historial. Sin esta
información expuesta en el DTO, el frontend no puede distinguir una reserva activa
de una activa-y-reasignada.

**Solución implementada**
Se añadió la bandera denormalizada `reassigned BOOLEAN NOT NULL DEFAULT FALSE` a
`reserv_instances`. `ReservInstanceService.reassign()` la establece a `true`.
El DTO `ReservInstanceResponseDTO` la expone como `Boolean reassigned`.

**Migración obligatoria en producción**
Antes de activar `ddl-auto=validate`, ejecutar el script
`back/aulas/docs/migration_v1.1__sync_reassigned.sql` en orden:
1. `ALTER TABLE` — añade la columna.
2. `UPDATE` de backfill — deriva el valor desde `reservation_history`.
3. Arrancar la aplicación.

Sin el backfill, las reasignaciones pasadas aparecerán como "Activa" en el historial.

**Archivos afectados**
- `ReservInstance.java` — campo `reassigned`
- `ReservInstanceService.java` — `instance.setReassigned(true)` en `reassign()`
- `ReservInstanceResponseDTO.java` — parámetro `Boolean reassigned`
- `ReservInstanceMapper.java` — mapeo por nombre (verificar compilación)
- `back/doc/migration_v1.1__sync_reassigned.sql` — script de despliegue

---

## Brecha 4 — Sin título de reserva ni ID amigable en el DTO

**Descripción**
`ReservInstanceResponseDTO` no tiene campo `title` (nombre de la clase/evento)
ni un identificador legible tipo `RE-0042` para mostrar en la UI.

**Por qué dificulta**
El diseño Figma muestra una columna "Detalle de Reserva" que en el prototipo
usa un nombre de clase. Sin dicho campo, el frontend recurre al `classroomName`
como texto principal y a un extracto del `uuid` como subtexto visual.

**Solución temporal en el frontend**
- Texto principal: `classroomName` (aula asignada).
- Subtexto: `getShortId(uuid)` → `RE-` + primeros 8 caracteres del UUID en mayúsculas.
  **Advertencia:** este ID es cosmético, no es único garantizado y no debe usarse
  como referencia para soporte técnico ni para búsquedas manuales en BD.

**Solución futura (opcional)**
Añadir un campo `title` (o `description`) al `ReservInstance` y exponerlo en el DTO.
Alternativa: generar un número correlativo legible mediante una secuencia de BD.

**Archivos afectados**
- `ReservInstance.java` — nuevo campo `title`
- `ReservInstanceResponseDTO.java` — parámetro `String title`
- `ReservInstanceMapper.java` — mapeo
- Frontend: reemplazar `classroomName` por `title` como texto principal cuando exista
