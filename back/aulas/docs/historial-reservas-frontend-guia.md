# Guía de Integración — Módulo Historial de Reservas (v1.2)

Esta guía describe el contrato actualizado que el **frontend** debe consumir para el módulo
"Historial de Reservas" tras implementar las brechas v1.1/v1.2 del backend.

---

## 1. Endpoints de listado (paginados + filtros server-side)

### 1.1 Todas las reservas (solo ADMIN)

```
GET /api/v1/reservations
```

### 1.2 Reservas de un usuario

```
GET /api/v1/reservations/user/{userUuid}
```

- Un **Maestro** solo puede consultar su propio `userUuid` (cualquier otro → 403).
- Un **ADMIN** puede consultar cualquier `userUuid`.

---

## 2. Parámetros de query

Todos los parámetros son **opcionales** y se combinan con **AND**.

| Parámetro | Tipo | Formato | Descripción |
|-----------|------|---------|-------------|
| `page` | `int` | `0` (base-0) | Número de página |
| `size` | `int` | `20` | Registros por página (máx. 100) |
| `sort` | `string` | `createdAt` \| `date` \| `status` | Campo de ordenamiento; valor inválido → 400 |
| `direction` | `string` | `asc` \| `desc` | Dirección de ordenamiento |
| `search` | `string` | texto libre | Búsqueda en nombre del aula, firstName del maestro, lastNames y su concatenación |
| `status` | `string` | ver tabla abajo | Filtro exacto por estado |
| `classroomId` | `UUID` | `xxxxxxxx-xxxx-...` | UUID del aula |
| `from` | `LocalDate` | `yyyy-MM-dd` | Fecha mínima inclusive |
| `to` | `LocalDate` | `yyyy-MM-dd` | Fecha máxima inclusive |

### Valores válidos para `status`

| Valor | Descripción |
|-------|-------------|
| `ACTIVE` | Reserva activa |
| `CANCELLED_BY_USER` | Cancelada por el maestro |
| `CANCELLED_BY_ADMIN` | Cancelada por un administrador |

Un valor desconocido (p. ej. `PENDING`) devuelve **HTTP 400**.

### Ejemplos de URL

```
# Todas las reservas del aula X en junio 2026, ordenadas por fecha
GET /api/v1/reservations?classroomId=550e8400-e29b-41d4-a716-446655440000&from=2026-06-01&to=2026-06-30&sort=date&direction=asc

# Reservas del maestro que contengan "programación" en aula o nombre, solo canceladas por admin
GET /api/v1/reservations/user/{userUuid}?search=programación&status=CANCELLED_BY_ADMIN

# Segunda página de reservas activas
GET /api/v1/reservations?status=ACTIVE&page=1&size=10
```

---

## 3. Forma de la respuesta

```typescript
// ApiResponse<PagedResultDTO<ReservInstanceResponseDTO>>
{
  "data": {
    "items": ReservInstanceResponseDTO[],
    "totalElements": number,  // cuenta FILTRADA (no el total global)
    "totalPages": number,
    "page": number,
    "size": number,
    "first": boolean,
    "last": boolean
  },
  "message": "...",
  "status": 200
}
```

### `ReservInstanceResponseDTO` (campos actualizados)

```typescript
interface ReservInstanceResponseDTO {
  uuid: string;              // UUID de la instancia
  groupUuid: string;         // UUID del grupo
  userUuid: string;          // UUID del maestro
  userFullName: string;      // Nombre completo del maestro
  userUsername: string;      // Username del maestro
  classroomUuid: string;     // UUID del aula
  classroomName: string;     // Nombre del aula
  date: string;              // "yyyy-MM-dd"
  status: "ACTIVE" | "CANCELLED_BY_USER" | "CANCELLED_BY_ADMIN";
  attendeeCount: number;
  timeSlots: TimeSlotDTO[];
  createdAt: string;         // ISO-8601
  reassigned: boolean;       // true = fue reasignada por un admin
  title: string | null;      // Etiqueta libre; null cuando no fue proporcionada
}
```

---

## 4. Cambios en la creación de reservas (POST)

Se agregó el campo `title` **opcional** a los dos endpoints de creación:

```
POST /api/v1/reservations/booking   (BookingRequestDTO)
POST /api/v1/reservations           (ReservInstanceRequestDTO)
```

```json
{
  "classroomUuid": "...",
  "attendeeCount": 30,
  "timeSlotIds": [3, 4, 5],
  "startDate": "2026-07-15",
  "title": "Programación I — Examen parcial"
}
```

### Reglas para `title`

- Máximo **150 caracteres** (validación en backend: `@Size(max=150)` sobre el string crudo).
- **El front debe hacer `.trim()` antes de enviar.** El backend también normaliza, pero si el
  campo contiene solo espacios y supera los 150 caracteres, `@Size` lo rechaza con 400 antes
  de llegar a la normalización.
- El backend normaliza `""` y strings en blanco a `null` — la respuesta nunca trae cadena vacía.
- Si `title` es `null` en la respuesta, usar `classroomName` como texto principal (la lógica de
  `res.title ?? res.classroomName` funciona correctamente porque el backend garantiza que
  `title` nunca es `""`).

---

## 5. Uso del campo `reassigned`

El campo `reassigned: boolean` en el DTO indica que un administrador reasignó la instancia a
otra aula/horario. El `status` **permanece `ACTIVE`** después de una reasignación.

```typescript
// Lógica de badge sugerida
function getBadge(instance: ReservInstanceResponseDTO): string | null {
  if (instance.status === "CANCELLED_BY_USER")  return "Cancelada";
  if (instance.status === "CANCELLED_BY_ADMIN") return "Cancelada (admin)";
  if (instance.reassigned)                      return "Reasignada";
  return null;
}
```

---

## 6. Mitigaciones que ya pueden retirarse

| Mitigación temporal | Reemplazada por |
|---------------------|-----------------|
| Filtro en memoria sobre la página actual (Brecha 1 TODO) | Filtros server-side (`search`, `status`, `classroomId`, `from`, `to`) |
| `getShortId(uuid)` → `"RE-" + uuid.substring(0,8)` como ID cosmético | Campo `title` del DTO (con fallback a `classroomName` cuando `null`) |
| Badge "Reasignada" derivado del módulo `history` (N+1) | Campo `reassigned: boolean` en el DTO |

---

## 7. Notas de comportamiento y advertencias

### `search` — rendimiento
- El filtro usa `LIKE %term%` con comodín inicial, que **no usa índices** (full table scan).
- **Mínimo 3 caracteres antes de disparar la búsqueda** (con debounce de ~300 ms).
- La búsqueda es case-insensitive y cubre: nombre del aula, firstName del maestro, lastNames
  del maestro y la concatenación `firstName + " " + lastNames`.

### `sort=status` — orden no lógico
- El enum está mapeado como `@Enumerated(STRING)`, así que `sort=status` ordena
  **alfabéticamente en inglés**: `ACTIVE` → `CANCELLED_BY_ADMIN` → `CANCELLED_BY_USER`.
- Si se requiere un orden semántico (ej. activas primero), ordenar/agrupar en cliente
  después de recibir la página, o usar `sort=date` como alternativa.

### `totalElements` es el conteo filtrado
- Cuando se aplican filtros, `totalElements` refleja el total de registros que cumplen los
  criterios, **no** el total global de la tabla. Usarlo para la paginación es correcto.

### Fechas
- Formato `yyyy-MM-dd` (ISO 8601), sin zona horaria.
- Ejemplo: `from=2026-06-01&to=2026-06-30`.

---

## 8. Script de migración de BD (para el equipo de despliegue)

Antes de desplegar v1.1 o v1.2 en producción (`ddl-auto=validate`), ejecutar los scripts en orden:

1. `docs/migration_v1.1__sync_reassigned.sql` — añade columna `reassigned` y backfill desde auditoría
2. `docs/migration_v1.2__add_title.sql` — añade columna `title` (nullable, sin backfill)

En desarrollo (`ddl-auto=update`) Hibernate crea/actualiza las columnas automáticamente al arrancar.
