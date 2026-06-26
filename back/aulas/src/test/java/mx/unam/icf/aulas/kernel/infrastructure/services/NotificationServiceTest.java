package mx.unam.icf.aulas.kernel.infrastructure.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import mx.unam.icf.aulas.kernel.domain.events.reservations.creations.ReservInstanceCreatedEventDTO;
import mx.unam.icf.aulas.kernel.domain.events.reservations.cancellations.ReservInstanceCancelledEventDTO;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private MailSender mailSender;

    @Mock
    private TemplateEngine templateEngine;

    @InjectMocks
    private NotificationService notificationService;

    @Captor
    ArgumentCaptor<String> toCaptor;

    @Captor
    ArgumentCaptor<String> subjectCaptor;

    @Captor
    ArgumentCaptor<String> bodyCaptor;

    @Test
    void notifyNewUserCredentials_sendsHtmlWithCredentials() {
        String email = "profesor@unam.mx";
        String fullName = "Juan Pérez";
        String matricula = "A123456";
        String username = "jperez";
        String plainPassword = "temporal123";

        // Call
        notificationService.notifyNewUserCredentials(email, fullName, matricula, username, plainPassword);

        // Verify
        verify(mailSender, times(1)).sendHtml(toCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture());

        assertEquals(email, toCaptor.getValue());
        assertTrue(subjectCaptor.getValue().toLowerCase().contains("bienvenido"));
        String body = bodyCaptor.getValue();
        assertTrue(body.contains(fullName));
        assertTrue(body.contains(matricula));
        assertTrue(body.contains(username));
        assertTrue(body.contains(plainPassword));
    }

    @Test
    void notifyReservationCreated_sendsToTeacherAndAllAdmins_evenIfOneAdminFails() {
        // Arrange
        ReservInstanceCreatedEventDTO dto = new ReservInstanceCreatedEventDTO(
                "maestro@unam.mx",
                "Maestro Ejemplo",
                "Aula 101",
                LocalDate.of(2026, 6, 25),
                LocalTime.of(9, 0),
                LocalTime.of(11, 0),
                UUID.randomUUID(),
                List.of("admin1@unam.mx", "badadmin@unam.mx", "admin2@unam.mx")
        );

        when(templateEngine.process(eq("emails/reservation-created-user"), any(Context.class))).thenReturn("<user-body>");
        when(templateEngine.process(eq("emails/reservation-created-admin"), any(Context.class))).thenReturn("<admin-body>");

        // Simulate failure when sending to badadmin
        doThrow(new RuntimeException("SMTP failure"))
                .when(mailSender).sendHtml(eq("badadmin@unam.mx"), anyString(), anyString());

        // Act
        notificationService.notifyReservationCreated(dto);

        // Assert: total 4 sendHtml calls -> 1 teacher + 3 admins
        verify(mailSender, times(4)).sendHtml(toCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture());

        // Captured recipients include teacher and all admins
        List<String> recipients = toCaptor.getAllValues();
        assertTrue(recipients.contains("maestro@unam.mx"));
        assertTrue(recipients.contains("admin1@unam.mx"));
        assertTrue(recipients.contains("badadmin@unam.mx"));
        assertTrue(recipients.contains("admin2@unam.mx"));

        // Verify that template engine was invoked for both templates
        verify(templateEngine, times(1)).process(eq("emails/reservation-created-user"), any(Context.class));
        verify(templateEngine, times(1)).process(eq("emails/reservation-created-admin"), any(Context.class));
    }

    @Test
    void notifyReservationCancelled_handlesNullTimes_and_notifiesAll() {
        ReservInstanceCancelledEventDTO dto = new ReservInstanceCancelledEventDTO(
                "maestro@unam.mx",
                "Maestro Ejemplo",
                "Aula 202",
                LocalDate.of(2026, 6, 26),
                null,
                null,
                UUID.randomUUID(),
                false,
                null,
                List.of("admin@unam.mx")
        );

        when(templateEngine.process(eq("emails/reservation-cancelled-user"), any(Context.class))).thenReturn("<cancel-user>");
        when(templateEngine.process(eq("emails/reservation-cancelled-admin"), any(Context.class))).thenReturn("<cancel-admin>");

        notificationService.notifyReservationCancelled(dto);

        // Expect 2 emails: maestro + 1 admin
        verify(mailSender, times(2)).sendHtml(toCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture());

        List<String> recipients = toCaptor.getAllValues();
        assertTrue(recipients.contains("maestro@unam.mx"));
        assertTrue(recipients.contains("admin@unam.mx"));

        // Ensure templates were called
        verify(templateEngine).process(eq("emails/reservation-cancelled-user"), any(Context.class));
        verify(templateEngine).process(eq("emails/reservation-cancelled-admin"), any(Context.class));
    }
}

