# Módulo de Semestres — Contrato de API para el Frontend

> **Estado:** ✅ Refactor de backend completado (2026-06-19) · ✅ Implementación frontend completada (2026-06-19).
>
> Este documento describe el contrato completo del módulo `academic/semesters`
> tras el refactor de Request/Response split y estado derivado `isActive`,
> e incluye la documentación de la implementación frontend ya entregada.

---

## Endpoints disponibles

| Método | Ruta                         | Auth         | Descripción                                        |
|--------|------------------------------|--------------|----------------------------------------------------|
| GET    | `/api/v1/semesters`          | Autenticado  | Lista todos los semestres con su estado derivado   |
| GET    | `/api/v1/semesters/active`   | Autenticado  | Semestre vigente único (**objeto**, no lista)      |
| POST   | `/api/v1/semesters`          | Solo ADMIN   | Crea un nuevo semestre                             |
| PUT    | `/api/v1/semesters/{uuid}`   | Solo ADMIN   | Actualiza un semestre existente                    |

Todas las respuestas siguen la envoltura `ApiResponse`:

```json
{ "data": { /* payload */ }, "error": false }
```

Errores:

```json
{ "message": "...", "error": true }
```

---

## Formato de fechas

Las fechas se envían y reciben como cadenas **ISO-8601 sin zona horaria**:

```
"yyyy-MM-dd"   →   "2026-08-01"
```

El rango del semestre es **inclusivo**: `startDate` y `endDate` son ambas parte del período.
No envíes objetos de fecha nativos del navegador directamente; serializa con
`date.toISOString().slice(0, 10)` o el helper equivalente de tu librería de fechas.

---

## `isActive` — campo derivado (solo lectura)

`isActive` **nunca se envía** en el body de un request ni se persiste en la base de datos.
El backend lo calcula en cada lectura comparando la fecha actual del servidor contra el rango
`[startDate, endDate]` del semestre.

```
isActive = (today >= startDate) && (today <= endDate)
```

El frontend debe:
- **Mostrar** `isActive` como un badge ("Activo" / "Cerrado" / "Futuro").
- **No incluir** `isActive` en formularios de creación o edición.
- **No asumir** que el valor se mantiene entre peticiones: siempre leer del response.

> **Por qué no se persiste:** una columna `is_active` en base de datos se desincroniza
> con el tiempo (un semestre vencido queda marcado como activo hasta el próximo save).
> El enfoque derivado garantiza que el estado siempre sea correcto sin jobs programados.

---

## Endpoints en detalle

### `GET /api/v1/semesters` — Lista completa

Devuelve todos los semestres, incluyendo históricos y futuros.

**Respuesta `200`:**

```json
{
  "data": [
    {
      "uuid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
      "name": "2026-1",
      "startDate": "2026-02-01",
      "endDate": "2026-07-31",
      "isActive": false,
      "createdAt": "2026-01-10T12:00:00",
      "updatedAt": "2026-01-10T12:00:00"
    },
    {
      "uuid": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
      "name": "2026-2",
      "startDate": "2026-08-01",
      "endDate": "2027-01-31",
      "isActive": true,
      "createdAt": "2026-06-01T09:30:00",
      "updatedAt": "2026-06-01T09:30:00"
    }
  ],
  "error": false
}
```

---

### `GET /api/v1/semesters/active` — Semestre vigente único

> ⚠️ **Cambio de contrato:** este endpoint devuelve un **único objeto**, no un arreglo.
> El frontend debe manejar también el caso `404`.

Devuelve el semestre cuyo rango contiene hoy. Si existen rangos solapados (anomalía de datos),
devuelve el de mayor `endDate`.

**Respuesta `200` (hay semestre vigente):**

```json
{
  "data": {
    "uuid": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "name": "2026-2",
    "startDate": "2026-08-01",
    "endDate": "2027-01-31",
    "isActive": true,
    "createdAt": "2026-06-01T09:30:00",
    "updatedAt": "2026-06-01T09:30:00"
  },
  "error": false
}
```

**Respuesta `404` (no hay semestre vigente):**

```json
{
  "message": "No active semester for the current date: 2026-06-19",
  "error": true
}
```

El frontend debe manejar el 404 mostrando un aviso al usuario ("Sin semestre activo actualmente")
en lugar de romper el calendario o la pantalla de reservas.

---

### `POST /api/v1/semesters` — Crear semestre

**Roles:** solo `ADMIN`.

**Body (JSON):**

```json
{
  "name": "2026-2",
  "startDate": "2026-08-01",
  "endDate": "2027-01-31"
}
```

**Respuesta `201` (creado):**

```json
{
  "data": {
    "uuid": "b2c3d4e5-f6a7-8901-bcde-f12345678901",
    "name": "2026-2",
    "startDate": "2026-08-01",
    "endDate": "2027-01-31",
    "isActive": true,
    "createdAt": "2026-06-19T10:00:00",
    "updatedAt": "2026-06-19T10:00:00"
  },
  "error": false
}
```

---

### `PUT /api/v1/semesters/{uuid}` — Actualizar semestre

**Roles:** solo `ADMIN`. Envía los tres campos: `name`, `startDate`, `endDate`.

**Body (JSON):**

```json
{
  "name": "2026-2",
  "startDate": "2026-08-05",
  "endDate": "2027-02-10"
}
```

**Respuesta `200`:** mismo esquema que el `POST`.

---

## Reglas de validación — espejo para el frontend

El backend rechaza con `400` cuando alguna regla se viola. El frontend puede pre-validar
en el formulario antes de enviar para dar feedback inmediato.

### Al **crear** un semestre:

| Regla | Mensaje del backend |
|-------|---------------------|
| `name` no vacío | `"Semester name is required"` |
| `startDate` requerida | `"Start date is required"` |
| `endDate` requerida | `"End date is required"` |
| `endDate` > `startDate` | `"End date must be strictly after start date"` |
| `startDate` no puede ser pasado | `"Start date cannot be in the past"` |
| `endDate` no puede ser pasado | `"End date cannot be in the past"` |
| Nombre ya existe | `"A semester with that name already exists: <name>"` |

### Al **editar** un semestre vigente o futuro (`endDate` actual >= hoy):

| Regla | Mensaje del backend |
|-------|---------------------|
| `endDate` > `startDate` | `"End date must be strictly after start date"` |
| Nuevo `endDate` no puede quedar antes de hoy | `"End date cannot be set to a past date for an ongoing or future semester"` |
| `startDate` editable sin restricción de pasado | _(sin error; se permite corregir la fecha de inicio)_ |
| Nombre único (contra otros semestres) | `"A semester with that name already exists: <name>"` |

### Al **editar** un semestre ya concluido (`endDate` actual < hoy — históricos):

| Regla | Mensaje del backend |
|-------|---------------------|
| `endDate` > `startDate` | `"End date must be strictly after start date"` |

> La regla de "no pasado" **no aplica** a semestres históricos. Esto permite que el admin
> corrija el nombre u otros metadatos de semestres ya terminados sin que el sistema lo bloquee.

### Respuesta de error `400`:

```json
{ "message": "End date must be strictly after start date", "error": true }
```

En perfil `dev`, los errores de Bean Validation (`@NotBlank`, `@NotNull`) incluyen el nombre
del campo:

```json
{ "message": "name: Semester name is required; startDate: Start date is required", "error": true }
```

---

## Análisis de implementación frontend

### Formulario de crear/editar

```jsx
// Dos inputs tipo date — serializar a "yyyy-MM-dd" antes de enviar
<input type="date" value={form.startDate} onChange={...} />
<input type="date" value={form.endDate}   onChange={...} />
```

**Validaciones en vivo (antes de enviar):**

```js
function validate(form, isEdit, originalEndDate) {
  const today = new Date().toISOString().slice(0, 10);
  if (!form.name)              return 'El nombre es obligatorio';
  if (!form.startDate)         return 'La fecha de inicio es obligatoria';
  if (!form.endDate)           return 'La fecha de fin es obligatoria';
  if (form.endDate <= form.startDate) return 'La fecha de fin debe ser posterior al inicio';

  if (!isEdit) {
    // Crear: nada en el pasado
    if (form.startDate < today) return 'La fecha de inicio no puede ser en el pasado';
    if (form.endDate   < today) return 'La fecha de fin no puede ser en el pasado';
  } else {
    // Editar: solo bloquear endDate en el pasado si el semestre no está concluido
    const semesterConcluded = originalEndDate < today;
    if (!semesterConcluded && form.endDate < today)
      return 'No puedes mover la fecha de fin al pasado en un semestre vigente';
  }
  return null; // OK
}
```

### Badge de estado derivado

```jsx
function SemesterBadge({ isActive, startDate, endDate }) {
  const today = new Date().toISOString().slice(0, 10);
  if (isActive)          return <Badge color="green">Activo</Badge>;
  if (startDate > today) return <Badge color="blue">Futuro</Badge>;
  return                        <Badge color="gray">Concluido</Badge>;
}
```

> No mostrar ni editar `isActive` directamente; derivarlo del response para el badge.

### Obtener el semestre vigente para precargar el calendario

```js
async function fetchActiveSemester() {
  try {
    const res = await api.get('/api/v1/semesters/active');
    return res.data;              // SemesterResponseDTO (objeto único)
  } catch (err) {
    if (err.response?.status === 404) {
      showWarning('No hay un semestre activo actualmente.');
      return null;
    }
    throw err;
  }
}
```

Usar `startDate` y `endDate` del semestre vigente para acotar el selector de fechas
del calendario de reservas (`min` / `max` del input date).

---

## Regla cross-módulo — reservaciones fuera del semestre

> Esta regla **no está implementada en el módulo de semestres**; se documenta aquí para
> su implementación en el módulo de reservaciones.

**Regla:** no se puede crear una reservación cuya fecha caiga fuera del rango
`[startDate, endDate]` del semestre al que pertenece.

```
ReservInstance.date >= semester.startDate  &&  ReservInstance.date <= semester.endDate
```

El módulo de reservaciones debe:
1. Obtener el semestre asociado al `ReservationGroup`.
2. Rechazar con `400` cualquier fecha de instancia fuera del rango.

---

## Riesgos de acortar fechas con reservaciones existentes

El servicio de semestres **no valida contra reservaciones** (desacoplamiento por diseño).
Esto implica:

| Acción del admin | Riesgo |
|------------------|--------|
| Acortar `endDate` | Reservaciones ya creadas cuya fecha quede después del nuevo `endDate` quedan "fuera de rango" pero siguen en BD |
| Mover `startDate` hacia adelante | Reservaciones cuya fecha quede antes del nuevo `startDate` quedan "fuera de rango" |

**Recomendaciones de implementación:**

- El frontend debe mostrar una **advertencia** al editar fechas de un semestre si existen
  reservaciones activas en él. Esto requiere un endpoint de estadísticas o un conteo previo
  al guardar.
- Los listados y reportes de reservaciones deben filtrar por el rango vigente del semestre
  para evitar mostrar reservaciones "huérfanas".
- A futuro, el módulo de reservaciones puede añadir una validación al *consultar* instancias
  que rechace o marque las que caigan fuera del rango del semestre asociado.

---

---

## Implementación frontend — completada (2026-06-19)

> Todos los archivos listados a continuación son **nuevos** (untracked en git al momento de
> la implementación). Ningún archivo frontend existente fue reemplazado; la integración con la
> UI existente se realizó con dos líneas en `ClassroomsPage.jsx`.

### Archivos creados

Rutas relativas a `front/icf-aulas/src/`:

| Archivo | Responsabilidad |
|---------|-----------------|
| `schemas/semester.js` | `SemesterRequestSchema` (name `^\d{4}-[1-2]$`, fechas con `z.string().date()`, refine `start < end`) y `SemesterResponseSchema` (incluye `isActive` como solo lectura) |
| `api/semesters.js` | `getActiveSemester` (404 → `null`), `getSemesters`, `createSemester`, `updateSemester`; `resolveErrorMessage` con mensajes localizados por código HTTP |
| `utils/semester.js` | `deriveSemesterStatus` (active / future / concluded), `SEMESTER_STATUS_LABEL`, `SEMESTER_STATUS_BADGE_VARIANT` |
| `hooks/useSemesters.js` | react-query: keys `['semesters', 'active']` y `['semesters', 'list']`, invalidación conjunta vía `['semesters']`, mutaciones create/update usando `useApiMutation` |
| `hooks/useSemestersForm.js` | Estado local del modal: valida con schema + reglas "no pasado" condicionales por modo (create / edit vigente / edit concluido); nunca incluye `isActive` en el payload |
| `modules/shared/semesters/ActiveSemesterButton.jsx` | Split-button: acción primaria (edit/create) + caret con lista de semestres y badges de estado; modal de formulario gated por `isAdmin` |
| `modules/shared/semesters/SemesterFormFields.jsx` | Cuerpo stateless del formulario (tres inputs: `name`, `startDate`, `endDate` con `type="date"`) |
| `modules/shared/semesters/ActiveSemesterButton.css` | Estilos del split-button, dropdown y badges |

### Decisiones de diseño

**`isActive` nunca se envía.**
`isActive` vive solo en `SemesterResponseSchema`; `SemesterRequestSchema` no lo contiene.
`useSemestersForm` siempre construye el payload desde `{ name, startDate, endDate }`.

**Estado derivado de tres valores.**
El backend devuelve `isActive` (boolean, hoy ∈ [start, end]), pero no expone "Futuro".
`deriveSemesterStatus` en `utils/semester.js` añade la distinción:

```js
if (semester.isActive)          return 'active';
if (semester.startDate > today) return 'future';
return 'concluded';
```

Esto mapea a badges `success / primary / neutral` via `SEMESTER_STATUS_BADGE_VARIANT`.

**404 de `/active` → `null`, nunca excepción.**
`getActiveSemester` en `api/semesters.js` captura el `HttpError` con status 404 y
devuelve `null`. Los componentes renderizan "Crear semestre" en lugar de romper la UI.

**Validación en dos capas (espejo del backend).**

| Capa | Dónde | Qué valida |
|------|-------|-----------|
| Zod | `SemesterRequestSchema` (`schemas/semester.js`) | Formato de name, existencia del día en el calendario (`z.string().date()`), `end > start` |
| Condicional | `useSemestersForm.handleSubmit` | Reglas "no pasado" según modo: create (ambas fechas ≥ hoy), edit vigente/futuro (`endDate` ≥ hoy), edit concluido (sin restricción de pasado) |

Este espejo garantiza feedback inmediato sin ronda al servidor para los errores más comunes.

**Fechas sin conversión.**
Los inputs nativos `type="date"` producen directamente `YYYY-MM-DD`, que es el formato
que espera el backend y que valida `SemesterRequestSchema`. No se requiere serialización adicional.

**Split-button resuelve el deadlock create-vs-edit.**
La acción primaria nunca fuerza edición cuando ya hay semestres históricos:
- Si hay semestre activo → primaria = editar ese semestre.
- Si no hay activo → primaria = "Crear semestre" (aunque existan semestres concluidos).
- El caret despliega todos los semestres con sus badges para editar cualquiera,
  más un ítem explícito "Crear nuevo semestre" al final.

**Invalidación de react-query.**
Dos queries (`['semesters', 'active']` y `['semesters', 'list']`) se invalidan juntas
pasando `['semesters']` como `invalidateKey` en `useApiMutation`. Un create o update
refresca ambas en paralelo, por lo que el split-button y el dropdown siempre muestran
datos frescos tras una mutación exitosa.

### Punto de integración

`ActiveSemesterButton` se monta en el header de la página de Aulas:

```jsx
// ClassroomsPage.jsx · líneas 25, 241-242
import ActiveSemesterButton from '../semesters/ActiveSemesterButton';
// …
<ActiveSemesterButton isAdmin={isAdmin} />
```

El componente es visible para todos los roles (muestra el semestre activo como lectura);
las acciones de edición y creación están gated por la prop `isAdmin` internamente.

---

## Resumen de archivos modificados

| Acción     | Archivo |
|------------|---------|
| Modificado | `…/academic/semesters/domain/Semester.java` |
| Reescrito  | `…/academic/semesters/app/dtos/SemesterRequestDTO.java` |
| Creado     | `…/academic/semesters/app/dtos/SemesterResponseDTO.java` |
| Eliminado  | `…/academic/semesters/app/dtos/SemesterRsponseDTO.java` (stub mal escrito) |
| Eliminado  | `…/academic/semesters/app/dtos/SemesterDTO.java` |
| Reescrito  | `…/academic/semesters/app/mappers/SemesterMapper.java` |
| Reescrito  | `…/academic/semesters/app/SemesterService.java` |
| Modificado | `…/academic/semesters/infrastructure/SemesterRepository.java` |
| Reescrito  | `…/academic/semesters/infrastructure/SemesterController.java` |
| Creado     | `docs/semesters-frontend-requests.md` (este archivo) |
