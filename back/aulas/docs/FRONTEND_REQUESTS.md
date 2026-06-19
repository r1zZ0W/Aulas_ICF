# Peticiones del Frontend al Backend — derivadas de la paginación

> Contexto: el frontend va a migrar `GET /api/v1/users` a **paginación del lado del
> servidor** (page/size/sort/direction), siguiendo `PAGINATION.md`. Al hacerlo se
> destapan dos huecos que hoy el frontend resolvía en cliente porque tenía **todos** los
> registros cargados. Estos dos endpoints/params eliminarían esos huecos. Ninguno es
> bloqueante: el frontend tiene un *workaround* temporal para cada uno, pero son
> subóptimos.
>
> **Estado:** ✅ Ambas peticiones implementadas en backend (2026-06-17).

---

## 1. ✅ Búsqueda en `/api/v1/users` (param `search`)

**Problema.** La tabla de usuarios tiene un buscador que filtra por nombre, apellidos,
correo y username. Hoy funciona porque el front carga la lista completa y filtra en
memoria. Con paginación del servidor el front solo tiene una página, así que el buscador
quedaría limitado a filtrar la página visible (inútil para buscar en todo el padrón).

**Workaround actual del front.** Filtrar solo la página cargada, con una nota de que la
búsqueda es local a la página.

**Petición.** Un query param de búsqueda en el endpoint paginado:

```
GET /api/v1/users?search=garcia&page=0&size=10&sort=firstName&direction=asc
```

- `search` (`string`, opcional): texto libre; el backend hace `LIKE`/`ILIKE` sobre
  `firstName`, `lastNames`, `email`, `username` (y `matricula` si aplica).
- Se combina con `page/size/sort/direction` y respeta el contrato de respuesta paginada
  existente (`{ items, totalElements, totalPages, page, size, first, last }`).
- `totalElements` debe reflejar el total **filtrado** (para que el paginador sea correcto).

---

## 2. ✅ Conteos/estadísticas de usuarios (endpoint de agregados)

**Problema.** La pantalla muestra 4 tarjetas: **Total**, **Activos**, **Inactivos**,
**Administradores**. Hoy se calculan en cliente sobre la lista completa. Con paginación del
servidor el front ya no tiene la lista completa, así que `totalElements` solo da el
**Total**; el desglose Activos/Inactivos/Admins no se puede calcular desde una sola página.

**Workaround actual del front.** Una segunda llamada a `GET /api/v1/users` sin params
(que devuelve todo en una página grande) solo para contar. Funciona pero trae todo el
padrón únicamente para sumar — desperdicia el beneficio de paginar.

**Petición.** Un endpoint ligero de agregados (ADMIN):

```
GET /api/v1/users/stats
```

```json
{
  "message": "...",
  "error": false,
  "data": {
    "total": 47,
    "active": 41,
    "inactive": 6,
    "admins": 3
  }
}
```

- Resuelto idealmente con `COUNT(...)` agrupado en BD, sin materializar la lista.
- Si más adelante hay filtros en la tabla, sería deseable que `stats` acepte los mismos
  filtros (`search`, etc.) para que las tarjetas reflejen el subconjunto mostrado — pero
  para la primera versión basta con los totales globales.

---

## Prioridad sugerida

| # | Petición | Impacto si NO se hace | Prioridad |
|---|---|---|---|
| 1 | `search` en `/users` | Buscador degradado (solo página actual) | Alta |
| 2 | `/users/stats` | Llamada extra trae todo el padrón para contar | Media |

> El resto de endpoints del documento de paginación (classrooms, reservations, resources,
> reservation-groups) aún no tienen cliente real en el frontend; cuando se construyan
> reutilizarán el mismo contrato y, si aplica, estas mismas peticiones (`search`, `stats`).
