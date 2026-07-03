/**
 * @fileoverview Validation schemas for the Reportes y Estadísticas dashboard.
 *
 * The `ReservationStatisticsSchema` mirrors exactly the shape of the
 * `ReservationStatisticsDTO` that the future backend endpoint will return
 * (see back/aulas/docs/reportes-estadisticas.md). The mock in `src/api/reports.js`
 * always produces data that passes this schema, so switching from mock to real
 * API requires only changing the fetch body — the validation layer stays identical.
 */
import { z } from 'zod';

// ─── Scope enum ───────────────────────────────────────────────────────────────

/** Analysis period granularity. */
export const ReportScopeEnum = z.enum(['MENSUAL', 'SEMESTRAL'], {
  errorMap: () => ({ message: 'Scope debe ser MENSUAL o SEMESTRAL' }),
});

// ─── Series items ─────────────────────────────────────────────────────────────

/** One bar in the "Aulas Más Ocupadas" chart (horas totales). */
const AulaOcupacionItemSchema = z.object({
  nombre: z.string(),
  horas: z.number().nonnegative(),
});

/** One bar in the "Usuarios con Más Reservas" chart (conteo de instancias). */
const UsuarioReservasItemSchema = z.object({
  nombre: z.string(),
  reservas: z.number().int().nonnegative(),
});

/** Split for the recurrence donut chart. */
const RecurrenciaSchema = z.object({
  recurrentes: z.number().int().nonnegative(),
  eventuales: z.number().int().nonnegative(),
});

/** One data point in the "Tendencia" area chart.
 *  `label` = day (MENSUAL, e.g. "01") or month abbrev (SEMESTRAL, e.g. "Ene"). */
const TendenciaItemSchema = z.object({
  label: z.string(),
  reservas: z.number().int().nonnegative(),
});

// ─── Full statistics DTO ─────────────────────────────────────────────────────

/**
 * Shape of `ApiResponse<ReservationStatisticsDTO>.data` from
 * GET /api/v1/reports/statistics?scope=MENSUAL|SEMESTRAL&anchor=...
 *
 * Nullable fields signal an empty/zero period (frontend shows "—").
 */
// ─── Available months ─────────────────────────────────────────────────────────

/**
 * Response shape for `GET /api/v1/reports/available-months`: the distinct `yyyy-MM`
 * months that have at least one active reservation, newest first.
 */
export const AvailableMonthsSchema = z.array(z.string().regex(/^\d{4}-\d{2}$/));

export const ReservationStatisticsSchema = z.object({
  // ── KPIs ────────────────────────────────────────────────────────────────────
  /** Total active reservations in the period. */
  totalReservas: z.number().int().nonnegative(),
  /**
   * Percentage change vs the immediately preceding equivalent period.
   * Positive = growth; negative = decline. `null` when the previous period had
   * zero reservations (avoids division-by-zero; frontend renders as null/no badge).
   */
  totalReservasDeltaPct: z.number().nullable(),
  /** Classroom with the most occupied hours. `null` when no data. */
  aulaMasOcupada: z.object({ nombre: z.string(), horas: z.number().nonnegative() }).nullable(),
  /** User with the highest reservation count. `null` when no data. */
  mayorUsuario: z.object({ nombre: z.string(), reservas: z.number().int().nonnegative() }).nullable(),
  /**
   * Fraction of instances belonging to a multi-date (recurring) group, as a
   * percentage 0–100. A group with >1 instance = recurrente; 1 instance = eventual.
   */
  tasaRecurrenciaPct: z.number().min(0).max(100),

  // ── Chart series ────────────────────────────────────────────────────────────
  /** Top-5 classrooms by total occupied hours (desc). BarChart vertical. */
  aulasMasOcupadas: z.array(AulaOcupacionItemSchema),
  /** Top-5 users by reservation count (desc). BarChart horizontal. */
  usuariosMasReservas: z.array(UsuarioReservasItemSchema),
  /** Recurrent vs eventual split. PieChart donut. */
  recurrencia: RecurrenciaSchema,
  /**
   * Reservation count per time bucket.
   * MENSUAL: one entry per calendar day ("01"…"31"); empty days have reservas=0.
   * SEMESTRAL: one entry per month ("Ene"…"Jun"); all months in the semester present.
   */
  tendencia: z.array(TendenciaItemSchema),
});

export default ReservationStatisticsSchema;
