package mx.unam.icf.aulas.modules.reports.app;

import java.time.LocalDate;

/**
 * Defines the time-granularity of a statistics analysis period for the
 * "Reportes y Estadísticas" dashboard.
 *
 * <p>Each constant controls two behaviours:</p>
 * <ol>
 *   <li>How {@link StatisticsPeriodResolver} interprets the {@code anchor} query parameter
 *       to derive the {@code [from, to]} date range.</li>
 *   <li>How the trend series is bucketed: one entry per calendar day for {@link #MONTHLY},
 *       one entry per calendar month for {@link #SEMESTER}.</li>
 * </ol>
 *
 * <p>The constant names are also the public query-parameter contract of
 * {@code GET /api/v1/reports/statistics?scope=…}; renaming them is a breaking API change
 * that must be coordinated with the frontend ({@code src/schemas/report/reportScope.js}).</p>
 */
public enum StatisticsScope {

    /** Monthly granularity — anchor is a {@code yyyy-MM} string. */
    MONTHLY,

    /** Semester granularity — anchor is a semester UUID string. */
    SEMESTER;

    /**
     * Spanish-MX month abbreviations, indexed by {@code Month.getValue() - 1} (0-based).
     *
     * <p>A fixed array is used instead of {@code Month.getDisplayName(TextStyle.SHORT, locale)}
     * to avoid CLDR-version drift across JDK updates that produces lowercase forms with trailing
     * periods (e.g., {@code "ene."}) instead of the {@code "Ene"} expected by the frontend.
     * These are <em>display values</em>, deliberately kept in Spanish for the es-MX UI.</p>
     */
    private static final String[] MONTH_ABBR_ES =
            { "Ene", "Feb", "Mar", "Abr", "May", "Jun",
              "Jul", "Ago", "Sep", "Oct", "Nov", "Dic" };

    /**
     * Parses a raw {@code scope} query-parameter value into a {@link StatisticsScope} constant.
     *
     * <p>Matching is case-insensitive and trims surrounding whitespace, so {@code "monthly"},
     * {@code "MONTHLY"}, and {@code " Monthly "} all resolve to {@link #MONTHLY}.</p>
     *
     * @param raw the raw string value from the HTTP query parameter; may be {@code null}
     * @return the matching scope constant
     * @throws IllegalArgumentException with the message {@code "scope must be MONTHLY or SEMESTER"}
     *         if the value is {@code null}, blank, or does not match either constant
     */
    public static StatisticsScope parse(String raw) {
        if (raw != null) {
            String trimmed = raw.trim();
            for (StatisticsScope s : values()) {
                if (s.name().equalsIgnoreCase(trimmed)) {
                    return s;
                }
            }
        }
        throw new IllegalArgumentException("scope must be MONTHLY or SEMESTER");
    }

    /**
     * Returns the trend-series bucket label for the given date under this scope, without the
     * year disambiguation described in {@link #trendLabel(LocalDate, boolean)}.
     *
     * <ul>
     *   <li>{@link #MONTHLY} — zero-padded day-of-month: {@code "01"}, {@code "15"}, {@code "31"}.</li>
     *   <li>{@link #SEMESTER} — Spanish abbreviated month name: {@code "Ene"}, {@code "Ago"}.
     *       Labels are dynamic (derived from the actual semester dates), so a fall semester
     *       correctly produces {@code ["Ago","Sep","Oct","Nov","Dic","Ene"]} rather than a
     *       hard-coded Jan–Jun block.</li>
     * </ul>
     *
     * @param d any date that falls within the analysis period
     * @return the bucket label string expected by the frontend chart component
     */
    public String trendLabel(LocalDate d) {
        return trendLabel(d, false);
    }

    /**
     * Returns the trend-series bucket label for the given date, optionally disambiguated by year.
     *
     * <p>{@code includeYear} exists for {@link #SEMESTER} periods that span more than 12 months
     * (normally impossible for a well-formed semester, but not database-enforced until the
     * duration guard was added to semester creation/editing — see
     * {@link mx.unam.icf.aulas.modules.academic.semesters.app.SemesterService}). Without it, two
     * dates a year apart both label as {@code "Ene"} and their trend counts would collide into a
     * single bucket instead of being kept separate. When {@code true}, the label becomes
     * {@code "Ene 26"} (month + 2-digit year). {@link #MONTHLY} ignores this flag — a day-of-month
     * label is already unique within any single queried month.</p>
     *
     * @param d           any date that falls within the analysis period
     * @param includeYear whether to append a 2-digit year to disambiguate repeated month labels
     * @return the bucket label string expected by the frontend chart component
     */
    public String trendLabel(LocalDate d, boolean includeYear) {
        return switch (this) {
            case MONTHLY  -> String.format("%02d", d.getDayOfMonth());
            case SEMESTER -> includeYear
                    ? String.format("%s %02d", MONTH_ABBR_ES[d.getMonthValue() - 1], d.getYear() % 100)
                    : MONTH_ABBR_ES[d.getMonthValue() - 1];
        };
    }
}
