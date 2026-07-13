package mx.unam.icf.aulas.modules.reports.app;

/**
 * Defines the time period covered by a generated reservation report.
 *
 * <p>The constant names double as the public query-parameter contract of
 * {@code GET /api/v1/reports/reservations?period=…}; renaming a constant is a
 * breaking API change that must be coordinated with the frontend
 * ({@code src/api/reports.js}).</p>
 *
 * @author Ithera
 * @version 2.0
 */
public enum ReportPeriod {

    /** The calendar month immediately preceding the current month. */
    PREVIOUS_MONTH,

    /** The current calendar month up to today. */
    CURRENT_MONTH
}
