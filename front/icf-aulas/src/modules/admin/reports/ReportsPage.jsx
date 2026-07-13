import { useState, useMemo } from 'react';
import {
  BarChart3, Info, Download,
  BookOpen, Building2, UserCheck, RefreshCw,
  CalendarRange,
} from 'lucide-react';
import {
  ResponsiveContainer,
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Cell,
  PieChart, Pie, Legend,
  AreaChart, Area,
} from 'recharts';

import { useReportStatistics } from './hooks/useReportStatistics';
import { useAvailableMonths } from './hooks/useAvailableMonths';
import { downloadReservationReportPdf } from '../../../api/reports';
import { toast } from '../../../utils/toast';
import Select from '../../../components/Select/Select';
import EmptyState from '../../../components/EmptyState/EmptyState';
import { useSemesters } from '../../shared/semesters/hooks/useSemesters';

import './ReportsPage.css';

// ── Palette (alineada con --moon-piccolo #005687) ──────────────────────────────

const COLOR_PRIMARY = '#005687';
const COLOR_LIGHT = '#bfdbfe';
const COLOR_BARS = [COLOR_PRIMARY, '#1a6fa0', '#3388b8', '#4da0cf', '#66b3e0'];
const COLOR_REC = COLOR_PRIMARY;
// Visibly distinct from COLOR_REC (was a near-invisible #bfdbfe) so the donut's
// "eventuales" slice/legend swatch doesn't disappear against the light card background.
const COLOR_EVE = '#94a3b8';
const COLOR_AREA = COLOR_PRIMARY;

// Subtle bar-hover guide: keeps the visual "which bar am I over" cue (full removal via
// `cursor={false}` would leave the user guessing) without the heavy default gray band.
const BAR_CURSOR = { fill: 'rgba(0, 0, 0, 0.04)' };

// 1 decimal — NOT Math.round: rounding to an integer would show a real 0.3% rate as
// "0%" (looks like zero recurrence) or a real 99.6% as "100%" (hides that exceptions
// exist), which matters for an admin-facing metric.
const pctFormatter = new Intl.NumberFormat('es-MX', { maximumFractionDigits: 1 });
function formatPct(value) {
  return pctFormatter.format(value ?? 0);
}

// ── Month label formatting ──────────────────────────────────────────────────────

/** Formats a `yyyy-MM` string as "Mes Año" in es-MX (e.g. "2026-06" → "Junio 2026"). */
function formatMonthLabel(yyyyMM) {
  const [year, month] = yyyyMM.split('-').map(Number);
  const d = new Date(year, month - 1, 1);
  return d.toLocaleDateString('es-MX', { month: 'long', year: 'numeric' })
    .replace(/^\w/, c => c.toUpperCase());
}

// ── Subcomponents ──────────────────────────────────────────────────────────────

/**
 * Single KPI card: icon + label + large value + optional delta/sub-line.
 */
function StatCard({ icon: Icon, label, value, sub, delta }) {
  const isLoading = value === undefined;

  let deltaEl = null;
  if (!isLoading && delta !== null && delta !== undefined) {
    const positive = delta >= 0;
    const sign = positive ? '+' : '';
    deltaEl = (
      <span className={`reports-page__kpi-delta ${positive ? 'reports-page__kpi-delta--positive' : 'reports-page__kpi-delta--negative'}`}>
        {sign}{delta}%
      </span>
    );
  }

  return (
    <div className="reports-page__kpi-card stat-card">
      <div className="reports-page__kpi-header">
        <div className="reports-page__kpi-icon-wrap">
          <Icon size={16} className="reports-page__kpi-icon" />
        </div>
        <p className="reports-page__kpi-label">{label}</p>
      </div>

      {isLoading ? (
        <>
          <div className="reports-page__skeleton reports-page__kpi-skeleton-value" />
          <div className="reports-page__skeleton reports-page__kpi-skeleton-sub" />
        </>
      ) : (
        <>
          <p className={`reports-page__kpi-value${typeof value === 'string' && value.length > 8 ? ' reports-page__kpi-value--lg' : ''}`}>
            {value ?? '—'}
          </p>
          {(deltaEl || sub) && (
            <div className="reports-page__kpi-sub">
              {deltaEl}
              {sub && <span className="reports-page__kpi-delta-label">{sub}</span>}
            </div>
          )}
        </>
      )}
    </div>
  );
}

/**
 * Card wrapper for charts: title + subtitle + body slot.
 */
function ChartCard({ title, subtitle, loading, children }) {
  return (
    <div className="reports-page__chart-card chart-card">
      <h3 className="reports-page__chart-title">{title}</h3>
      <p className="reports-page__chart-subtitle">{subtitle}</p>
      <div className="reports-page__chart-body">
        {loading
          ? <div className="reports-page__skeleton reports-page__chart-skeleton" />
          : children
        }
      </div>
    </div>
  );
}

// ── Custom tooltip ─────────────────────────────────────────────────────────────

function SimpleTooltip({ active, payload, label, unit = '' }) {
  if (!active || !payload?.length) return null;
  return (
    <div style={{ background: '#1e293b', color: '#f1f5f9', padding: '8px 12px', borderRadius: 8, fontSize: 13 }}>
      <p style={{ margin: 0, fontWeight: 600 }}>{label ?? payload[0].name}</p>
      <p style={{ margin: '2px 0 0', opacity: 0.85 }}>
        {payload[0].value}{unit}
      </p>
    </div>
  );
}

// ── Component ──────────────────────────────────────────────────────────────────

/**
 * Reportes y Estadísticas — ADMIN-only analytics dashboard.
 *
 * Period filter state is kept local (`useState`) for now; when the backend
 * endpoint is connected, consider migrating to `useSearchParams` (URL as
 * single source of truth) following the same pattern as `HistoryPage`.
 */
export default function ReportsPage() {
  // ── Period filter state ────────────────────────────────────────────────────
  const [scope, setScope] = useState('MONTHLY');
  const { semesters: semestersList } = useSemesters();
  const { months: availableMonths } = useAvailableMonths();

  const filteredMonths = useMemo(() => {
    if (!availableMonths || availableMonths.length === 0) return [];

    const now = new Date();
    const currentMonth = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0');

    return availableMonths.filter(month => month <= currentMonth);
  }, [availableMonths]);

  const monthOptions = useMemo(() => {
    return filteredMonths.map(m => ({ value: m, label: formatMonthLabel(m) }));
  }, [filteredMonths]);

  const latestAvailableMonth = filteredMonths[0] ?? '';

  const semesterOptions = useMemo(() => {
    return semestersList.map(s => ({
      value: s.uuid,
      label: `Semestre ${s.name}`,
    }));
  }, [semestersList]);

  const activeSemesterUuid = useMemo(() => {
    const active = semestersList.find(s => s.isActive);
    if (active) return active.uuid;
    return semestersList[0]?.uuid ?? '';
  }, [semestersList]);

  const defaultAnchor = useMemo(() => {
    if (scope === 'MONTHLY') return latestAvailableMonth;
    return activeSemesterUuid;
  }, [scope, latestAvailableMonth, activeSemesterUuid]);

  // Both start blank — the list of months/semesters loads asynchronously, so there's no
  // synchronous default to seed the state with. The render-time fallback below (`|| …`)
  // resolves to the newest month / active semester as soon as that data arrives.
  const [anchorMonthly, setAnchorMonthly] = useState('');
  const [anchorSemester, setAnchorSemester] = useState('');

  const anchor = scope === 'MONTHLY'
    ? (anchorMonthly || latestAvailableMonth)
    : (anchorSemester || activeSemesterUuid);
  const setAnchor = scope === 'MONTHLY' ? setAnchorMonthly : setAnchorSemester;

  const anchorOptions = scope === 'MONTHLY' ? monthOptions : semesterOptions;

  // ── Data ───────────────────────────────────────────────────────────────────
  const { stats, loading } = useReportStatistics({ scope, anchor: anchor || defaultAnchor });

  // ── Exportar PDF ───────────────────────────────────────────────────────────
  const [exporting, setExporting] = useState(false);

  async function handleExportPdf() {
    if (exporting) return;
    setExporting(true);
    try {
      const { blob, filename } = await downloadReservationReportPdf({ period: 'CURRENT_MONTH' });

      const url = URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      // Deferred cleanup: revoking synchronously after click() can invalidate the
      // objectURL before the browser's download subsystem opens the Blob stream
      // (intermittent 0-byte downloads in Chromium under load).
      setTimeout(() => {
        link.remove();
        URL.revokeObjectURL(url);
      }, 100);
    } catch (error) {
      toast.error(error.message);
    } finally {
      setExporting(false);
    }
  }

  // ── Derived values ─────────────────────────────────────────────────────────
  const ratePct = stats?.recurrenceRatePct ?? 0;
  const recurringCount = stats?.recurrence.recurring ?? 0;
  const oneTimeCount = stats?.recurrence.oneTime ?? 0;
  const paddingAngle = (recurringCount > 0 && oneTimeCount > 0) ? 2 : 0;

  const donutData = stats
    ? [
      { name: 'Recurrentes', value: recurringCount },
      { name: 'Eventuales', value: oneTimeCount },
    ]
    : [];

  return (
    <div className="reports-page">

      {/* ── Header ───────────────────────────────────────────────────────── */}
      <div className="reports-page__header">
        <div className="reports-page__header-text">
          <h1 className="reports-page__title">
            <BarChart3 size={22} className="reports-page__title-icon" />
            Reportes y Estadísticas
          </h1>
          <p className="reports-page__subtitle">
            Análisis detallado de ocupación, usuarios y tendencias de las aulas
          </p>
        </div>

        <button
          type="button"
          onClick={handleExportPdf}
          disabled={exporting}
          className="reports-page__export-btn"
          title="Descargar reporte PDF del mes en curso"
        >
          <Download size={15} />
          {exporting ? 'Generando…' : 'Exportar PDF'}
        </button>
      </div>

      {/* ── Info panel ───────────────────────────────────────────────────── */}
      <div className="reports-page__info-panel">
        <Info size={20} className="reports-page__info-icon" />
        <div className="reports-page__info-body">
          <p className="reports-page__info-title">Información del Reporte Actual</p>
          <p className="reports-page__info-text">
            Este panel muestra las métricas de uso de las instalaciones. Puedes identificar cuáles son las{' '}
            <strong>aulas más solicitadas</strong>, quiénes son los{' '}
            <strong>usuarios o departamentos que más reservan</strong>, y la proporción de eventos
            únicos frente a clases o eventos recurrentes. Utiliza el filtro de periodo para alternar
            entre la vista mensual y semestral.
          </p>
        </div>
      </div>

      {/* ── Period filter ─────────────────────────────────────────────────── */}
      <div className="reports-page__filters">
        <span className="reports-page__filter-label">
          <CalendarRange size={16} className="reports-page__filter-label-icon" />
          Periodo de análisis:
        </span>

        <div className="reports-page__scope-toggle" role="group" aria-label="Granularidad del periodo">
          {[
            { value: 'MONTHLY', label: 'Mensual' },
            { value: 'SEMESTER', label: 'Semestral' },
          ].map(opt => (
            <button
              key={opt.value}
              type="button"
              className={`reports-page__scope-btn${scope === opt.value ? ' reports-page__scope-btn--active' : ''}`}
              onClick={() => setScope(opt.value)}
              aria-pressed={scope === opt.value}
            >
              {opt.label}
            </button>
          ))}
        </div>

        <div className="reports-page__divider" aria-hidden />

        <Select
          value={anchor}
          onChange={setAnchor}
          options={anchorOptions}
          placeholder="Seleccionar periodo…"
          size="sm"
          className="reports-page__anchor-select"
          aria-label="Periodo específico"
        />
      </div>

      {/* ── KPI cards ─────────────────────────────────────────────────────── */}
      {!loading && stats && stats.totalReservations === 0 ? (
        <div style={{ marginTop: '24px', padding: '40px 0' }}>
          <EmptyState message="No hay datos de reservaciones para el periodo seleccionado." />
        </div>
      ) : (
        <>
          <div className="reports-page__kpi-grid">
            <StatCard
              icon={BookOpen}
              label="Total Reservaciones"
              value={stats ? stats.totalReservations.toLocaleString('es-MX') : undefined}
              delta={stats?.totalReservationsDeltaPct}
            />
            <StatCard
              icon={Building2}
              label="Aula Más Ocupada"
              value={stats ? (stats.mostOccupiedClassroom?.name ?? '—') : undefined}
              sub={stats?.mostOccupiedClassroom ? `${stats.mostOccupiedClassroom.hours} horas ocupadas` : undefined}
            />
            <StatCard
              icon={UserCheck}
              label="Mayor Usuario"
              value={stats ? (stats.topUser?.name ?? '—') : undefined}
              sub={stats?.topUser ? `${stats.topUser.reservations} reservaciones` : undefined}
            />
            <StatCard
              icon={RefreshCw}
              label="Tasa de Recurrencia"
              value={stats ? `${formatPct(stats.recurrenceRatePct)}%` : undefined}
              sub="de las reservas son recurrentes"
            />
          </div>

          {/* ── Charts grid ───────────────────────────────────────────────────── */}
          <div className="reports-page__charts-grid">

            {/* Aulas Más Ocupadas — BarChart vertical */}
            <ChartCard
              title="Aulas Más Ocupadas"
              subtitle="Horas totales de reservación por aula"
              loading={loading}
            >
              <ResponsiveContainer width="100%" height={250}>
                <BarChart data={stats?.mostOccupiedClassrooms ?? []} margin={{ top: 8, right: 8, left: -10, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" vertical={false} />
                  <XAxis dataKey="name" tick={{ fontSize: 11, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 11, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                  <Tooltip cursor={BAR_CURSOR} content={<SimpleTooltip unit=" h" />} />
                  <Bar dataKey="hours" radius={[4, 4, 0, 0]} maxBarSize={50}>
                    {(stats?.mostOccupiedClassrooms ?? []).map((_, i) => (
                      <Cell key={i} fill={COLOR_BARS[i % COLOR_BARS.length]} />
                    ))
                    }
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>

            {/* Usuarios con Más Reservas — BarChart horizontal */}
            <ChartCard
              title="Usuarios con Más Reservas"
              subtitle="Top organizadores o departamentos"
              loading={loading}
            >
              <ResponsiveContainer width="100%" height={250}>
                <BarChart
                  layout="vertical"
                  data={stats?.topUsers ?? []}
                  margin={{ top: 8, right: 24, left: 8, bottom: 0 }}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" horizontal={false} />
                  <XAxis type="number" tick={{ fontSize: 11, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                  <YAxis type="category" dataKey="name" width={90} tick={{ fontSize: 11, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                  <Tooltip cursor={BAR_CURSOR} content={<SimpleTooltip unit="" />} />
                  <Bar dataKey="reservations" radius={[0, 4, 4, 0]} maxBarSize={22}>
                    {(stats?.topUsers ?? []).map((_, i) => (
                      <Cell key={i} fill={i === 0 ? COLOR_PRIMARY : COLOR_LIGHT} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>

            {/* Recurrencia de Reservas — PieChart (donut) */}
            <ChartCard
              title="Recurrencia de Reservas"
              subtitle="Proporción de eventos recurrentes vs eventuales"
              loading={loading}
            >
              <div style={{ position: 'relative', width: '100%', height: 250 }}>
                <ResponsiveContainer width="100%" height={250}>
                  <PieChart>
                    <Pie
                      data={donutData}
                      cx="50%"
                      cy="46%"
                      innerRadius={65}
                      outerRadius={95}
                      dataKey="value"
                      startAngle={90}
                      endAngle={-270}
                      paddingAngle={paddingAngle}
                    >
                      <Cell fill={COLOR_REC} />
                      <Cell fill={COLOR_EVE} />
                    </Pie>
                    <Legend
                      iconType="circle"
                      iconSize={11}
                      wrapperStyle={{ fontSize: 12, color: '#6b7280', paddingTop: 4 }}
                    />
                    <Tooltip content={<SimpleTooltip unit=" reservas" />} />
                  </PieChart>
                </ResponsiveContainer>
                {/* Central label — rendered over the chart */}
                {stats && (
                  <div style={{
                    position: 'absolute', top: 0, left: 0, right: 0,
                    height: 'calc(100% - 40px)',   // subtract legend height
                    display: 'flex', flexDirection: 'column',
                    alignItems: 'center', justifyContent: 'center',
                    pointerEvents: 'none',
                  }}>
                    <span style={{ fontSize: 28, fontWeight: 700, color: '#111827', lineHeight: 1 }}>
                      {ratePct > 50 ? `${formatPct(ratePct)}%` : `${formatPct(100 - ratePct)}%`}
                    </span>
                    <span style={{ fontSize: 11, color: '#9ca3af', marginTop: 4 }}>
                      {ratePct > 50 ? 'Recurrentes' : 'Eventuales'}
                    </span>
                  </div>
                )}
              </div>
            </ChartCard>

            {/* Tendencia de Reservaciones — AreaChart */}
            <ChartCard
              title="Tendencia de Reservaciones"
              subtitle="Volumen de reservas a lo largo del tiempo"
              loading={loading}
            >
              <ResponsiveContainer width="100%" height={250}>
                <AreaChart
                  data={stats?.trend ?? []}
                  margin={{
                    top: 8, right: 8,
                    // SEMESTER rotates its labels -30° with textAnchor="end", which shifts
                    // the first tick left and every tick's descenders down — extra left/bottom
                    // margin keeps them from clipping against the chart edges.
                    left: scope === 'SEMESTER' ? 20 : -10,
                    bottom: scope === 'SEMESTER' ? 12 : 0,
                  }}
                >
                  <defs>
                    <linearGradient id="areaGrad" x1="0" y1="0" x2="0" y2="1">
                      <stop offset="5%" stopColor={COLOR_AREA} stopOpacity={0.3} />
                      <stop offset="95%" stopColor={COLOR_AREA} stopOpacity={0.03} />
                    </linearGradient>
                  </defs>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" vertical={false} />
                  <XAxis
                    dataKey="label"
                    tick={{ fontSize: 11, fill: '#6b7280' }}
                    axisLine={false}
                    tickLine={false}
                    // MONTHLY has 28-31 day labels — thin them out to start/end only.
                    // SEMESTER has ~6 month labels; interval={0} forces recharts to render
                    // every one instead of its collision heuristic skipping/duplicating ticks
                    // (the reported bug). Rotating them keeps them legible on narrower screens
                    // now that the collision-avoidance algorithm is disabled.
                    interval={scope === 'MONTHLY' ? 'preserveStartEnd' : 0}
                    angle={scope === 'SEMESTER' ? -30 : 0}
                    textAnchor={scope === 'SEMESTER' ? 'end' : 'middle'}
                    height={scope === 'SEMESTER' ? 42 : 30}
                  />
                  <YAxis tick={{ fontSize: 11, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                  <Tooltip content={<SimpleTooltip />} />
                  <Area
                    type="monotone"
                    dataKey="reservations"
                    stroke={COLOR_AREA}
                    strokeWidth={2}
                    fill="url(#areaGrad)"
                    dot={false}
                    activeDot={{ r: 4, strokeWidth: 0, fill: COLOR_AREA }}
                  />
                </AreaChart>
              </ResponsiveContainer>
            </ChartCard>

          </div>
        </>
      )}
    </div>
  );
}
