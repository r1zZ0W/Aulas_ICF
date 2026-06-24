# Reservation History

## Propósito

Este módulo implementa un **registro de historial de cambios** (_change history_) para las reservas del sistema, satisfaciendo los requerimientos de auditoría del DFR y la LFTAIP bajo el principio de que "nada se elimina físicamente" y todo cambio de estado debe poder rastrearse.

### ¿Qué problema resuelve?

Antes de este módulo, las operaciones de cancelación, reasignación y creación de reservas no dejaban rastro auditable. Una reserva cancelada perdía todo contexto: quién la canceló, cuándo y por qué motivo. Esta entidad resuelve ese vacío sin alterar la lógica de negocio existente.

### ¿Por qué una entidad dedicada y no logs de aplicación?

Los logs de aplicación (Logback, ELK) son volátiles, difíciles de consultar relacionalmente y no están disponibles desde la API. Una entidad JPA permite:

- Consultar el historial por reserva con filtros, paginación y ordenamiento.
- Relacionar eventos con usuarios, grupos e instancias de forma referencial.
- Incluir los datos en reportes o vistas administrativas futuras.

---

## Estructura

### Módulo

```
modules/reservations/history/
├── domain/
│   ├── ReservationEvent.java      # Enum: tipos de eventos
│   └── ReservationHistory.java    # Entidad JPA principal
├── app/
│   ├── ReservationHistoryService.java
│   ├── dtos/
│   │   └── ReservationHistoryResponseDTO.java
│   └── mappers/
│       └── ReservationHistoryMapper.java
└── infrastructure/
    ├── ReservationHistoryRepository.java
    └── ReservationHistoryController.java
```

### Tabla: `reservation_history`

| Columna                | Tipo         | Notas                                                    |
|------------------------|--------------|----------------------------------------------------------|
| `id`                   | BIGINT PK    | Auto-incremental, heredado de `BaseEntity`               |
| `uuid`                 | BINARY(16)   | Identificador público, generado automáticamente          |
| `group_id`             | BIGINT FK    | → `reservation_groups.id`, nullable                      |
| `instance_id`          | BIGINT FK    | → `reserv_instances.id`, nullable                        |
| `performed_by_user_id` | BIGINT FK    | → `users.id`, nullable (acciones de sistema)             |
| `event_type`           | VARCHAR(30)  | Valor del enum `ReservationEvent`, NOT NULL              |
| `details`              | VARCHAR(500) | Nota libre opcional; obligatoria si el actor es nulo     |
| `created_at`           | DATETIME     | Seteado por `@PrePersist` en `BaseEntity`, inmutable     |
| `updated_at`           | DATETIME     | Seteado por `@PrePersist`/`@PreUpdate` en `BaseEntity`   |

> **¿Por qué `event_type` y no `event`?**
> `EVENT` es una palabra reservada en MySQL/MariaDB (Event Scheduler). Nombrar la columna `event` causa errores de sintaxis SQL crípticos en DDL y queries nativas. Se usa `event_type` para evitar ese conflicto.

### Eventos disponibles (`ReservationEvent`)

| Constante              | Cuándo se registra                                  |
|------------------------|-----------------------------------------------------|
| `CREATED`              | Al crear una instancia de reserva (individual o recurrente) |
| `UPDATED`              | Al modificar datos de una instancia existente       |
| `CANCELLED_BY_USER`    | Cuando el maestro propietario cancela su reserva    |
| `CANCELLED_BY_ADMIN`   | Cuando un administrador cancela la reserva          |
| `REASSIGNED`           | Cuando un administrador reasigna aula o slots       |

### Relaciones

Todas las FKs son **nullable** de forma intencional:

- `group` y `instance` son ambas opcionales para permitir registrar eventos a distintos niveles de granularidad sin restricciones artificiales.
- `performedBy` es nullable para acomodar acciones del sistema o procesos sin sesión HTTP (ver [Contextos asíncronos](#⚠️-actor-en-contextos-asíncronos-o-de-sistema)).

---

## Flujo

```
Operación exitosa sobre una reserva
         ↓
ReservationHistoryService.register(instance, ReservationEvent.X, "detalle")
         ↓
ReservationHistory persisted (dentro de la misma transacción)
         ↓
reservation_history row committed junto con el cambio
```

### Transaccionalidad (Change History, no Security Audit)

Los métodos de escritura del servicio usan la propagación por defecto **`REQUIRED`**, lo que significa que **participan en la transacción del llamador**. Si la operación de la reserva hace rollback, el registro de historial desaparece con ella.

Esto es intencional y correcto para este caso de uso:
- Solo se registran cambios **exitosos** → consistencia total entre el estado actual y el historial.
- La integridad referencial de las FKs se mantiene: nunca puede existir un registro apuntando a una reserva que nunca se persistió.

#### ¿Qué pasa si quiero saber qué *intentó* hacer alguien?

Eso es **auditoría de seguridad**, no historial de cambios. Pertenece a:
- Logs de aplicación (Logback configurado con nivel DEBUG/WARN en el service).
- Una tabla separada de tipo _flat soft-reference_ (sin FKs relacionales), escrita con `REQUIRES_NEW` para sobrevivir rollbacks.

Ese es un módulo futuro independiente. Este módulo **no** lo cubre.

---

## Integración

### Desde dónde llamar

`ReservationHistoryService` está inyectado como `private final` en `ReservInstanceService`. Todos los puntos de integración están dentro de métodos `@Transactional(rollbackFor = Exception.class)`:

| Método en `ReservInstanceService` | Evento registrado      | Llamada                                                   |
|-----------------------------------|------------------------|-----------------------------------------------------------|
| `createBooking(...)` (batch)      | `CREATED` (por instancia) | `historyService.registerAll(saved, CREATED, "...")`    |
| `save(...)` (instancia única)     | `CREATED`              | `historyService.register(saved, CREATED, "...")`          |
| `cancelByUser(...)`               | `CANCELLED_BY_USER`    | `historyService.register(saved, CANCELLED_BY_USER, "...")`|
| `cancelByAdmin(...)`              | `CANCELLED_BY_ADMIN`   | `historyService.register(saved, CANCELLED_BY_ADMIN, ".")` |
| `reassign(...)`                   | `REASSIGNED`           | `historyService.register(saved, REASSIGNED, "...")`       |

### ¿Cuándo registrar nuevos eventos?

Llama a `register(...)` o `registerAll(...)` **siempre dentro del mismo método `@Transactional`**, después de que el cambio se haya persistido con `repository.save(...)` y antes del `return`. Así garantizas atomicidad: o ambos commit juntos, o ambos hacen rollback.

### Usando `registerAll` para reservas recurrentes

Cuando se crean múltiples instancias en un solo booking (e.g. un grupo semanal durante un semestre), usa `registerAll` en lugar de llamar `register` en un loop:

```java
// MAL: N round-trips individuales a la BD
for (ReservInstance inst : saved) {
    historyService.register(inst, ReservationEvent.CREATED, "...");
}

// BIEN: Hibernate puede batchar todas las inserciones en un solo viaje
historyService.registerAll(saved, ReservationEvent.CREATED, "Reservation created");
```

### ⚠️ Actor en contextos asíncronos o de sistema

`resolveActor()` lee un `ThreadLocal` del contexto de Spring Security. Funciona correctamente en peticiones HTTP síncronas.

**Perderás el actor** (resultado: `performed_by_user_id = null`) si el método que llama a `register` se ejecuta en:
- Un hilo `@Async`.
- Un `ThreadPoolTaskExecutor`.
- Una tarea `@Scheduled` (e.g., limpieza de reservas vencidas por cron).

**Regla obligatoria para contextos de sistema:** El campo `details` se convierte en el único rastro de quién o qué actuó. Siempre pasa una descripción estricta:

```java
// Ejemplo en una tarea programada
historyService.register(instance, ReservationEvent.CANCELLED_BY_ADMIN,
    "System Cron: evicted expired reservation");
```

---

## Ejemplos

### Registrar un evento desde un Service

```java
// Caso 1 — Actor conocido (el dueño ya fue cargado en el flujo)
historyService.register(
    instance.getGroup(),
    instance,
    ReservationEvent.CREATED,
    ownerUser,              // User entity ya disponible
    "Reservation created"
);

// Caso 2 — Actor auto-resuelto desde el contexto de seguridad (HTTP request)
historyService.register(instance, ReservationEvent.CANCELLED_BY_ADMIN, "Cancelled by administrator");

// Caso 3 — Batch para reservas recurrentes
historyService.registerAll(savedInstances, ReservationEvent.CREATED, "Reservation created");
```

### Consultar historial desde la API (ADMIN only)

```http
GET /api/v1/reservations/history/instance/{instanceUuid}?page=0&size=10&sort=createdAt&direction=desc
Authorization: Bearer <admin-token>
```

Respuesta:
```json
{
  "message": "Success",
  "data": {
    "items": [
      {
        "uuid": "...",
        "eventType": "CANCELLED_BY_ADMIN",
        "groupUuid": "...",
        "instanceUuid": "...",
        "performedByUuid": "...",
        "performedByName": "Juan Pérez García",
        "details": "Cancelled by administrator",
        "createdAt": "2026-06-23T14:30:00"
      },
      {
        "uuid": "...",
        "eventType": "CREATED",
        "groupUuid": "...",
        "instanceUuid": "...",
        "performedByUuid": "...",
        "performedByName": "María López",
        "details": "Reservation created",
        "createdAt": "2026-06-20T09:15:00"
      }
    ],
    "totalElements": 2,
    "totalPages": 1,
    "page": 0,
    "size": 10,
    "first": true,
    "last": true
  },
  "error": null
}
```

```http
GET /api/v1/reservations/history/group/{groupUuid}?page=0&size=20&sort=eventType&direction=asc
Authorization: Bearer <admin-token>
```

### Ordenamiento soportado

| Parámetro `?sort=` | Descripción                               |
|--------------------|-------------------------------------------|
| `createdAt`        | Fecha del evento (default, descendente)   |
| `eventType`        | Tipo de evento (alfabético)               |

Cualquier otro valor de `sort` retorna **HTTP 400** (validado por `@SortWhitelist`).

---

## Buenas prácticas para futuras implementaciones

1. **Siempre dentro de la misma transacción** — no crear un método `@Async` para el registro de historial pensando en "no bloquear el request". Si la reserva falla, el historial debe fallar también.

2. **Usar `registerAll` para operaciones en batch** — cualquier caso donde se persistan múltiples instancias en un loop debe delegarse al método de batch para no saturar el contexto de Hibernate.

3. **`details` no es opcional cuando el actor puede ser nulo** — en tareas programadas o procesos de sistema, el campo `details` es el único contexto disponible. Hazlo descriptivo: incluye el tipo de proceso, el motivo y cualquier identificador relevante.

4. **No agregar nuevos valores al enum `ReservationEvent` sin consenso** — los valores del enum se almacenan como strings en la BD. Añadir un valor es backward-compatible, pero eliminar o renombrar uno rompería todos los registros históricos existentes con ese valor.

5. **No implementar endpoints de escritura HTTP para este módulo** — `ReservationHistory` es append-only. La única forma de crear registros es a través de `ReservationHistoryService` desde el código interno. No exponer `POST`, `PUT` ni `DELETE`.
