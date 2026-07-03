# Guía de Integración — Módulo Reportes y Estadísticas (v1.0)

Esta guía describe el contrato de datos que el **frontend** (`/reports`) espera consumir para
el dashboard "Reportes y Estadísticas" (nodo Figma `208-44`). El backend actualmente solo ofrece
generación de PDF (`GET /api/v1/reports/reservations`); este documento especifica el **endpoint
JSON de estadísticas** que falta implementar.

---

## 1. Mapa pantalla → dato

| Elemento en la pantalla | Campo / origen |
|---|---|
| KPI — Total Reservaciones (`368`) | `totalReservas` |
| KPI — delta `+12% vs periodo anterior` | `totalReservasDeltaPct` (puede ser null) |
| KPI — Aula Más Ocupada (`Aula 101`) | `aulaMasOcupada.nombre` |
| KPI — sublínea `120 horas ocupadas` | `aulaMasOcupada.horas` |
| KPI — Mayor Usuario (`María García`) | `mayorUsuario.nombre` |
| KPI — sublínea `45 reservaciones` | `mayorUsuario.reservas` |
| KPI — Tasa de Recurrencia (`68%`) | `tasaRecurrenciaPct` |
| KPI — sublínea `de las reservas son recurrentes` | texto fijo (usa `tasaRecurrenciaPct`) |
| Gráfica Aulas Más Ocupadas (barras verticales, horas) | `aulasMasOcupadas[].{nombre, horas}` |
| Gráfica Usuarios con Más Reservas (barras horizontales) | `usuariosMasReservas[].{nombre, reservas}` |
| Gráfica Recurrencia (dona, `68%` central) | `recurrencia.{recurrentes, eventuales}` + `tasaRecurrenciaPct` |
| Gráfica Tendencia (área, Ene–Jun / días) | `tendencia[].{label, reservas}` |
| Toggle Mensual/Semestral | param `scope` de la query |
| Dropdown mes/semestre | param `anchor` de la query |
| Botón "Exportar PDF" | `GET /api/v1/reports/reservations` (ya existe) |
| Panel informativo | Texto estático; sin dato dinámico |
| Título/subtítulo de página | Texto estático |

---

## 2. Modelo de periodo

El filtro de la pantalla tiene dos dimensiones:

| Dimensión | Parámetro | Valores |
|---|---|---|
| Granularidad | `scope` | `MENSUAL` \| `SEMESTRAL` |
| Anclaje concreto | `anchor` | `yyyy-MM` (MENSUAL) \| UUID de `Semester` (SEMESTRAL) |

### Derivación del rango de fechas `[from, to]`

**MENSUAL** — `anchor = "2026-06"`:
```
from = 2026-06-01
to   = min(2026-06-30, LocalDate.now())   // no se proyecta hacia el futuro
```

**SEMESTRAL** — `anchor = <semesterUuid>`:
```
from = semester.startDate
to   = min(semester.endDate, LocalDate.now())
```

### Periodo anterior (para deltas)

| Scope | Periodo anterior |
|---|---|
| MENSUAL (`2026-06`) | `2026-05-01` … `2026-05-31` |
| SEMESTRAL (`2026-1`) | semestre inmediatamente anterior |

> Si el periodo anterior no tiene datos (`totalReservas = 0`), `totalReservasDeltaPct`
> devuelve `null` (evita división por cero). El frontend omite la sublínea delta cuando el campo es null.

---

## 3. Endpoint propuesto

```
GET /api/v1/reports/statistics
```

### Autorización

```java
@PreAuthorize("hasRole('ADMIN')")   // igual que ReportController.reservationReport()
```

### Parámetros de query

| Parámetro | Tipo | Obligatorio | Default | Descripción |
|---|---|---|---|---|
| `scope` | `String` (`MENSUAL` \| `SEMESTRAL`) | No | `MENSUAL` | Granularidad del periodo |
| `anchor` | `String` | No | mes en curso (`yyyy-MM`) o semestre activo | Anclaje del periodo |

### Errores

| Situación | HTTP | Mensaje |
|---|---|---|
| `scope` no reconocido | 400 | `"scope debe ser MENSUAL o SEMESTRAL"` |
| `anchor` con formato inválido para el scope | 400 | `"anchor inválido para scope MENSUAL/SEMESTRAL"` |
| UUID de semestre inexistente (SEMESTRAL) | 400 | `"Semestre no encontrado: <uuid>"` |

### Respuesta exitosa

```
HTTP 200
Content-Type: application/json
```

```typescript
// ApiResponse<ReservationStatisticsDTO>
{
  "data": ReservationStatisticsDTO,
  "message": "OK",
  "status": 200
}
```

---

## 4. Forma de la respuesta — `ReservationStatisticsDTO`

La interface TypeScript es **idéntica** al schema Zod de `src/schemas/report.js` del frontend,
de modo que conectar el endpoint real solo requiere cambiar el cuerpo de `getReservationStatistics()`
en `src/api/reports.js` — la capa de validación permanece intacta.

```typescript
interface AulaOcupacion {
  nombre: string;   // Classroom.name
  horas: number;    // nº de slots × 0.5 h; siempre >= 0
}

interface UsuarioReservas {
  nombre: string;   // user.firstName + " " + user.lastNames
  reservas: number; // count de ReservInstance en el periodo; siempre >= 0
}

interface Recurrencia {
  recurrentes: number;  // reservas (grupos) con >1 instancia en el periodo — no sesiones-día
  eventuales:  number;  // reservas (grupos) con exactamente 1 instancia en el periodo
}

interface TendenciaItem {
  // MENSUAL:    día con zero-padding ("01"..."31")
  // SEMESTRAL:  abreviatura de mes en es-MX ("Ene"..."Dic")
  label:    string;
  reservas: number;  // count de ReservInstance en ese bucket; 0 cuando vacío
}

interface ReservationStatisticsDTO {
  // KPIs
  totalReservas:          number;               // COUNT(ri) en el periodo
  totalReservasDeltaPct:  number | null;        // (actual-anterior)/anterior*100; null si anterior=0
  aulaMasOcupada:         AulaOcupacion | null; // null cuando no hay datos
  mayorUsuario:           UsuarioReservas | null;
  tasaRecurrenciaPct:     number;               // 0-100; recurrentes/(recurrentes+eventuales)*100 — sobre nº de reservas, no totalReservas

  // Series de gráficas
  aulasMasOcupadas:    AulaOcupacion[];    // top 5, desc por horas
  usuariosMasReservas: UsuarioReservas[];  // top 5, desc por reservas
  recurrencia:         Recurrencia;
  tendencia:           TendenciaItem[];    // un entry por día (MENSUAL) o mes (SEMESTRAL)
}
```

---

## 5. Cálculo de cada métrica desde el dominio

El **universo base** para todas las métricas es:

```
ReservInstance ri
WHERE ri.status = 'ACTIVE'
  AND ri.date  BETWEEN from AND to
```

Coherente con `ReservationReportService.generatePdf()` que ya filtra `ReservInstanceStatus.ACTIVE`.

### 5.1 `totalReservas`

```sql
SELECT COUNT(ri.id)
FROM reserv_instances ri
WHERE ri.status = 'ACTIVE'
  AND ri.date BETWEEN :from AND :to
```

### 5.2 `totalReservasDeltaPct`

1. Calcular `countActual` (§5.1 sobre el periodo actual).
2. Calcular `countAnterior` (§5.1 sobre el periodo equivalente anterior, ver §2).
3. `delta = (countActual - countAnterior) / (double) countAnterior * 100`.
4. Si `countAnterior == 0` → `delta = null`.

### 5.3 `aulaMasOcupada` y `aulasMasOcupadas`

Cada `ReservSlot` representa un bloque de **30 minutos** (confirmado en `TimeSlot`).

```sql
SELECT c.name AS nombre,
       COUNT(rs.instance_id) * 0.5 AS horas
FROM reserv_slots rs
JOIN reserv_instances ri ON ri.id = rs.instance_id
JOIN classrooms c         ON c.id = rs.classroom_id
WHERE ri.status = 'ACTIVE'
  AND rs.date  BETWEEN :from AND :to
GROUP BY c.id, c.name
ORDER BY horas DESC
LIMIT 5
```

`aulaMasOcupada` = el primer elemento; `aulasMasOcupadas` = los 5.
Si la lista está vacía → `aulaMasOcupada = null`.

### 5.4 `mayorUsuario` y `usuariosMasReservas`

```sql
SELECT CONCAT(u.first_name, ' ', u.last_names) AS nombre,
       COUNT(ri.id) AS reservas
FROM reserv_instances ri
JOIN reservation_groups rg ON rg.id = ri.group_id
JOIN users u               ON u.id  = rg.user_id
WHERE ri.status = 'ACTIVE'
  AND ri.date  BETWEEN :from AND :to
GROUP BY u.id
ORDER BY reservas DESC
LIMIT 5
```

La concatenación `firstName + " " + lastNames` replica el método `fullName(ri)` de
`ReservationReportService` (línea 155).

### 5.5 Recurrencia (`recurrencia` y `tasaRecurrenciaPct`)

Se cuenta por **reserva (`ReservationGroup`)**, no por sesión-día — una clase semanal con 12
sesiones en el periodo aporta `1` a "recurrentes", no 12. Una reserva es **recurrente** si su
`ReservationGroup` tiene más de una instancia activa en el rango de fechas del periodo;
**eventual** si tiene exactamente una.

```sql
SELECT
  SUM(CASE WHEN grupo_count > 1 THEN 1 ELSE 0 END) AS recurrentes,
  SUM(CASE WHEN grupo_count = 1 THEN 1 ELSE 0 END) AS eventuales
FROM (
    SELECT ri.group_id, COUNT(ri.id) AS grupo_count
    FROM reserv_instances ri
    WHERE ri.status = 'ACTIVE'
      AND ri.date BETWEEN :from AND :to
    GROUP BY ri.group_id
) sub
```

Implementado como una única query `GROUP BY` (`ReportStatisticsRepository.countInstancesPerGroup`)
que devuelve un conteo por grupo; el servicio clasifica cada fila (`>1` = recurrente, `==1` =
eventual). Se evita a propósito una subconsulta `EXISTS` correlacionada por fila — el `GROUP BY`
único escala mejor. Ver la nota de índice recomendado en la sección de implementación (§8) para
la migración `(status, date, group_id)`.

**Justificación**: al crear un booking con `daysOfWeek` vacío, `ReservInstanceService` genera un
grupo con exactamente una instancia (eventual). Con patrón semanal genera múltiples instancias
(recurrente). Medir por conteo de instancias del grupo (y no por `daysOfWeek.size()`) es más
robusto porque funciona incluso si las instancias futuras aún no se han materializado fuera del
rango.

```
tasaRecurrenciaPct = recurrentes / (recurrentes + eventuales) * 100
```

Donde el denominador es el **número de reservas (grupos) distintas** con instancias en el
periodo — **no** `totalReservas`, que cuenta sesiones-día. Si no hay reservas en el periodo →
`tasaRecurrenciaPct = 0`, `recurrentes = 0`, `eventuales = 0`.

### 5.6 `tendencia`

**MENSUAL** — agrupar por día del mes:

```sql
SELECT DAY(ri.date) AS dia, COUNT(ri.id) AS reservas
FROM reserv_instances ri
WHERE ri.status = 'ACTIVE'
  AND ri.date BETWEEN :from AND :to
GROUP BY DAY(ri.date)
ORDER BY dia
```

Rellenar los días del mes sin registros con `reservas = 0`.
`label = String.format("%02d", dia)`.

**SEMESTRAL** — agrupar por mes:

```sql
SELECT MONTH(ri.date) AS mes, COUNT(ri.id) AS reservas
FROM reserv_instances ri
WHERE ri.status = 'ACTIVE'
  AND ri.date BETWEEN :from AND :to
GROUP BY MONTH(ri.date)
ORDER BY mes
```

Rellenar los meses del semestre sin registros con `reservas = 0`.
`label` = abreviatura en locale `es-MX` usando el `DateTimeFormatter` de `ReservationReportService`
(ej. `"Ene"`, `"Feb"`, `"Mar"`).

**Nota (defensiva)**: un semestre bien formado nunca excede 12 meses (impuesto por
`SemesterService` al crear/editar, §8). Si un semestre legado excede ese rango, dos fechas de
distintos años caerían en la misma etiqueta (`"Ene"`) y sus conteos se sumarían incorrectamente
en un solo bucket. Para evitarlo, cuando el rango supera 12 meses cada etiqueta se desambigua con
el año (`"Ene 26"`, 2 dígitos) — ver `StatisticsScope.tendenciaLabel(LocalDate, boolean)`.

---

## 6. Bordes y comportamiento especial

| Caso | Comportamiento |
|---|---|
| Periodo sin reservas | Conteos en `0`, arrays vacíos, `aulaMasOcupada = null`, `mayorUsuario = null` |
| `totalReservasDeltaPct = null` | Frontend omite la línea `+X% vs periodo anterior` |
| `anchor` de mes futuro | `to = LocalDate.now()`, no se proyectan reservas futuras |
| Formato de fechas | `yyyy-MM-dd` ISO-8601, sin zona horaria |
| Locale | `es-MX` para nombres de mes en `tendencia.label` (SEMESTRAL) |

---

## 7. Botón "Exportar PDF"

Reutiliza el endpoint existente sin cambios:

```
GET /api/v1/reports/reservations?period=MES_EN_CURSO|MES_ANTERIOR[&classroomUuid=...]
```

El frontend lo abre en pestaña nueva (`<a target="_blank">`).

**Brecha conocida**: el PDF filtra por `MES_EN_CURSO / MES_ANTERIOR` mientras que el dashboard
permite elegir cualquier mes/semestre. Brecha aceptable por ahora; una mejora futura puede
extender `ReportController.reservationReport()` para aceptar `from`/`to` explícitos.

---

## 8. Notas de implementación (backend)

### Estructura de código sugerida

```
modules/reports/
  app/
    ReportPeriod.java                 ← ya existe (MES_EN_CURSO/ANTERIOR)
    ReservationReportService.java     ← ya existe (PDF)
    ReservationStatisticsService.java ← NUEVO: calcula las métricas de §5
    dtos/
      ReservationStatisticsDTO.java   ← NUEVO: record con los campos de §4
  infrastructure/
    ReportController.java             ← MODIFICAR: añadir endpoint /statistics
```

### Principios

- Queries de agregación (`GROUP BY COUNT`) directamente en el repositorio — no cargar entidades y agregar en memoria (evitar N+1).
- Reusar `ReservInstanceStatus.ACTIVE`, patrón de rango `findActiveByDateRange`, `fullName(ri)`, `MONTH_FMT` de `ReservationReportService`.
- Respuesta envuelta en `ApiResponse<ReservationStatisticsDTO>` (mismo wrapper del proyecto).
- `@Transactional(readOnly = true)` en el método de servicio.

### Guard de duración de semestre (causa raíz de la brecha de tendencia semestral)

`SemesterService.validateDates` impone `endDate <= startDate + 12 meses` (constante
`MAX_SEMESTER_MONTHS`) al crear/editar un semestre — incluso al editar uno ya concluido, para
impedir ampliarlo a un rango multi-año. Existe porque `StatisticsPeriodResolver` deriva el rango
`[from, to]` de SEMESTRAL directamente de `[semester.startDate, semester.endDate]` sin un tope
propio: un semestre mal configurado (p. ej. varios años) se reflejaría fielmente en el dashboard,
produciendo una gráfica de Tendencia con meses repetidos. El fix de etiquetas año-conscientes
(§5.6) es una capa defensiva adicional para datos legado anteriores a este guard.

### Índice recomendado para `countInstancesPerGroup` (recurrencia, §5.5)

El índice existente `idx_reserv_instances_group_status(group_id, status)` no cubre el predicado
`date BETWEEN :from AND :to` de la nueva query de recurrencia. Se recomienda un índice compuesto
`(status, date, group_id)` — ya declarado en `ReservInstance` vía `@Table(indexes = ...)` y
documentado en `docs/migration_v1.4__reports_status_date_group_index.sql` para aplicarlo
manualmente en bases de datos que no usan `ddl-auto=update`.

---

## 9. Conexión frontend ↔ backend — checklist

Cuando el endpoint esté listo, el único cambio en el frontend es el cuerpo de `src/api/reports.js`:

```js
// ANTES (mock):
export async function getReservationStatistics({ scope = 'MENSUAL', anchor = '' } = {}) {
  await new Promise(resolve => setTimeout(resolve, 320));
  return ReservationStatisticsSchema.parse(buildMockStats(scope, anchor));
}

// DESPUÉS (real):
export async function getReservationStatistics({ scope = 'MENSUAL', anchor = '' } = {}) {
  const qs = new URLSearchParams({ scope });
  if (anchor) qs.set('anchor', anchor);
  const { data } = await api.get(`/api/v1/reports/statistics?${qs}`);
  return ReservationStatisticsSchema.parse(data.data);
}
```

El hook `useReportStatistics`, los componentes y el schema Zod **no requieren ningún cambio**.
