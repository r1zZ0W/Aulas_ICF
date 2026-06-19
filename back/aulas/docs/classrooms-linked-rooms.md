# Aulas vinculadas — Decisiones de diseño pendientes

Este documento recoge los problemas de negocio que surgen de la relación
padre/hija entre aulas (`linked_room_id`) y las opciones de solución para
que el equipo pueda tomar una decisión informada antes de implementar los
cambios en el backend.

---

## 1. El "Efecto Orfandad" — ¿Qué pasa con las hijas cuando se da de baja al padre?

Hoy, al dar de baja un aula (baja lógica, `isActive = false`), sus aulas hijas
siguen apuntando al mismo `linked_room_id`. El frontend muestra "Padre no
disponible" como resiliencia de presentación, pero el dato inconsistente persiste
en la BD.

### Opciones de solución

#### A. Desvincular hijas automáticamente ⭐ **(recomendada)**

Al dar de baja un aula padre, el backend ejecuta `SET linked_room_id = NULL` en
todas las hijas directas.

```sql
UPDATE classrooms SET linked_room_id = NULL WHERE linked_room_id = :parentId;
```

O en `ClassroomService.deleteByUuid`:

```java
classroomRepository.unlinkChildren(classroom.getId());
classroom.setIsActive(false);
classroomRepository.save(classroom);
```

**Pros:** preserva historial, evita cascadas sorpresivas, alineado con el NFR DFR
"nada se elimina físicamente". Las hijas quedan "solteras" y siguen siendo
operativas.

**Contras:** el administrador pierde la información de agrupación sin aviso;
convendría mostrar una advertencia en el modal de baja si el aula tiene hijas.

#### B. Cascada — desactivar también las hijas

Al dar de baja al padre, se desactivan automáticamente todas las hijas (y las
nietas, si aplica).

**Pros:** consistencia de estado.

**Contras:** efecto sorpresa alto; una baja de un bloque podría desactivar 10+
aulas sin que el admin lo espere. No recomendado salvo flujos muy controlados.

#### C. Bloquear — impedir la baja mientras existan hijas

El backend lanza un error 409 si el aula tiene hijas vinculadas activas.

**Pros:** el admin es consciente de cada cambio.

**Contras:** más fricción de uso; el admin tiene que desvincular manualmente cada
hija antes de poder dar de baja al padre.

---

## 2. Propuesta de endpoints — separar baja lógica de borrado físico

### Estado actual

```
DELETE /api/v1/classrooms/{uuid}  →  baja lógica (isActive = false)
```

El método se llama `DELETE` pero solo desactiva el registro. Esto genera
confusión: un consumidor espera que `DELETE` elimine el recurso, pero aquí
solo lo desactiva.

### Propuesta

Separar los dos conceptos en endpoints distintos:

| Endpoint | Acción | Descripción |
|---|---|---|
| `PATCH /api/v1/classrooms/{uuid}/deactivate` | Baja lógica | `isActive = false`. El aula deja de aparecer en el catálogo del maestro. |
| `PATCH /api/v1/classrooms/{uuid}/reactivate` | Reactivar | `isActive = true`. Complementario al anterior. |
| `DELETE /api/v1/classrooms/{uuid}` | **Borrado físico real** | Elimina el registro de la BD. Solo cuando no existan referencias activas. |

### Caveats del borrado físico

Antes de implementar `DELETE` como borrado real hay que resolver:

1. **Integridad referencial con reservaciones.** Las tablas
   `reserv_instances` y `reserv_slots` tienen FK a `classrooms.id`.
   Borrar un aula con reservaciones asociadas viola la FK.

   Opciones:
   - Denegar el borrado si existen reservaciones (retornar 409).
   - Transferir las reservaciones a "aula no disponible" (estado
     especial).
   - Eliminar en cascada (destruye historial — violación del NFR DFR).

2. **Regla de hijas.** Si el aula tiene hijas vinculadas, ¿se desvinculan
   primero (opción A del punto 1) o se bloquea el borrado?

3. **NFR DFR.** El documento de requisitos especifica "nada se elimina
   físicamente". El borrado físico requiere una excepción explícita y
   aprobada por el dueño del proyecto antes de implementarse.

### Migración del frontend

- El frontend usaría `PATCH /deactivate` donde hoy llama a `DELETE`.
- La UI ya muestra "Dar de baja" como etiqueta — el cambio es transparente
  para el usuario.
- `DELETE` real estaría detrás de un segundo modal de confirmación más
  explícito ("Esta acción es irreversible").

---

## 3. Prevención de ciclos (implementada en el frontend)

El selector de "Aula padre" del formulario de creación/edición ya excluye:

1. El aula que se está editando (no puede ser su propio padre).
2. Todos sus descendientes (hijas, nietas…) calculados en
   `src/utils/classroomTree.js → getDescendantUuids`.

Esto previene el problema "Aulas Matrioshka" (A es padre de B, B es padre de A).
El backend debería reforzar esta validación en `ClassroomService.save/update` para
evitar ciclos introducidos por llamadas directas a la API.

### Validación sugerida en backend

```java
private void assertNoCycle(Classroom target, Classroom linkedRoom) {
    Classroom cursor = linkedRoom;
    while (cursor != null) {
        if (cursor.getId().equals(target.getId()))
            throw new DomainException("Linked room creates a cycle: " + target.getName());
        cursor = cursor.getLinkedRoom();
    }
}
```

Llamar a `assertNoCycle(classroom, linked)` antes de `classroom.setLinkedRoom(linked)`
en `save` y `update`.
