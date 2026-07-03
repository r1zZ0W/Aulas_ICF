# Solicitud: Filtro de "Reasignada" en el historial de reservas

**Fecha:** 2026-06-30  
**Módulo:** `reservations/instances`  
**Solicitante:** Frontend (icf-aulas)

---

## Contexto

En la vista de **Historial de Reservas** con rol de **Maestro**, el usuario necesita
filtrar sus reservas por los estados que le son relevantes:

| Label UI        | Estado real en BD                                  |
|-----------------|----------------------------------------------------|
| Activa          | `status = ACTIVE AND reassigned = false`           |
| Reasignada      | `status = ACTIVE AND reassigned = true`            |
| Cancelada       | `status IN (CANCELLED_BY_USER, CANCELLED_BY_ADMIN)`|

El problema es que **"Reasignada" no es un `ReservInstanceStatus`** — es el campo
booleano `reassigned` sobre una instancia que sigue siendo `ACTIVE`. Por lo tanto,
el filtro `?status=REASSIGNED` no existe y el frontend no puede filtrar por ese
estado usando el parámetro `status` actual.

---

## Cambio solicitado en el backend

### Opción A (recomendada): Agregar parámetro `reassigned` al endpoint

Exponer un query param opcional `reassigned` (Boolean) en los endpoints:

- `GET /api/v1/reservations`
- `GET /api/v1/reservations/user/{userUuid}`

**Ejemplo de llamada:**
```
GET /api/v1/reservations/user/{uuid}?reassigned=true
```

#### Cambios necesarios

1. **`ReservInstanceFilter`** — agregar campo:
   ```java
   Boolean reassigned
   ```

2. **`ReservInstanceSpecification`** — agregar predicado:
   ```java
   if (f.reassigned() != null) {
       predicates.add(cb.equal(root.get("reassigned"), f.reassigned()));
   }
   ```

3. **`ReservInstanceController`** — agregar `@RequestParam` en `findAll` y `findByUser`:
   ```java
   @RequestParam(required = false) Boolean reassigned
   ```
   Y pasarlo al constructor de `ReservInstanceFilter`.

4. **`ReservInstanceService`** (si aplica) — propagar el filtro al repositorio.

### Opción B: Agregar `REASSIGNED` como valor virtual de status en el DTO

No se recomienda porque cambia el contrato del enum `ReservInstanceStatus` en el
dominio. El campo `reassigned` es explícitamente un *display hint*, no un estado
de ciclo de vida (ver documentación en `ReservInstance.java`).

---

## Impacto en el frontend

Una vez que el backend soporte `?reassigned=true`, el `HistoryPage.jsx` puede
agregar la opción a `STATUS_OPTIONS` del maestro como un filtro "sintético":

```js
// Filtros para vista maestro
const TEACHER_STATUS_OPTIONS = [
  { value: '',            label: 'Todos los estados' },
  { value: 'ACTIVE',     label: 'Activa'             },
  { value: 'REASSIGNED', label: 'Reasignada'         },  // → param: reassigned=true, status vacío
  { value: 'CANCELLED',  label: 'Cancelada'           },  // → status=CANCELLED_BY_USER,CANCELLED_BY_ADMIN
];
```

Esto requiere lógica adicional en el frontend para traducir el valor del Select
a los parámetros correctos de la API.

---

## Prioridad

**Media** — La vista de historial del maestro funciona correctamente sin este filtro
(muestra todos los estados). Solo falta la granularidad de poder filtrar
exclusivamente las reasignadas.
