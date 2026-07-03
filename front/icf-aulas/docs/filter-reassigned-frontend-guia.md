# Guía de Integración — Filtro "Reasignada" en el historial de reservas

**Fecha:** 2026-06-30  
**Módulo:** `HistoryPage` / `useReservationHistory` / `api/reservations`  
**Backend requerido:** `v1.3` — expone `?reassigned=true|false` en ambos endpoints de listado  

---

## 1. Contrato del backend

```
GET /api/v1/reservations[?reassigned=true|false]
GET /api/v1/reservations/user/{uuid}[?reassigned=true|false]
```

El parámetro es **opcional** y se combina con AND con el resto de filtros:

| `reassigned` en la URL | Semántica |
|------------------------|-----------|
| ausente                | Sin restricción (retorna reasignadas y no reasignadas) |
| `true`                 | Solo instancias reasignadas por un admin (`reassigned = true`) |
| `false`                | Solo instancias nunca reasignadas |
| otro valor (p. ej. `foo`) | **400 Bad Request** (converter estándar de Spring) |

### Partición limpia (diseño acordado)

El `Select` del maestro ofrece tres opciones mutuamente excluyentes:

| Label UI    | Query params enviados                       |
|-------------|---------------------------------------------|
| Todos       | _(sin `status`, sin `reassigned`)_          |
| Activa      | `status=ACTIVE&reassigned=false`            |
| Reasignada  | `status=ACTIVE&reassigned=true`             |
| Cancelada   | `status=CANCELLED_BY_USER` _(o `_BY_ADMIN`)_ |

---

## 2. URL como única fuente de verdad — sin estado virtual

La URL almacena **params reales** (`status` + `reassigned`), no un estado virtual `REASSIGNED`.
Copiar `?status=ACTIVE&reassigned=true` en otra pestaña reproduce exactamente la vista "Reasignada".

### 2a. Añadir `reassigned` a los filtros URL

En `HistoryPage.jsx`, extender la lista de keys de `useUrlFilters`:

```js
// Antes:
const { values: filterValues, setFilter, resetFilters } =
  useUrlFilters(['status', 'classroomId', 'from', 'to']);

// Después:
const { values: filterValues, setFilter, resetFilters } =
  useUrlFilters(['status', 'reassigned', 'classroomId', 'from', 'to']);
```

### 2b. Setter atómico para el par `(status, reassigned)`

`useUrlFilters.setFilter` borra la key con valor falsy, así que no puede persistir
`reassigned=false` de forma aislada. Se necesita un setter propio que escriba/borre **ambos params**
en **un solo** `setParams` (→ una sola entrada de historial del navegador):

```js
import { useSearchParams } from 'react-router-dom';

// Dentro de HistoryPage o como hook local:
const [, setParams] = useSearchParams();

function applyStatusSelection(value) {
  setParams(prev => {
    const next = new URLSearchParams(prev);
    next.delete('page'); // resetear paginación al cambiar filtro
    if (value === 'REASSIGNED') {
      next.set('status', 'ACTIVE');
      next.set('reassigned', 'true');
    } else if (value === 'ACTIVE') {
      next.set('status', 'ACTIVE');
      next.set('reassigned', 'false');
    } else if (value) {
      // CANCELLED_BY_USER, CANCELLED_BY_ADMIN
      next.set('status', value);
      next.delete('reassigned');
    } else {
      // vacío = "Todos"
      next.delete('status');
      next.delete('reassigned');
    }
    return next;
  }, { replace: true }); // replace → no ensucia el historial de navegación
}
```

### 2c. Derivar el valor del `Select` de la URL

El `Select` necesita mostrar el estado combinado. **No** almacenar un tercer string — derivarlo:

```js
function selectValueFrom(status, reassigned) {
  if (status === 'ACTIVE' && reassigned === 'true')  return 'REASSIGNED';
  if (status === 'ACTIVE' && reassigned === 'false') return 'ACTIVE';
  return status || ''; // CANCELLED_* o vacío ("Todos")
}

// Uso:
const statusSelectValue = selectValueFrom(filterValues.status, filterValues.reassigned);
```

### 2d. Opciones del `Select`

```js
// STATUS_OPTIONS_TEACHER — añadir entrada "Reasignada"
const STATUS_OPTIONS_TEACHER = [
  { value: '',           label: 'Todos los estados'    },
  { value: 'ACTIVE',     label: 'Activa'               },
  { value: 'REASSIGNED', label: 'Reasignada'           },
  { value: 'CANCELLED_BY_USER', label: 'Cancelada'     },
];

// STATUS_OPTIONS_ADMIN — por consistencia también:
const STATUS_OPTIONS_ADMIN = [
  { value: '',                   label: 'Todos los estados'      },
  { value: 'ACTIVE',             label: 'Activa'                 },
  { value: 'REASSIGNED',         label: 'Reasignada'             },
  { value: 'CANCELLED_BY_USER',  label: 'Cancelada por maestro'  },
  { value: 'CANCELLED_BY_ADMIN', label: 'Cancelada por admin'    },
];
```

Conectar al `Select`:

```jsx
<Select
  value={statusSelectValue}           // ← valor derivado de la URL
  onChange={applyStatusSelection}     // ← setter atómico
  options={isAdmin ? STATUS_OPTIONS_ADMIN : STATUS_OPTIONS_TEACHER}
/>
```

### 2e. Normalización defensiva del "fantasma" Opción B

Si alguien edita la barra de direcciones a `?status=REASSIGNED` (estado virtual heredado de diseños
anteriores), o comparte un enlace viejo, **sin normalización** la sanitización descartaría
`REASSIGNED` del `status` pero conservaría `reassigned=true` en la URL, enviando
`?reassigned=true` sin `status=ACTIVE` — semántica incorrecta.

Añadir este rewrite **al inicio del componente**, antes de sanitizar y fetchear:

```js
// En HistoryPage, junto a la lectura de filterValues:
const [params, setParams] = useSearchParams();

// Reescribir el estado virtual heredado a params reales (idempotente)
if (params.get('status') === 'REASSIGNED') {
  setParams(prev => {
    const next = new URLSearchParams(prev);
    next.set('status', 'ACTIVE');
    next.set('reassigned', 'true');
    return next;
  }, { replace: true }); // replace → no añade entrada extra al historial
}
```

> Usar `useEffect` NO es necesario aquí si el componente ya usa React Router's
> `useSearchParams` de forma reactiva — la escritura desencadena un re-render controlado.
> Si el linter advierte "render-phase side effect", envolver en un `useLayoutEffect` vacío.

---

## 3. Sanitización anti-400

`REASSIGNED` **no es un valor válido** para el enum `status` del backend.
La sanitización debe producir los params correctos, nunca enviar `status=REASSIGNED`:

```js
// Constantes de valores válidos de status
const VALID_STATUSES = ['ACTIVE', 'CANCELLED_BY_USER', 'CANCELLED_BY_ADMIN'];

// Derivar los params seguros para el fetch
const rawStatus     = filterValues.status;
const rawReassigned = filterValues.reassigned;

// 'REASSIGNED' se expresa como status=ACTIVE + reassigned=true (lo hace applyStatusSelection).
// Aquí solo sanitizamos por si acaso la URL llega con valores inesperados.
const safeStatus     = VALID_STATUSES.includes(rawStatus) ? rawStatus : '';
const safeReassigned = rawReassigned === 'true'  ? true
                     : rawReassigned === 'false' ? false
                     : undefined; // ausente → sin restricción

// hasFilters debe incluir safeReassigned para que el botón "Limpiar" aparezca:
const hasFilters = !!(search || safeStatus || safeReassigned !== undefined || safeClassroomId || safeFrom || safeTo);
```

---

## 4. Propagación del param por la cadena de fetch

### 4a. `useReservationHistory.js`

Aceptar `reassigned` y meterlo en `filterParams`:

```js
export function useReservationHistory({
  // ... params existentes ...
  reassigned,    // boolean | undefined — nuevo
}) {
  // ...
  const filterParams = {
    page, size, search: search || undefined, sort, direction,
    status:      safeStatus      || undefined,
    reassigned,                               // ← nuevo; false se envía, undefined no
    classroomId: classroomId || undefined,
    from, to,
  };

  // queryKey ya usa filterParams como objeto → reassigned queda automáticamente incluido
  // en la clave de caché. NUNCA desestructures los campos individuales en la key o
  // "Activa" y "Reasignada" colisionarán (misma key, datos distintos).
  const { data: allData, isLoading: allLoading } = useQuery({
    queryKey: ['reservations', 'history', 'all', filterParams],  // ← objeto completo
    queryFn:  () => getReservations(filterParams),
    // ...
  });
}
```

### 4b. `api/reservations.js`

Añadir `reassigned` a la desestructuración y pasarlo a `buildPageParams`:

```js
export async function getReservations({
  page, size, sort = 'date', direction = 'desc',
  search, status, reassigned, classroomId, from, to  // ← añadir reassigned
} = {}) {
  const qs = buildPageParams({ page, size, sort, direction, search, status, reassigned, classroomId, from, to });
  // ...
}

export async function getReservationsByUser(userUuid, {
  page, size, sort = 'date', direction = 'desc',
  search, status, reassigned, classroomId, from, to  // ← añadir reassigned
} = {}) {
  const qs = buildPageParams({ page, size, sort, direction, search, status, reassigned, classroomId, from, to });
  // ...
}
```

### 4c. `utils/queryUtils.js → buildPageParams`

Añadir la entrada `reassigned` a `entries`:

```js
export function buildPageParams({ search, status, reassigned, classroomId, from, to, page, size, sort, direction } = {}) {
  const entries = [
    ['search',      search],
    ['status',      status],
    // reassigned: false ES un valor válido y DEBE enviarse al backend.
    // El filtro v !== undefined && v !== null && v !== '' lo deja pasar correctamente.
    // NO cambiar a un truthy-check (!!v) — rompería el filtro "Activa" (reassigned=false).
    ['reassigned',  reassigned],
    ['classroomId', classroomId],
    ['from',        from],
    ['to',          to],
    ['page',        page],
    ['size',        size],
    ['sort',        sort],
    ['direction',   direction],
  ].filter(([, v]) => v !== undefined && v !== null && v !== '');

  if (entries.length === 0) return '';
  return '?' + entries.map(([k, v]) => `${k}=${encodeURIComponent(v)}`).join('&');
}
```

---

## 5. Badge — sin cambios

`reservationBadge(row)` en `utils/reservations.js` ya deriva "Reasignada" de `row.reassigned`.
No requiere modificación. El DTO del backend ya incluye `reassigned: boolean`.

---

## 6. Anti-patrón: NO meter esta lógica en el mapper/schema

El mapper (`schemas/reservation.js`, `ReservInstanceResponseSchema`) es DTO → vista **puro**.
La traducción entre el valor del `Select` y los query params es **estado de URL** y vive en
`HistoryPage` + `applyStatusSelection`.

Si ves lógica de mapeo `REASSIGNED ↔ (ACTIVE + reassigned)` dentro de Zod o en el mapper,
es un code smell que rompe la separación de capas. El badge ya usa `row.reassigned`
directamente — el mapper no tiene ninguna razón legítima para procesar este campo de otra forma.

---

## 7. Checklist de Definition of Done

Marcar antes de mergear el PR:

- [ ] Cambiar el `Select` a "Reasignada": la URL muestra `?status=ACTIVE&reassigned=true` (params reales, no virtuales)
- [ ] Pegar `?status=ACTIVE&reassigned=true` en una pestaña nueva: el `Select` muestra "Reasignada" y la tabla muestra solo reasignadas
- [ ] Alternar "Activa" ↔ "Reasignada": los datos cambian (sin colisión de caché de React Query)
- [ ] Seleccionar "Todos": la URL **borra** tanto `status` como `reassigned`
- [ ] Editar la URL a `?status=REASSIGNED` manualmente: el componente la reescribe a `?status=ACTIVE&reassigned=true` y muestra "Reasignada" correctamente
- [ ] El archivo `schemas/reservation.js` y el mapper permanecen **intactos** (cero cambios)

---

## 8. Nota de rendimiento (MySQL)

La columna `reassigned` es booleana (baja cardinalidad). En el volumen actual de un instituto no
se necesita índice adicional. Si en el futuro el volumen lo justificara, la opción para MySQL es un
índice **compuesto** (MySQL no soporta índices parciales de estilo PostgreSQL):

```sql
-- Solo si el volumen lo justifica en el futuro
CREATE INDEX idx_reserv_instances_status_reassigned_date
  ON reserv_instances (status, reassigned, date);
```

No se crea ahora.
