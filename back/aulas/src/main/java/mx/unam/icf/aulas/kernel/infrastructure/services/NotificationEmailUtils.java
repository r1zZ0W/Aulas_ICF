package mx.unam.icf.aulas.kernel.infrastructure.services;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Utility methods used by {@link NotificationService} to format notification data
 * and derive recipient lists for administrator emails.
 *
 * <p>This class is intentionally stateless and non-instantiable.</p>
 * @author Ithera
 * @version 1.0
 */
public final class NotificationEmailUtils {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es-MX"));
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private NotificationEmailUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Formats a reservation schedule using a fixed 24-hour time pattern.
     *
     * @param start start time of the reservation block
     * @param end end time of the reservation block
     * @return a human-readable schedule string, or an em dash when one of the
     *         values is missing
     */
    public static String formatSchedule(LocalTime start, LocalTime end) {
        if (start == null || end == null) {
            return "—";
        }
        return start.format(TIME_FMT) + " – " + end.format(TIME_FMT);
    }

    /**
     * Formats a reservation date using a Spanish (Mexico) long-date pattern.
     *
     * @param date reservation date to format
     * @return a human-readable date string, or an empty string when the input is
     *         {@code null}
     */
    public static String formatDate(LocalDate date) {
        return date == null ? "" : date.format(DATE_FMT);
    }

    /**
     * Resolves the primary recipient for administrator notifications.
     *
     * <p>The configured super-admin mailbox takes priority. If it is blank or
     * missing, the first non-blank address from the provided list is used as a
     * fallback.</p>
     *
     * @param superAdminEmail configured mailbox for the main administrator inbox
     * @param adminEmails list of available administrator email addresses
     * @return the primary recipient address, or {@code null} when no valid address
     *         is available
     */
    public static String resolvePrimaryAdminRecipient(String superAdminEmail, List<String> adminEmails) {
        if (superAdminEmail != null && !superAdminEmail.isBlank()) {
            return superAdminEmail.trim();
        }

        if (adminEmails == null) {
            return null;
        }

        return adminEmails.stream()
                .filter(email -> email != null && !email.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    /**
     * Builds a sanitized CC list for administrator notifications.
     *
     * <p>The primary recipient is excluded, blank values are ignored, and the
     * remaining addresses are deduplicated while preserving their original order.</p>
     *
     * @param primaryRecipient primary recipient already placed in the {@code To} field
     * @param adminEmails raw list of administrator email addresses
     * @return a list of CC recipients ready to pass to the mail sender
     */
    public static List<String> buildCcRecipients(String primaryRecipient, List<String> adminEmails) {
        if (adminEmails == null || adminEmails.isEmpty()) {
            return List.of();
        }

        List<String> ccRecipients = new ArrayList<>();
        String normalizedPrimary = primaryRecipient == null ? "" : primaryRecipient.trim().toLowerCase(Locale.ROOT);

        for (String adminEmail : adminEmails) {
            if (adminEmail == null || adminEmail.isBlank()) {
                continue;
            }

            String normalizedAdmin = adminEmail.trim();
            boolean isPrimary = normalizedAdmin.toLowerCase(Locale.ROOT).equals(normalizedPrimary);
            if (!isPrimary && !containsIgnoreCase(ccRecipients, normalizedAdmin)) {
                ccRecipients.add(normalizedAdmin);
            }
        }

        return ccRecipients;
    }

    /**
     * Checks whether a list already contains an email address, ignoring case.
     *
     * @param list collection to inspect
     * @param target email address to search for
     * @return {@code true} when the list already contains the target address;
     *         otherwise {@code false}
     */
    private static boolean containsIgnoreCase(List<String> list, String target) {
        return list.stream().anyMatch(element -> element.equalsIgnoreCase(target));
    }
}

