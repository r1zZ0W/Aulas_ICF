# Aulas — Peticiones del Frontend (junio 2026)

> **Estado:** ⏳ Pendientes de implementación en backend.

---

## 1. ⏳ Asignación en bloque de hijas `PUT /api/v1/classrooms/{uuid}/children`

### Contexto

La relación padre/hija es un árbol auto-referenciado (`linked_room_id`): cada aula apunta a
**un solo padre**. Desde la interfaz, un administrador necesita poder asignar varias hijas al
mismo padre en una sola operación (ej. asignar Aula 101, Aula 102 y Laboratorio B a "Edificio A").

### Implementación actual del frontend (interina)

Mientras no exista este endpoint, el frontend asigna las hijas de forma **secuencial**
(un `PUT /api/v1/classrooms/{childUuid}` por hija), para evitar colisiones optimistas / deadlocks
de JPA en el pool de Tomcat. Esta estrategia **no es atómica**:
si un PUT falla a mitad, el estado de la BD puede quedar inconsistente.
El frontend muestra un toast de error crítico y fuerza un refetch antes de cerrar el modal.

### Petición

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

1. Para cada UUID en `childUuids`: `UPDATE classrooms SET linked_room_id = <id del padre> WHERE uuid = <childUuid>`.
2. Para cada hija directa preexistente cuyo UUID **no** esté en `childUuids`:
   `UPDATE classrooms SET linked_room_id = NULL WHERE uuid = <childUuid>`.
3. Todo en una sola transacción — si cualquier paso falla, hacer rollback completo.
4. Validaciones:
   - Cada UUID en `childUuids` debe corresponder a un aula activa (`400` si no existe o está inactiva).
   - Reusar `assertNoCycle(parent, child)` para cada hija propuesta (`400` si forma ciclo).
   - Ignorar silenciosamente UUIDs duplicados dentro del mismo array.
5. **Respuesta `200`:** `{ "message": "Children updated successfully" }`.
6. **Respuesta `400`:** `{ "message": "...", "error": true }` (ciclo, UUID inválido, etc.).

**Nota de migración del frontend:** al llegar este endpoint, reemplazar el bucle `for...of` de
`setChildrenMutation` en `src/hooks/useClassrooms.js` por una única llamada a
`api.put(\`/api/v1/classrooms/\${parentUuid}/children\`, { childUuids })`.

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
(`ClassroomController.java:135-160`). El frontend ya los cablea en esta versión.

```
PATCH /api/v1/classrooms/{uuid}/deactivate   →  isActive = false, unlinks children (orphan A)
PATCH /api/v1/classrooms/{uuid}/reactivate   →  isActive = true
```

No se requiere ninguna acción adicional en backend para estas operaciones.
