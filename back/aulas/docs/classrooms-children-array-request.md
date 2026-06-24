# Aulas — Peticiones del Frontend (junio 2026)

> **Estado:** §1 implementado ✅ — §2 pendiente ⏳

---

## 1. ✅ Asignación en bloque de hijas `PUT /api/v1/classrooms/{uuid}/children`

### Contexto

La relación padre/hija es un árbol auto-referenciado (`linked_room_id`): cada aula apunta a
**un solo padre**. Desde la interfaz, un administrador necesita poder asignar varias hijas al
mismo padre en una sola operación (ej. asignar Aula 101, Aula 102 y Laboratorio B a "Edificio A").

### Implementación (backend — `ClassroomController` + `ClassroomService`)

El endpoint reemplaza la estrategia interina secuencial que existía en el frontend.

```
PUT /api/v1/classrooms/{uuid}/children
Content-Type: application/json
Authorization: Bearer <token>
Rol: ADMIN
```

**Body:**

```json
{ "childUuids": ["<uuid>", "<uuid>", "…"] }
```

**Semántica (transaccional):**

1. Carga el padre; si no existe → `404`.
2. Deduplica `childUuids` silenciosamente.
3. Carga **todos** los hijos deseados en **una sola query** (`findAllByUuidIn`); si algún UUID
   no existe → `400`.
4. Precalcula el conjunto de ancestros del padre **una vez** (`collectAncestorIds`).
5. Para cada hijo deseado (en memoria): valida `isActive`, valida ausencia de ciclo → `400`.
6. Vincula los hijos deseados (`linkedRoom = parent`).
7. Desvincula (`linkedRoom = null`) las hijas directas actuales ausentes del nuevo conjunto.
8. `saveAll` de todas las entidades modificadas — una sola transacción.
9. **Respuesta `200`:** `{ "message": "Children updated successfully" }`.
10. **Respuesta `400`:** `{ "message": "...", "error": true }` (ciclo, UUID inválido, inactivo).
11. **Respuesta `403`:** rol MAESTRO.
12. **Respuesta `404`:** padre no encontrado.

**Frontend migrado:** `src/hooks/useClassrooms.js` → `setChildrenMutation` ya llama a
`setClassroomChildren(parentUuid, childUuids)` (un único PUT).
`src/hooks/useClassroomsForm.js` → `handleEditSubmit` envía el conjunto completo deseado.

---

## 2. ⏳ Filtro de estado en `GET /api/v1/classrooms` (param `status`)

### Contexto

Actualmente el admin recibe **todas** las aulas (activas + inactivas) y el frontend aplica un
filtro de estado **client-side sobre la página cargada**. Con paginación esto es subóptimo:
el admin solo filtra la página visible, no el total de registros.

### Petición

```
GET /api/v1/classrooms?status=ACTIVE&page=0&size=10&sort=name&direction=asc
GET /api/v1/classrooms?status=INACTIVE&page=0&size=10
GET /api/v1/classrooms?status=ALL    (equivale a sin parámetro — comportamiento actual)
```

**Valores aceptados para `status`:** `ACTIVE`, `INACTIVE`, `ALL` (default si ausente).
El parámetro solo aplica cuando el rol es `ADMIN`; para `MAESTRO` se ignora (siempre devuelve
activas, como hoy).

### Respuesta

Mismo contrato `PagedResultDTO<ClassroomResponseDTO>`. `totalElements` debe reflejar el conteo
filtrado por estado.

---

## Confirmación: deactivate/reactivate ya implementados ✅

Los endpoints `PATCH /deactivate` y `PATCH /reactivate` están **implementados y disponibles**
(`ClassroomController.java:127-152`). El frontend ya los cablea en esta versión.

```
PATCH /api/v1/classrooms/{uuid}/deactivate   →  isActive = false, unlinks children (orphan A)
PATCH /api/v1/classrooms/{uuid}/reactivate   →  isActive = true
```

No se requiere ninguna acción adicional en backend para estas operaciones.

---

## Confirmación: DELETE ya implementado ✅ (con advertencia de arquitectura)

`DELETE /api/v1/classrooms/{uuid}` — elimina el aula y **en cascada** todos sus datos dependientes.

> ⚠️ **Trade-off de arquitectura.** Esta operación rompe la NFR "nada se elimina físicamente"
> y los requisitos de auditoría LFTAIP: destruye el histórico de reservas, asignaciones de
> equipos y trazabilidad académica del aula eliminada. Las alternativas más robustas —
> FKs `NULLABLE` con `SET NULL`, o soft-delete real (`@SQLDelete`/`@Where`) — están fuera
> de alcance por decisión del propietario del proyecto y deben re-evaluarse antes de
> un despliegue en entorno regulado.

**Orden de eliminación (child-before-parent):**
1. Se capturan los `group_id` afectados (antes de borrar instancias).
2. Se desvinculan hijas directas (`linked_room_id = NULL`).
3. `DELETE reserv_slots WHERE classroom_id = ?`
4. `DELETE reserv_instances WHERE classroom_id = ?`
5. `DELETE classroom_resources WHERE classroom_id = ?`
6. Se eliminan los `ReservationGroup` que quedan sin instancias (grupos huérfanos);
   la eliminación es per-entity para que Hibernate limpie `reservation_group_days`.
7. `DELETE classrooms WHERE id = ?`
