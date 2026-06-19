# Paginación de la API — Guía para el equipo de Frontend

> **Versión:** 1.0 — Aplica a todos los endpoints de listado a partir de este commit.

---

## Qué cambió (resumen rápido)

Antes los endpoints de listado devolvían `data` como un **array directo**:

```json
{
  "message": "...",
  "error": false,
  "data": [ { "uuid": "...", ... }, ... ]
}
```

Ahora `data` es **siempre** un objeto de paginación — independientemente de si mandas
params de paginación o no:

```json
{
  "message": "...",
  "error": false,
  "data": {
    "items": [ { "uuid": "...", ... }, ... ],
    "totalElements": 47,
    "totalPages": 3,
    "page": 0,
    "size": 20,
    "first": true,
    "last": false
  }
}
```

**Migración mínima requerida:** cambiar `response.data` → `response.data.items` en cada
consumidor de estos endpoints.

---

## Endpoints que cambian

| Endpoint | Rol requerido |
|---|---|
| `GET /api/v1/users` | ADMIN |
| `GET /api/v1/classrooms` | Cualquier autenticado |
| `GET /api/v1/resources` | Cualquier autenticado |
| `GET /api/v1/reservation-groups` | ADMIN |
| `GET /api/v1/reservation-groups/user?userUuid={uuid}` | ADMIN o propio usuario |
| `GET /api/v1/reservations` | Cualquier autenticado |
| `GET /api/v1/reservations/pending` | ADMIN |
| `GET /api/v1/reservations/user/{userUuid}` | ADMIN o propio usuario |

Los endpoints de detalle por UUID (`GET .../uuid`) y los catálogos pequeños (roles,
semesters, timeslots) **no cambian**.

---

## Query params disponibles

| Param | Tipo | Default | Descripción |
|---|---|---|---|
| `page` | `int ≥ 0` | `0` | Página (base cero). Negativos → 0. |
| `size` | `int 1–100` | `20` | Elementos por página. Máximo 100. |
| `sort` | `string` | Ver tabla de módulos | Campo por el que ordenar. |
| `direction` | `asc` \| `desc` | Ver tabla de módulos | Dirección del orden. |

**Sin params:** el backend devuelve una página única grande (`size=1000`) con todos los
registros. El contrato `{ items, totalElements, ... }` es el mismo.

---

## Campos ordenables por endpoint

| Endpoint | Campos (`?sort=`) | Default |
|---|---|---|
| `/users` | `createdAt`, `email`, `username`, `matricula`, `firstName` | `createdAt` DESC |
| `/classrooms` | `createdAt`, `name`, `capacity` | `name` ASC |
| `/resources` | `createdAt`, `name` | `name` ASC |
| `/reservation-groups` | `createdAt`, `status` | `createdAt` DESC |
| `/reservation-groups/user` | `createdAt`, `status` | `createdAt` DESC |
| `/reservations` | `createdAt`, `date`, `status` | `date` DESC |
| `/reservations/pending` | `createdAt`, `date`, `status` | `date` DESC |
| `/reservations/user/{uuid}` | `createdAt`, `date`, `status` | `date` DESC |

Cualquier valor fuera de la lista → **HTTP 400** con el mensaje:
```json
{ "error": true, "message": "Invalid sort field 'X'. Allowed fields: [...]" }
```

---

## Ejemplos de petición

### Sin paginación (comportamiento heredado)
```
GET /api/v1/users
Authorization: Bearer <token>
```
Respuesta: página única con todos los usuarios.

### Con paginación explícita
```
GET /api/v1/users?page=0&size=10&sort=email&direction=asc
Authorization: Bearer <token>
```

```json
{
  "message": "Operation successfully completed without any errors.",
  "error": false,
  "data": {
    "items": [ ... ],
    "totalElements": 47,
    "totalPages": 5,
    "page": 0,
    "size": 10,
    "first": true,
    "last": false
  }
}
```

### Página siguiente
```
GET /api/v1/users?page=1&size=10&sort=email&direction=asc
```

### Cola pendiente de reservas (segunda página)
```
GET /api/v1/reservations/pending?page=1&size=20&sort=date&direction=asc
Authorization: Bearer <token>   (requiere rol ADMIN)
```

---

## Schema TypeScript del objeto de paginación

```typescript
export interface PagedResult<T> {
  items: T[];
  totalElements: number;
  totalPages: number;
  page: number;       // base 0
  size: number;
  first: boolean;
  last: boolean;
}

export interface ApiResponse<T> {
  message: string;
  error: boolean;
  data: T;
}

// Ejemplo de uso:
// ApiResponse<PagedResult<UserResponse>>
```

---

## Checklist de migración — Prioridad

Empieza por los consumidores más visibles:

- [ ] `useUsers.js` — `response.data` → `response.data.items`; capturar `totalPages` / `totalElements` para paginador de `UsersPage.jsx`.
- [ ] `UsersPage.jsx` — añadir controles de paginación (página anterior/siguiente, selector de `size`).
- [ ] Consumidores de `GET /api/v1/reservations` y `/pending`.
- [ ] Consumidores de `GET /api/v1/classrooms`.
- [ ] Consumidores de `GET /api/v1/reservation-groups/user`.
- [ ] `GET /api/v1/resources` (si hay tabla de equipos en el frontend).

---

## Errores comunes

| Síntoma | Causa | Solución |
|---|---|---|
| `TypeError: response.data.map is not a function` | `data` ya no es un array | Cambiar a `response.data.items` |
| HTTP 400 `Invalid sort field` | `?sort=campo_no_permitido` | Usar solo los campos de la tabla de módulos |
| `size` recibido > 100 | El backend capa a 100 silenciosamente | Diseñar el UI para no pedir más de 100/página |
| `totalPages: 0` con registros vacíos | Normal — página vacía | Tratar `totalPages < 1` como "sin resultados" |

---

## Preguntas frecuentes

**¿Puedo seguir llamando sin `page`/`size`?**
Sí. Recibirás todos los registros en `items`, en una sola página. Solo cambia el tipo de `data` (de array a objeto). Es la migración mínima que tienes que hacer.

**¿El orden puede cambiar si no mando `sort`?**
Sí, ahora el backend aplica siempre un orden determinista (ver tabla por módulo). Antes el orden era el del motor de base de datos (no garantizado). Eso es una mejora.

**¿`page` empieza en 0 o en 1?**
En **0** (base cero). La página 1 se pide con `?page=1`.

**¿Cómo sé si es la última página?**
El campo `last: true` lo indica. También puedes comparar `page === totalPages - 1` o verificar que `items.length < size`.
