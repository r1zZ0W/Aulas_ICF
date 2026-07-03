package mx.unam.icf.aulas.kernel.infrastructure.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mx.unam.icf.aulas.kernel.domain.events.reservations.cancellations.ReservInstanceCancelledEventDTO;
import mx.unam.icf.aulas.kernel.domain.events.reservations.creations.ReservInstanceCreatedEventDTO;
import mx.unam.icf.aulas.kernel.domain.events.reservations.reassigns.ReservInstanceReassignEventDTO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.List;

/**
 * Best-effort notification service that renders HTML email templates via Thymeleaf
 * and delegates delivery to {@link MailSender}.
 *
 * <p>The class focuses on orchestration only: it prepares template variables,
 * renders the HTML bodies, and delegates recipient/date/time helper work to
 * {@link NotificationEmailUtils}.</p>
 *
 * <p>All public reservation-notification methods are annotated with {@code @Async}
 * so they execute on the bounded {@code mail-} thread pool defined in
 * {@link mx.unam.icf.aulas.kernel.infrastructure.config.AsyncConfig}. Callers
 * return immediately, keeping HTTP latency independent of SMTP performance.</p>
 *
 * <p>Failures are best-effort: template and SMTP issues are caught and logged at
 * WARN level, and they never propagate back to the business transaction.</p>
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

    private final MailSender     mailSender;
    private final TemplateEngine templateEngine;

    @Value("${app.notifications.super-admin-email:${app.seed.admin-email:}}")
    private String superAdminEmail;

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
            String dateStr   = NotificationEmailUtils.formatDate(dto.date());
            String horario   = NotificationEmailUtils.formatSchedule(dto.startTime(), dto.endTime());
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
            sendAdminNotification(
                    "Sistema de reserva de aulas — Nueva reserva registrada — " + dateStr,
                    bodyAdmin,
                    dto.adminEmails()
            );
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
            String dateStr  = NotificationEmailUtils.formatDate(dto.date());
            String schedule  = NotificationEmailUtils.formatSchedule(dto.startTime(), dto.endTime());
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
                    "Sistema de reserva de aulas — Tu reserva del " + dateStr + " ha sido reasignada",
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
            sendAdminNotification(
                    "Sistema de reserva de aulas — Reserva reasignada — " + dateStr,
                    bodyAdmin,
                    dto.adminEmails()
            );
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
            String dateStr      = NotificationEmailUtils.formatDate(dto.date());
            String horario      = NotificationEmailUtils.formatSchedule(dto.startTime(), dto.endTime());
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
                    "Sistema de reserva de aulas — Tu reserva del " + dateStr + " ha sido cancelada",
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
            sendAdminNotification(
                    "Sistema de reserva de aulas — Reserva cancelada — " + dateStr,
                    bodyAdmin,
                    dto.adminEmails()
            );
        } catch (Exception e) {
            log.warn("[NotificationService] Failed to send cancellation notifications: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Dispatches a single consolidated HTML notification to the administrator group.
     *
     * <p>The configured super-admin mailbox is used as the primary recipient and the
     * remaining administrator addresses are added as CC recipients.</p>
     *
     * @param subject email subject line
     * @param body pre-rendered HTML content body
     * @param adminEmails list of administrator email addresses from the event payload
     */
    private void sendAdminNotification(String subject, String body, List<String> adminEmails) {
        String primaryRecipient = NotificationEmailUtils.resolvePrimaryAdminRecipient(superAdminEmail, adminEmails);
        if (primaryRecipient == null || primaryRecipient.isBlank()) {
            log.warn("[NotificationService] No admin recipient available to send notification: {}", subject);
            return;
        }

        List<String> bccRecipients = NotificationEmailUtils.buildCcRecipients(primaryRecipient, adminEmails);
        mailSender.sendHtml(primaryRecipient, subject, body, bccRecipients);
    }

}
