import { useState, useMemo, useRef, lazy, Suspense } from 'react';
import {
  BarChart3, Info, Download,
  BookOpen, Building2, UserCheck, RefreshCw,
  CalendarRange,
} from 'lucide-react';

import { useReportStatistics } from './hooks/useReportStatistics';
import { useAvailableMonths } from './hooks/useAvailableMonths';
import { toast } from '../../../utils/toast';
import Select from '../../../components/Select/Select';
import EmptyState from '../../../components/EmptyState/EmptyState';
import ErrorBanner from '../../../components/ErrorBanner/ErrorBanner';
import { useSemesters } from '../../shared/semesters/hooks/useSemesters';

import './ReportsPage.css';

// ── Dynamic import for heavy Recharts grid ────────────────────────────────────
const ReportCharts = lazy(() => import('./ReportCharts'));

const pctFormatter = new Intl.NumberFormat('es-MX', { maximumFractionDigits: 1 });
function formatPct(value) {
  return pctFormatter.format(value ?? 0);
}

/**
 * Formatea una cadena en formato "YYYY-MM" (ej. "2026-07")
 * a una etiqueta legible en español (ej. "Julio 2026").
 *
 * @param {string} monthStr - Cadena de fecha en formato "YYYY-MM"
 * @returns {string} Nombre del mes y año capitalizado, o la cadena original si el formato es inválido.
 */
function formatMonthLabel(monthStr) {
  if (!monthStr || typeof monthStr !== 'string') return '';
  const [yearStr, monthNumStr] = monthStr.split('-');
  const year = parseInt(yearStr, 10);
  const monthNum = parseInt(monthNumStr, 10);
  if (!yearStr || !monthNumStr || isNaN(year) || isNaN(monthNum) || monthNum < 1 || monthNum > 12) {
    return monthStr;
  }
  const date = new Date(year, monthNum - 1, 1);
  const monthName = date.toLocaleDateString('es-MX', { month: 'long' });
  return `${monthName.charAt(0).toUpperCase()}${monthName.slice(1)} ${year}`;
}

// ── Error formatting ────────────────────────────────────────────────────────────

function formatPdfErrorMessage(error) {
  if (!error) {
    return 'No se pudo generar el reporte en PDF. Por favor, inténtalo de nuevo.';
  }

  const msg = typeof error === 'string' ? error : (error.message || '');
  const name = typeof error === 'object' && error?.name ? error.name : '';

  if (msg.includes('NO_BLOCKS_FOUND'))
    return 'No se encontraron elementos o gráficos disponibles en el reporte para exportar.';

  if (msg.includes('CANVAS_CAPTURE_FAILED') || msg.includes('CANVAS_EMPTY'))
    return 'No se pudieron procesar los gráficos del reporte. Verifica que la página haya cargado completamente e inténtalo de nuevo.';

  if (msg.includes('PDF_ADD_IMAGE_FAILED'))
    return 'Ocurrió un error al componer el documento PDF con los gráficos visuales.';

  if (msg.includes('PDF_SAVE_FAILED'))
    return 'No se pudo guardar el archivo PDF. Asegúrate de que tu navegador permita descargas.';

  if (
    msg.includes('Failed to fetch dynamically imported module') ||
    msg.includes('Importing a module script failed') ||
    msg.includes('ChunkLoadError') ||
    name === 'ChunkLoadError'
  )
    return 'No se pudieron cargar los módulos de exportación a PDF. Comprueba tu conexión a internet e inténtalo de nuevo.';

  if (msg.includes('Tainted canvas') || msg.includes('SecurityError') || name === 'SecurityError')
    return 'No se pudieron exportar los gráficos debido a restricciones de seguridad de la página o imágenes externas.';

  if (
    msg.toLowerCase().includes('out of memory') ||
    msg.toLowerCase().includes('canvas area exceeds') ||
    msg.toLowerCase().includes('maximum size')
  )
    return 'El contenido del reporte excede los límites de memoria de la pantalla. Intenta ajustar el tamaño de la ventana o la escala visual.';

  if (msg.toLowerCase().includes('download') || msg.toLowerCase().includes('permission'))
    return 'El navegador ha bloqueado la descarga del archivo. Revisa los permisos de descargas.';

  if (
    msg &&
    !msg.includes('TypeError') &&
    !msg.includes('ReferenceError') &&
    !msg.includes('undefined') &&
    !msg.includes('null') &&
    !msg.includes('at ')
  )
    return msg;

  return 'Ocurrió un error al generar el reporte PDF. Por favor, inténtalo de nuevo o contacta al soporte técnico.';
}

// ── Subcomponents ──────────────────────────────────────────────────────────────

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

function ReportPdfCover({ scope, periodLabel, totalReservations }) {
  const generatedAt = new Date().toLocaleString('es-MX', {
    dateStyle: 'long',
    timeStyle: 'short',
  });

  return (
    <div className="reports-page__pdf-cover" data-pdf-block>
      <h2 className="reports-page__pdf-cover-title">Reportes y Estadísticas</h2>
      <p className="reports-page__pdf-cover-period">
        {scope === 'MONTHLY' ? 'Mensual' : 'Semestral'} — {periodLabel}
      </p>
      <p className="reports-page__pdf-cover-meta">
        Total de reservaciones en el periodo: {totalReservations.toLocaleString('es-MX')}
      </p>
      <p className="reports-page__pdf-cover-meta">
        Generado el {generatedAt}
      </p>
    </div>
  );
}

// ── Component ──────────────────────────────────────────────────────────────────

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

  const [anchorMonthly, setAnchorMonthly] = useState('');
  const [anchorSemester, setAnchorSemester] = useState('');

  const anchor = scope === 'MONTHLY'
    ? (anchorMonthly || latestAvailableMonth)
    : (anchorSemester || activeSemesterUuid);
  const setAnchor = scope === 'MONTHLY' ? setAnchorMonthly : setAnchorSemester;

  const anchorOptions = scope === 'MONTHLY' ? monthOptions : semesterOptions;

  // ── PDF export metadata ──────────────────────────────────────────────────────
  const periodLabel = scope === 'MONTHLY'
    ? (anchor ? formatMonthLabel(anchor) : '')
    : (semesterOptions.find(o => o.value === anchor)?.label ?? '');

  const periodSlugValue = scope === 'MONTHLY'
    ? anchor
    : (semestersList.find(s => s.uuid === anchor)?.name ?? anchor);

  // ── Data ───────────────────────────────────────────────────────────────────
  const { stats, loading, isFetching, isError, refetch } = useReportStatistics({ scope, anchor: anchor || defaultAnchor });

  // ── Exportar PDF ───────────────────────────────────────────────────────────
  const exportRootRef = useRef(null);
  const [exporting, setExporting] = useState(false);
  const [exportProgress, setExportProgress] = useState(null);

  const exportDisabled = exporting || loading || isFetching || !stats || stats.totalReservations === 0;

  async function handleExportPdf() {
    if (exportDisabled || !exportRootRef.current) return;

    const root = exportRootRef.current;
    setExporting(true);
    root.classList.add('reports-page--exporting');
    try {
      await new Promise(resolve => requestAnimationFrame(resolve));

      const { exportBlocksToPdf, buildReportFilename } = await import('./exportStatisticsPdf.js');
      const blocks = Array.from(root.querySelectorAll('[data-pdf-block]'));
      const filename = buildReportFilename(scope, periodSlugValue);

      await exportBlocksToPdf({
        blocks,
        filename,
        onProgress: (current, total) => setExportProgress({ current, total }),
      });
    } catch (error) {
      console.error('Error al exportar el reporte a PDF:', error);
      toast.error(formatPdfErrorMessage(error));
    } finally {
      root.classList.remove('reports-page--exporting');
      setExporting(false);
      setExportProgress(null);
    }
  }

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
          disabled={exportDisabled}
          className="reports-page__export-btn"
          title="Descargar el reporte del periodo seleccionado en PDF"
        >
          <Download size={15} />
          {exporting
            ? (exportProgress ? `Generando ${exportProgress.current}/${exportProgress.total}…` : 'Generando…')
            : 'Exportar PDF'}
        </button>
      </div>

      {isError && (
        <ErrorBanner
          message="No se pudieron cargar las estadísticas del periodo seleccionado."
          onDismiss={() => refetch()}
        />
      )}

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

      {/* ── Exportable region: cover (PDF-only) + KPI cards + charts ────────── */}
      <div ref={exportRootRef} className="reports-page__export-root">
        {stats && (
          <ReportPdfCover
            scope={scope}
            periodLabel={periodLabel}
            totalReservations={stats.totalReservations}
          />
        )}

        {!loading && stats && stats.totalReservations === 0 ? (
          <div style={{ marginTop: '24px', padding: '40px 0' }}>
            <EmptyState message="No hay datos de reservaciones para el periodo seleccionado." />
          </div>
        ) : (
          <>
            <div className="reports-page__kpi-grid" data-pdf-block>
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

            {/* ── Charts grid (Lazy loaded Recharts) ─────────────────────────────── */}
            <Suspense fallback={<div className="reports-page__skeleton reports-page__chart-skeleton" style={{ height: 500 }} />}>
              <ReportCharts stats={stats} loading={loading} scope={scope} />
            </Suspense>
          </>
        )}
      </div>
    </div>
  );
}
