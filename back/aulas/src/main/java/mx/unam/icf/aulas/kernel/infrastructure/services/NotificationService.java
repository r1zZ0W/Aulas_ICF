package mx.unam.icf.aulas.kernel.infrastructure.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.kernel.domain.events.reservations.cancellations.ReservInstanceCancelledEventDTO;
import mx.unam.icf.aulas.kernel.domain.events.reservations.creations.ReservInstanceCreatedEventDTO;
import mx.unam.icf.aulas.kernel.domain.events.reservations.reassigns.ReservInstanceReassignEventDTO;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Best-effort notification service that renders HTML email templates via Thymeleaf
 * and delegates delivery to {@link MailSender}.
 *
 * <p>All public methods are annotated with {@code @Async} so they execute on the
 * bounded {@code mail-} thread pool defined in
 * {@link mx.unam.icf.aulas.kernel.infrastructure.config.AsyncConfig}.
 * Callers (event listeners) return immediately after the call, keeping HTTP
 * response latency independent of SMTP performance.</p>
 *
 * <p>All methods are best-effort: SMTP and template failures are caught and logged at
 * WARN level; they never propagate back to the caller or affect the business
 * transaction (which has already committed by the time these methods run).</p>
 *
 * <p>Template location: {@code classpath:/templates/emails/&lt;name&gt;.html}.
 * Variables are resolved by Thymeleaf using {@code th:text}, {@code th:if}, etc.</p>
 *
 * @author Ithera
 * @version 2.0
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", new Locale("es", "MX"));
    private static final DateTimeFormatter TIME_FMT =
            DateTimeFormatter.ofPattern("HH:mm");

    private final MailSender     mailSender;
    private final TemplateEngine templateEngine;

    // ── DFR §3.1 — User registration ─────────────────────────────────────────

    /**
     * Sends login credentials to a newly registered user (DFR §3.1).
     *
     * @param email         institutional email of the new user
     * @param fullName      display name (firstName + lastNames)
     * @param matricula     auto-generated unique academic ID
     * @param username      login username
     * @param plainPassword plaintext password from the registration DTO (not yet erased)
     */
    public void notifyNewUserCredentials(String email, String fullName, String matricula,
                                         String username, String plainPassword) {
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
     * Notifies the owning teacher and all active administrators when a reservation
     * is successfully created (DFR §4.1).
     *
     * <p>Runs asynchronously on the {@code mail-} thread pool; failures are logged and
     * never propagated.</p>
     *
     * @param dto event payload carrying all information needed for both email templates
     */
    @Async
    public void notifyReservationCreated(ReservInstanceCreatedEventDTO dto) {
        try {
            String dateStr   = dto.date().format(DATE_FMT);
            String horario   = scheduleBuilder(dto.startTime(), dto.endTime());
            String idStr     = dto.reservationId().toString();

            // Notify the Maestro
            Context ctxUser = new Context();
            ctxUser.setVariable("maestroNombre", dto.maestroFullName());
            ctxUser.setVariable("aula",          dto.classroomName());
            ctxUser.setVariable("fecha",         dateStr);
            ctxUser.setVariable("horario",       horario);
            ctxUser.setVariable("idReserva",     idStr);
            ctxUser.setVariable("actividad",     "Reserva de aula");
            String bodyUser = templateEngine.process("emails/reservation-created-user", ctxUser);
            mailSender.sendHtml(
                    dto.maestroEmail(),
                    "Tu reserva ha sido confirmada — " + dto.classroomName() + " · " + dateStr,
                    bodyUser);

            // Notify each active admin
            Context ctxAdmin = new Context();
            ctxAdmin.setVariable("maestroNombre", dto.maestroFullName());
            ctxAdmin.setVariable("aula",          dto.classroomName());
            ctxAdmin.setVariable("fecha",         dateStr);
            ctxAdmin.setVariable("horario",       horario);
            ctxAdmin.setVariable("idReserva",     idStr);
            ctxAdmin.setVariable("actividad",     "Reserva de aula");
            String bodyAdmin = templateEngine.process("emails/reservation-created-admin", ctxAdmin);
            for (String adminEmail : dto.adminEmails()) {
                try {
                    mailSender.sendHtml(adminEmail,
                            "[Aulas ICF] Nueva reserva registrada — " + dateStr,
                            bodyAdmin);
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
     * Notifies the owning teacher and all active administrators when a reservation
     * is reassigned to a different classroom or time block (DFR §4.3).
     *
     * <p>Runs asynchronously on the {@code mail-} thread pool; failures are logged and
     * never propagated.</p>
     *
     * @param dto event payload carrying all information needed for both email templates
     */
    @Async
    public void notifyReservationReassigned(ReservInstanceReassignEventDTO dto) {
        try {
            String dateStr  = dto.date().format(DATE_FMT);
            String schedule  = scheduleBuilder(dto.startTime(), dto.endTime());
            String idStr    = dto.reservationId().toString();

            // Notify the Maestro
            Context ctxUser = new Context();
            ctxUser.setVariable("maestroNombre",  dto.teacherFullName());
            ctxUser.setVariable("aulaAnterior",   dto.oldClassroomName());
            ctxUser.setVariable("aulaNueva",      dto.newClassroomName());
            ctxUser.setVariable("fecha",          dateStr);
            ctxUser.setVariable("horario",        schedule);
            ctxUser.setVariable("idReserva",      idStr);
            String bodyUser = templateEngine.process("emails/reservation-reassigned-user", ctxUser);
            mailSender.sendHtml(
                    dto.teacherEmail(),
                    "[Aulas ICF] Tu reserva del " + dateStr + " ha sido reasignada",
                    bodyUser);

            // Notify each active admin
            Context ctxAdmin = new Context();
            ctxAdmin.setVariable("maestroNombre",  dto.teacherFullName());
            ctxAdmin.setVariable("aulaAnterior",   dto.oldClassroomName());
            ctxAdmin.setVariable("aulaNueva",      dto.newClassroomName());
            ctxAdmin.setVariable("fecha",          dateStr);
            ctxAdmin.setVariable("horario",        schedule);
            ctxAdmin.setVariable("idReserva",      idStr);
            String bodyAdmin = templateEngine.process("emails/reservation-reassigned-admin", ctxAdmin);
            for (String adminEmail : dto.adminEmails()) {
                try {
                    mailSender.sendHtml(adminEmail,
                            "[Aulas ICF] Reserva reasignada — " + dateStr,
                            bodyAdmin);
                } catch (Exception adminEx) {
                    log.warn("[NotificationService] Failed to notify admin {}: {}", adminEmail, adminEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[NotificationService] Failed to send reassignment notifications: {}", e.getMessage());
        }
    }

    // ── DFR §4.4 — Cancellation ───────────────────────────────────────────────

    /**
     * Notifies the owning teacher and all active administrators when a reservation
     * is cancelled (by the teacher or by an administrator).
     *
     * <p>Runs asynchronously on the {@code mail-} thread pool; failures are logged and
     * never propagated.</p>
     *
     * @param dto event payload carrying all information needed for both email templates
     */
    @Async
    public void notifyReservationCancelled(ReservInstanceCancelledEventDTO dto) {
        try {
            String dateStr      = dto.date().format(DATE_FMT);
            String horario      = scheduleBuilder(dto.startTime(), dto.endTime());
            String idStr        = dto.reservationId().toString();
            String canceladaPor = dto.cancelledByAdmin() ? "Administrador" : "Usuario";

            // Notify the Maestro
            Context ctxUser = new Context();
            ctxUser.setVariable("maestroNombre", dto.maestroFullName());
            ctxUser.setVariable("aula",          dto.classroomName());
            ctxUser.setVariable("fecha",         dateStr);
            ctxUser.setVariable("horario",       horario);
            ctxUser.setVariable("idReserva",     idStr);
            ctxUser.setVariable("actividad",     "Reserva de aula");
            ctxUser.setVariable("motivo",        dto.reason());
            String bodyUser = templateEngine.process("emails/reservation-cancelled-user", ctxUser);
            mailSender.sendHtml(
                    dto.maestroEmail(),
                    "[Aulas ICF] Tu reserva del " + dateStr + " ha sido cancelada",
                    bodyUser);

            // Notify each active admin
            Context ctxAdmin = new Context();
            ctxAdmin.setVariable("maestroNombre", dto.maestroFullName());
            ctxAdmin.setVariable("aula",          dto.classroomName());
            ctxAdmin.setVariable("fecha",         dateStr);
            ctxAdmin.setVariable("horario",       horario);
            ctxAdmin.setVariable("idReserva",     idStr);
            ctxAdmin.setVariable("actividad",     "Reserva de aula");
            ctxAdmin.setVariable("canceladaPor",  canceladaPor);
            String bodyAdmin = templateEngine.process("emails/reservation-cancelled-admin", ctxAdmin);
            for (String adminEmail : dto.adminEmails()) {
                try {
                    mailSender.sendHtml(adminEmail,
                            "[Aulas ICF] Reserva cancelada — " + dateStr,
                            bodyAdmin);
                } catch (Exception adminEx) {
                    log.warn("[NotificationService] Failed to notify admin {}: {}", adminEmail, adminEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[NotificationService] Failed to send cancellation notifications: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String scheduleBuilder(LocalTime start, LocalTime end) {
        if (start == null || end == null) return "—";
        return start.format(TIME_FMT) + " – " + end.format(TIME_FMT);
    }
}
