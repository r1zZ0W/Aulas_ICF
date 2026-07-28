/**
 * @fileoverview Client-side PDF export for the Reportes y Estadísticas dashboard.
 *
 * Captures a set of DOM nodes ("blocks") with html2canvas-pro and lays them out on an
 * A4 document with jsPDF, one block at a time.
 *
 * Deliberately NOT a single html2canvas() call over the whole dashboard container: a
 * monolithic canvas gets sliced blindly by page height, which cuts a chart or a KPI
 * card in half wherever the page boundary happens to fall. Capturing block-by-block
 * makes the chart/KPI-grid the smallest unit of insertion, so `addPage()` is only ever
 * called *between* blocks — a block is never split across two pages.
 *
 * Trade-off: the output is a rasterized image, not vector content. Text is not
 * selectable or searchable in the resulting PDF. That is the accepted cost of an exact
 * visual replica of the live .jsx view without re-implementing it server-side.
 */
import { jsPDF } from 'jspdf';
import html2canvas from 'html2canvas-pro';

// ── Layout constants (mm, A4 portrait) ──────────────────────────────────────────

const MARGIN = 12;
const GAP = 6;

/**
 * Captures each DOM node in `blocks` and lays them out on an A4 PDF, paginating by
 * measured block height so a block is never split across two pages, then triggers
 * the download.
 *
 * @param {object} params
 * @param {HTMLElement[]} params.blocks   DOM nodes to capture, in the order they should
 *                                        appear in the document.
 * @param {string} params.filename        Suggested filename for the downloaded PDF.
 * @param {(current: number, total: number) => void} [params.onProgress]
 *                                        Called before capturing each block (1-indexed).
 * @returns {Promise<void>}
 */
export async function exportBlocksToPdf({ blocks, filename, onProgress }) {
  const pdf = new jsPDF({ unit: 'mm', format: 'a4', orientation: 'portrait' });
  const pageW = pdf.internal.pageSize.getWidth();
  const pageH = pdf.internal.pageSize.getHeight();
  const usableW = pageW - 2 * MARGIN;
  const usableH = pageH - 2 * MARGIN;

  // Sharper output on high-DPI screens; floors at 2x even on 1x displays so the
  // rasterized text/lines stay legible when the PDF is zoomed.
  const scale = Math.max(2, window.devicePixelRatio || 1);

  let cursorY = MARGIN;

  for (let i = 0; i < blocks.length; i++) {
    const block = blocks[i];
    onProgress?.(i + 1, blocks.length);
    // Yield to the event loop before each heavy capture so the "Generando N/total"
    // label actually has a chance to paint instead of the UI freezing solid.
    await new Promise(resolve => requestAnimationFrame(resolve));

    const canvas = await html2canvas(block, {
      scale,
      backgroundColor: '#ffffff',
      logging: false,
    });

    let imgW = usableW;
    let imgH = (canvas.height * imgW) / canvas.width;
    let x = MARGIN;

    // Defensive guard: a block taller than one full page (shouldn't happen with the
    // current layout, but a future layout change could produce one) is shrunk to fit
    // the page height instead of silently overflowing past the bottom margin, and
    // centered horizontally. This also guarantees the invariant the page-break check
    // below relies on (imgH <= usableH), so a block placed at the top of a fresh page
    // (cursorY === MARGIN) can never itself trigger another page break.
    if (imgH > usableH) {
      imgH = usableH;
      imgW = (canvas.width * imgH) / canvas.height;
      x = MARGIN + (usableW - imgW) / 2;
    }

    if (cursorY + imgH > pageH - MARGIN) {
      pdf.addPage();
      cursorY = MARGIN;
    }

    pdf.addImage(canvas.toDataURL('image/png'), 'PNG', x, cursorY, imgW, imgH);
    cursorY += imgH + GAP;
  }

  pdf.save(filename);
}

// ── Filename helper ──────────────────────────────────────────────────────────────

const SCOPE_LABELS = {
  MONTHLY: 'mensual',
  SEMESTER: 'semestral',
};

/**
 * Builds a filesystem-safe filename for the exported report, identifying the analyzed
 * period rather than the generation date (the generation timestamp is printed inside
 * the PDF's cover block instead).
 *
 * @param {'MONTHLY'|'SEMESTER'} scope
 * @param {string} periodLabel  e.g. `'2026-07'` (MONTHLY anchor) or a semester display
 *                               name like `'2026-2'`.
 * @returns {string} e.g. `'reporte_estadisticas_mensual_2026-07.pdf'`
 */
export function buildReportFilename(scope, periodLabel) {
  const scopeSlug = SCOPE_LABELS[scope] ?? scope.toLowerCase();
  const periodSlug = (periodLabel ?? '')
    .normalize('NFD').replace(/[\u0300-\u036f]/g, '') // strip accents
    .toLowerCase()
    .replace(/\s+/g, '_')
    .replace(/[^a-z0-9_-]/g, '');

  return `reporte_estadisticas_${scopeSlug}_${periodSlug}.pdf`;
}
