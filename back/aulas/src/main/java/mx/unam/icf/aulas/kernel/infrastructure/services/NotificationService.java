package mx.unam.icf.aulas.kernel.infrastructure.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Best-effort notification service that wraps {@link MailSender} with try/catch guards.
 *
 * <p>Accepts only primitive/value-type arguments so this kernel-level service remains
 * free of module-level dependencies (kernel ← modules direction is preserved).</p>
 *
 * <p>All methods are best-effort: SMTP failures are logged at WARN level and never propagated.
 * A delivery failure must never roll back the underlying business transaction.</p>
 *
 * @author Ithera
 * @version 1.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "MX"));

    private final MailSender mailSender;

    // ── DFR §3.1 — User registration ─────────────────────────────────────────

    /**
     * Sends login credentials to a newly registered user (DFR §3.1).
     *
     * @param email        institutional email of the new user
     * @param fullName     display name (firstName + lastNames)
     * @param matricula    auto-generated unique academic ID
     * @param username     login username
     * @param departamento department/area (may be null)
     * @param plainPassword plaintext password from the registration DTO (not yet erased)
     */
    public void notifyNewUserCredentials(String email, String fullName, String matricula,
                                          String username, String departamento, String plainPassword) {
        try {
            mailSender.sendHtml(
                    email,
                    "Bienvenido/a al Sistema de Aulas ICF — Tus credenciales de acceso",
                    "<p>Estimado/a <strong>" + fullName + "</strong>,</p>"
                    + "<p>Tu cuenta en el <strong>Sistema de Reservas de Aulas ICF</strong> ha sido creada. "
                    + "A continuación encontrarás tus credenciales de acceso:</p>"
                    + "<table cellpadding='4'>"
                    + "<tr><td><strong>Matrícula:</strong></td><td>" + matricula + "</td></tr>"
                    + "<tr><td><strong>Usuario:</strong></td><td>" + username + "</td></tr>"
                    + "<tr><td><strong>Contraseña temporal:</strong></td><td>" + plainPassword + "</td></tr>"
                    + (departamento != null ? "<tr><td><strong>Departamento:</strong></td><td>" + departamento + "</td></tr>" : "")
                    + "</table>"
                    + "<p>Por seguridad, te recomendamos cambiar tu contraseña al iniciar sesión por primera vez "
                    + "desde el menú <em>Mi perfil</em>.</p>"
                    + "<p>Si tienes alguna duda, contacta al administrador del sistema.</p>"
            );
        } catch (Exception e) {
            log.warn("[NotificationService] Failed to send credentials email to {}: {}", email, e.getMessage());
        }
    }

    // ── DFR §4.1 — Reservation creation ──────────────────────────────────────

    /**
     * Notifies the owning Maestro and active administrators when a reservation is submitted (DFR §4.1).
     *
     * @param maestroEmail    email of the teacher who submitted the reservation
     * @param maestroFullName display name of the teacher
     * @param classroomName   name of the requested classroom
     * @param date            reservation date
     * @param adminEmails     list of active ADMIN email addresses to notify
     */
    public void notifyReservationCreated(String maestroEmail, String maestroFullName,
                                          String classroomName, LocalDate date,
                                          List<String> adminEmails) {
        try {
            String dateStr = date.format(DATE_FMT);
            String body = "<p>Se ha registrado una nueva solicitud de reserva:</p>"
                    + "<ul>"
                    + "<li><strong>Maestro/a:</strong> " + maestroFullName + "</li>"
                    + "<li><strong>Aula solicitada:</strong> " + classroomName + "</li>"
                    + "<li><strong>Fecha:</strong> " + dateStr + "</li>"
                    + "<li><strong>Estado:</strong> PENDIENTE (en espera de aprobación)</li>"
                    + "</ul>"
                    + "<p>Ingresa al sistema para revisar y aprobar o rechazar la solicitud.</p>";

            // Notify the Maestro
            mailSender.sendHtml(maestroEmail,
                    "Tu solicitud de reserva ha sido recibida — " + classroomName + " · " + dateStr, body);

            // Notify each active admin
            for (String adminEmail : adminEmails) {
                try {
                    mailSender.sendHtml(adminEmail,
                            "[Aulas ICF] Nueva solicitud de reserva pendiente — " + dateStr, body);
                } catch (Exception adminEx) {
                    log.warn("[NotificationService] Failed to notify admin {}: {}", adminEmail, adminEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[NotificationService] Failed to send reservation-created notifications: {}", e.getMessage());
        }
    }

    // ── DFR §4.3 — Reassignment ───────────────────────────────────────────────

    /**
     * Notifies the owning Maestro when their reservation is reassigned (DFR §4.3).
     *
     * @param maestroEmail     email of the teacher who owns the reservation
     * @param maestroFullName  display name of the teacher
     * @param date             reservation date
     * @param oldClassroomName name of the original classroom (before reassignment)
     * @param newClassroomName name of the new classroom (after reassignment)
     */
    public void notifyReservationReassigned(String maestroEmail, String maestroFullName,
                                             LocalDate date,
                                             String oldClassroomName, String newClassroomName) {
        try {
            String dateStr = date.format(DATE_FMT);
            mailSender.sendHtml(
                    maestroEmail,
                    "[Aulas ICF] Tu reserva del " + dateStr + " ha sido reasignada",
                    "<p>Estimado/a <strong>" + maestroFullName + "</strong>,</p>"
                    + "<p>El administrador ha realizado cambios en tu reserva del "
                    + "<strong>" + dateStr + "</strong>:</p>"
                    + "<ul>"
                    + "<li><strong>Aula anterior:</strong> " + oldClassroomName + "</li>"
                    + "<li><strong>Aula asignada:</strong> " + newClassroomName + "</li>"
                    + "</ul>"
                    + "<p>Ingresa al sistema para consultar el bloque de horario actualizado.</p>"
                    + "<p>Si tienes alguna duda, comunícate con el administrador.</p>"
            );
        } catch (Exception e) {
            log.warn("[NotificationService] Failed to send reassignment notification to {}: {}",
                    maestroEmail, e.getMessage());
        }
    }
}
