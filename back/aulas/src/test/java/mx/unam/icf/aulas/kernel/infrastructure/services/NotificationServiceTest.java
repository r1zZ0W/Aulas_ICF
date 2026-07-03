package mx.unam.icf.aulas.kernel.infrastructure.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.springframework.test.util.ReflectionTestUtils;
import mx.unam.icf.aulas.kernel.domain.events.reservations.creations.ReservInstanceCreatedEventDTO;
import mx.unam.icf.aulas.kernel.domain.events.reservations.cancellations.ReservInstanceCancelledEventDTO;
import mx.unam.icf.aulas.kernel.domain.events.reservations.reassigns.ReservInstanceReassignEventDTO;

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

    @Captor
    ArgumentCaptor<List<String>> ccCaptor;

    private static final String SUPER_ADMIN_EMAIL = "superadmin@unam.mx";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(notificationService, "superAdminEmail", SUPER_ADMIN_EMAIL);
    }

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

        // Act
        notificationService.notifyReservationCreated(dto);

        // Assert: 1 teacher email + 1 admin email with CC
        verify(mailSender).sendHtml(eq("maestro@unam.mx"), anyString(), anyString());
        verify(mailSender).sendHtml(toCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture(), ccCaptor.capture());

        assertEquals(SUPER_ADMIN_EMAIL, toCaptor.getValue());
        assertEquals(List.of("admin1@unam.mx", "badadmin@unam.mx", "admin2@unam.mx"), ccCaptor.getValue());

        // Verify that template engine was invoked for both templates
        verify(templateEngine, times(1)).process(eq("emails/reservation-created-user"), any(Context.class));
        verify(templateEngine, times(1)).process(eq("emails/reservation-created-admin"), any(Context.class));
    }

    @Test
    void notifyReservationReassigned_sendsToTeacherAndSuperAdminWithCc() {
        ReservInstanceReassignEventDTO dto = new ReservInstanceReassignEventDTO(
                "maestro@unam.mx",
                "Maestro Ejemplo",
                LocalDate.of(2026, 6, 27),
                "Aula 101",
                "Aula 202",
                LocalTime.of(12, 0),
                LocalTime.of(14, 0),
                UUID.randomUUID(),
                List.of("admin1@unam.mx", "admin2@unam.mx")
        );

        when(templateEngine.process(eq("emails/reservation-reassigned-user"), any(Context.class))).thenReturn("<user-body>");
        when(templateEngine.process(eq("emails/reservation-reassigned-admin"), any(Context.class))).thenReturn("<admin-body>");

        notificationService.notifyReservationReassigned(dto);

        verify(mailSender).sendHtml(eq("maestro@unam.mx"), anyString(), anyString());
        verify(mailSender).sendHtml(toCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture(), ccCaptor.capture());

        assertEquals(SUPER_ADMIN_EMAIL, toCaptor.getValue());
        assertEquals(List.of("admin1@unam.mx", "admin2@unam.mx"), ccCaptor.getValue());
        verify(templateEngine).process(eq("emails/reservation-reassigned-user"), any(Context.class));
        verify(templateEngine).process(eq("emails/reservation-reassigned-admin"), any(Context.class));
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

        // Expect 2 emails: maestro + 1 admin mailbox with empty CC
        verify(mailSender).sendHtml(eq("maestro@unam.mx"), anyString(), anyString());
        verify(mailSender).sendHtml(toCaptor.capture(), subjectCaptor.capture(), bodyCaptor.capture(), ccCaptor.capture());

        assertEquals(SUPER_ADMIN_EMAIL, toCaptor.getValue());
        assertEquals(List.of("admin@unam.mx"), ccCaptor.getValue());

        // Ensure templates were called
        verify(templateEngine).process(eq("emails/reservation-cancelled-user"), any(Context.class));
        verify(templateEngine).process(eq("emails/reservation-cancelled-admin"), any(Context.class));
    }
}

