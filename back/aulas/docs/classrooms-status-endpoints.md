# Aulas — Endpoints de estado: contrato y guía de migración

Este documento describe los endpoints de ciclo de vida de aulas disponibles tras
la refactorización de junio 2026, las reglas de negocio que los rigen, y los pasos
de migración necesarios en el frontend.

---

## Endpoints

| Método | Ruta | Rol | Descripción |
|--------|------|-----|-------------|
| `PATCH` | `/api/v1/classrooms/{uuid}/deactivate` | ADMIN | Baja lógica + desvincula hijas. |
| `PATCH` | `/api/v1/classrooms/{uuid}/reactivate` | ADMIN | Reactiva el aula. |
| `DELETE` | `/api/v1/classrooms/{uuid}` | — | **Eliminado.** Responde `405 Method Not Allowed`. |

> El borrado físico real está **diferido** (ver sección al final).

---

## Comportamiento de `PATCH /deactivate`

1. Busca el aula por UUID; responde `404` si no existe.
2. **Desvincula hijas (orphan cleanup, opción A):** ejecuta
   `UPDATE classrooms SET linked_room_id = NULL WHERE linked_room_id = <id>`.
   Las aulas hijas quedan "solteras" — siguen activas y operativas, pero sin padre.
3. Marca el aula como `isActive = false`.
4. Responde `200` con `{ "message": "Classroom deactivated successfully" }`.

### Consideración UX importante (pendiente en frontend)

El botón "Dar de baja" en la UI debe permanecer **deshabilitado** hasta que el
equipo de frontend implemente el aviso de hijas en el modal de confirmación.
El flujo esperado es:

1. El admin abre el modal de baja.
2. Si el aula tiene hijas vinculadas activas, el modal muestra:
   > "Este aula es el padre de N aulas. Al darla de baja, esas aulas quedarán
   > desvinculadas pero seguirán activas."
3. El admin confirma → se llama `PATCH /deactivate`.

Hasta implementar ese aviso, **no exponer la acción de baja en la UI**.

---

## Comportamiento de `PATCH /reactivate`

1. Busca el aula por UUID; responde `404` si no existe.
2. Marca el aula como `isActive = true`.
3. Responde `200` con `{ "message": "Classroom reactivated successfully" }`.
4. Las hijas que fueron desvinculadas durante una baja previa **no** se re-vinculan
   automáticamente; el administrador debe restaurar el vínculo editando cada hija.

---

## Validación de ciclos (aplicada en `save` y `update`)

Al crear o actualizar un aula con `linkedRoomUuid`, el backend recorre la cadena
de padres del aula propuesta y lanza `400 Bad Request` si detecta un ciclo:

```
{
  "success": false,
  "message": "Linked room creates a cycle in the parent chain: <name>"
}
```

Casos bloqueados:
- Auto-referencia: `linkedRoomUuid == uuid` del aula actual.
- Ciclo indirecto: A→B, intentar actualizar B con `linkedRoomUuid = A`.

El frontend ya previene esto en cliente (`src/utils/classroomTree.js →
getDescendantUuids`). La validación del backend es una segunda línea de defensa
para llamadas directas a la API.

---

## Migración del frontend

### `src/api/classrooms.js`

Reemplazar la función `deleteClassroom` (que hoy llama `api.delete`) por dos
funciones:

```js
/**
 * Deactivates a classroom (soft-delete / baja lógica). ADMIN only.
 * Automatically unlinks direct child classrooms (orphan cleanup, option A).
 * PATCH /api/v1/classrooms/{uuid}/deactivate
 *
 * NOTE: Do NOT wire this to a UI button until the children-warning UX
 * is implemented in the confirmation modal (see classrooms-status-endpoints.md).
 */
export async function deactivateClassroom(uuid) {
  try {
    await api.patch(`/api/v1/classrooms/${uuid}/deactivate`);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}

/**
 * Reactivates a previously deactivated classroom. ADMIN only.
 * PATCH /api/v1/classrooms/{uuid}/reactivate
 */
export async function reactivateClassroom(uuid) {
  try {
    await api.patch(`/api/v1/classrooms/${uuid}/reactivate`);
  } catch (error) {
    if (error instanceof HttpError) throw new Error(resolveErrorMessage(error));
    throw error;
  }
}
```

### `src/hooks/useClassrooms.js`

Reemplazar `deleteMutation` (que llama `deleteClassroom`) por las mutaciones
correspondientes una vez que se implemente la UX:

```js
const deactivateMutation = useApiMutation({
  mutationFn: deactivateClassroom,
  invalidateKey: ['classrooms'],
  successMessage: 'Aula dada de baja correctamente.',
});

const reactivateMutation = useApiMutation({
  mutationFn: reactivateClassroom,
  invalidateKey: ['classrooms'],
  successMessage: 'Aula reactivada correctamente.',
});
```

---

## Borrado físico — DIFERIDO

El borrado físico de aulas **no está implementado** y requiere decisión explícita
del dueño del proyecto (Arturo Quintero) dado que el NFR DFR establece "nada se
elimina físicamente".

Antes de implementarlo habría que resolver:

1. **Integridad referencial:** `reserv_instances` y `reserv_slots` tienen FK a
   `classrooms.id`. Un borrado físico fallaría con violación de FK si existen
   reservaciones asociadas. Opciones: denegar con `409`, transferir a estado
   "aula no disponible", o eliminar en cascada (destruye historial — violación del
   NFR DFR, descartada).
2. **Regla de hijas:** ¿desvinculadas primero, o bloquear el borrado con `409`?
3. **Aprobación del cliente** documentada antes de añadir el endpoint `DELETE`.
