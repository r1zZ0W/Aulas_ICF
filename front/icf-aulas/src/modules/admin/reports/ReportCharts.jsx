import {
  ResponsiveContainer,
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, Cell,
  PieChart, Pie, Legend,
  AreaChart, Area,
} from 'recharts';

const COLOR_PRIMARY = '#005687';
const COLOR_LIGHT = '#bfdbfe';
const COLOR_BARS = [COLOR_PRIMARY, '#1a6fa0', '#3388b8', '#4da0cf', '#66b3e0'];
const COLOR_REC = COLOR_PRIMARY;
const COLOR_EVE = '#94a3b8';
const COLOR_AREA = COLOR_PRIMARY;

const BAR_CURSOR = { fill: 'rgba(0, 0, 0, 0.04)' };

const pctFormatter = new Intl.NumberFormat('es-MX', { maximumFractionDigits: 1 });
function formatPct(value) {
  return pctFormatter.format(value ?? 0);
}

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

function ChartCard({ title, subtitle, loading, children }) {
  return (
    <div className="reports-page__chart-card chart-card" data-pdf-block>
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

export default function ReportCharts({ stats, loading, scope }) {
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
  );
}
