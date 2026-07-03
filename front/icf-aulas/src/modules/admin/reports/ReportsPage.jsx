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
import { buildPdfReportUrl } from '../../../api/reports';
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

// ── Donut center label (recharts renderCustomizedLabel) ────────────────────────

function DonutCenterLabel({ cx, cy, pct }) {
  return (
    <g>
      <text x={cx} y={cy - 8} textAnchor="middle" dominantBaseline="central"
        style={{ fontSize: 26, fontWeight: 700, fill: '#111827' }}>
        {pct}%
      </text>
      <text x={cx} y={cy + 18} textAnchor="middle" dominantBaseline="central"
        style={{ fontSize: 11, fill: '#9ca3af' }}>
        Recurrentes
      </text>
    </g>
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
  const [scope, setScope] = useState('MENSUAL');
  const { semesters: semestersList } = useSemesters();
  const { months: availableMonths } = useAvailableMonths();

  const monthOptions = useMemo(() => {
    return availableMonths.map(m => ({ value: m, label: formatMonthLabel(m) }));
  }, [availableMonths]);

  // Months come back newest-first (see useAvailableMonths/getAvailableMonths).
  const latestAvailableMonth = availableMonths[0] ?? '';

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
    if (scope === 'MENSUAL') return latestAvailableMonth;
    return activeSemesterUuid;
  }, [scope, latestAvailableMonth, activeSemesterUuid]);

  // Both start blank — the list of months/semesters loads asynchronously, so there's no
  // synchronous default to seed the state with. The render-time fallback below (`|| …`)
  // resolves to the newest month / active semester as soon as that data arrives.
  const [anchorMensual, setAnchorMensual] = useState('');
  const [anchorSemestral, setAnchorSemestral] = useState('');

  const anchor = scope === 'MENSUAL'
    ? (anchorMensual || latestAvailableMonth)
    : (anchorSemestral || activeSemesterUuid);
  const setAnchor = scope === 'MENSUAL' ? setAnchorMensual : setAnchorSemestral;

  const anchorOptions = scope === 'MENSUAL' ? monthOptions : semesterOptions;

  // ── Data ───────────────────────────────────────────────────────────────────
  const { stats, loading } = useReportStatistics({ scope, anchor: anchor || defaultAnchor });

  // ── Exportar PDF ───────────────────────────────────────────────────────────
  const pdfUrl = buildPdfReportUrl({ period: 'MES_EN_CURSO' });

  // ── Derived values ─────────────────────────────────────────────────────────
  const tasaPct = stats?.tasaRecurrenciaPct ?? 0;
  const recurrentes = stats?.recurrencia.recurrentes ?? 0;
  const eventuales = stats?.recurrencia.eventuales ?? 0;
  const paddingAngle = (recurrentes > 0 && eventuales > 0) ? 2 : 0;

  const donutData = stats
    ? [
      { name: 'Recurrentes', value: recurrentes },
      { name: 'Eventuales', value: eventuales },
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

        <a
          href={pdfUrl}
          target="_blank"
          rel="noopener noreferrer"
          className="reports-page__export-btn"
          title="Descargar reporte PDF del mes en curso"
        >
          <Download size={15} />
          Exportar PDF
        </a>
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
            { value: 'MENSUAL', label: 'Mensual' },
            { value: 'SEMESTRAL', label: 'Semestral' },
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
      {!loading && stats && stats.totalReservas === 0 ? (
        <div style={{ marginTop: '24px', padding: '40px 0' }}>
          <EmptyState message="No hay datos de reservaciones para el periodo seleccionado." />
        </div>
      ) : (
        <>
          <div className="reports-page__kpi-grid">
            <StatCard
              icon={BookOpen}
              label="Total Reservaciones"
              value={stats ? stats.totalReservas.toLocaleString('es-MX') : undefined}
              delta={stats?.totalReservasDeltaPct}
            />
            <StatCard
              icon={Building2}
              label="Aula Más Ocupada"
              value={stats ? (stats.aulaMasOcupada?.nombre ?? '—') : undefined}
              sub={stats?.aulaMasOcupada ? `${stats.aulaMasOcupada.horas} horas ocupadas` : undefined}
            />
            <StatCard
              icon={UserCheck}
              label="Mayor Usuario"
              value={stats ? (stats.mayorUsuario?.nombre ?? '—') : undefined}
              sub={stats?.mayorUsuario ? `${stats.mayorUsuario.reservas} reservaciones` : undefined}
            />
            <StatCard
              icon={RefreshCw}
              label="Tasa de Recurrencia"
              value={stats ? `${formatPct(stats.tasaRecurrenciaPct)}%` : undefined}
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
                <BarChart data={stats?.aulasMasOcupadas ?? []} margin={{ top: 8, right: 8, left: -10, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" vertical={false} />
                  <XAxis dataKey="nombre" tick={{ fontSize: 11, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                  <YAxis tick={{ fontSize: 11, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                  <Tooltip cursor={BAR_CURSOR} content={<SimpleTooltip unit=" h" />} />
                  <Bar dataKey="horas" radius={[4, 4, 0, 0]} maxBarSize={50}>
                    {(stats?.aulasMasOcupadas ?? []).map((_, i) => (
                      <Cell key={i} fill={COLOR_BARS[i % COLOR_BARS.length]} />
                    ))}
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
                  data={stats?.usuariosMasReservas ?? []}
                  margin={{ top: 8, right: 24, left: 8, bottom: 0 }}
                >
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" horizontal={false} />
                  <XAxis type="number" tick={{ fontSize: 11, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                  <YAxis type="category" dataKey="nombre" width={90} tick={{ fontSize: 11, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                  <Tooltip cursor={BAR_CURSOR} content={<SimpleTooltip unit="" />} />
                  <Bar dataKey="reservas" radius={[0, 4, 4, 0]} maxBarSize={22}>
                    {(stats?.usuariosMasReservas ?? []).map((_, i) => (
                      <Cell key={i} fill={i === 0 ? COLOR_PRIMARY : COLOR_LIGHT} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </ChartCard>

            {/* Recurrencia de Reservas — PieChart (donut) */}
            <ChartCard
              title="Recurrencia de Reservas"
              subtitle="Proporción de eventos recurrentes (ej. clases) vs eventuales"
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
                      {tasaPct > 50 ? `${formatPct(tasaPct)}%` : `${formatPct(100 - tasaPct)}%`}
                    </span>
                    <span style={{ fontSize: 11, color: '#9ca3af', marginTop: 4 }}>
                      {tasaPct > 50 ? 'Recurrentes' : 'Eventuales'}
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
                  data={stats?.tendencia ?? []}
                  margin={{
                    top: 8, right: 8,
                    // SEMESTRAL rotates its labels -30° with textAnchor="end", which shifts
                    // the first tick left and every tick's descenders down — extra left/bottom
                    // margin keeps them from clipping against the chart edges.
                    left: scope === 'SEMESTRAL' ? 20 : -10,
                    bottom: scope === 'SEMESTRAL' ? 12 : 0,
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
                    // MENSUAL has 28-31 day labels — thin them out to start/end only.
                    // SEMESTRAL has ~6 month labels; interval={0} forces recharts to render
                    // every one instead of its collision heuristic skipping/duplicating ticks
                    // (the reported bug). Rotating them keeps them legible on narrower screens
                    // now that the collision-avoidance algorithm is disabled.
                    interval={scope === 'MENSUAL' ? 'preserveStartEnd' : 0}
                    angle={scope === 'SEMESTRAL' ? -30 : 0}
                    textAnchor={scope === 'SEMESTRAL' ? 'end' : 'middle'}
                    height={scope === 'SEMESTRAL' ? 42 : 30}
                  />
                  <YAxis tick={{ fontSize: 11, fill: '#6b7280' }} axisLine={false} tickLine={false} />
                  <Tooltip content={<SimpleTooltip />} />
                  <Area
                    type="monotone"
                    dataKey="reservas"
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
