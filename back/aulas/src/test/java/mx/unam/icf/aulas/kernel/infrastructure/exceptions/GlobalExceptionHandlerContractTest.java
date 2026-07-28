package mx.unam.icf.aulas.kernel.infrastructure.exceptions;

import jakarta.validation.Valid;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode;
import mx.unam.icf.aulas.modules.access.users.app.dtos.RegisterRequestDTO;
import mx.unam.icf.aulas.modules.reservations.instances.app.exceptions.ReservationConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;

import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the {@code code}/{@code data} contract {@link GlobalExceptionHandler} now guarantees —
 * the shape the frontend's {@code resolveApiError}/{@code errorCatalog} depend on. Uses a
 * synthetic controller (not a real one) so each case is isolated to the handler's own mapping
 * logic, independent of any single module's wiring.
 */
@ExtendWith(MockitoExtension.class)
class GlobalExceptionHandlerContractTest {

    @RestController
    static class ProbeController {
        @PostMapping("/probe/register")
        void register(@Valid @RequestBody RegisterRequestDTO dto) { /* never reached when invalid */ }

        @GetMapping("/probe/domain-error")
        void domainError() {
            throw new DomainException(ErrorCode.USER_EMAIL_TAKEN, "Email is already registered");
        }

        @GetMapping("/probe/access-denied")
        void accessDenied() {
            throw new AccessDeniedException("You do not have permission to perform this action.");
        }

        @GetMapping("/probe/slot-conflict")
        void slotConflict() {
            throw new ReservationConflictException(LocalDate.of(2026, 1, 1), 3);
        }

        @GetMapping("/probe/db-constraint")
        void dbConstraint() {
            throw new DataIntegrityViolationException("duplicate key",
                    new SQLIntegrityConstraintViolationException(
                            "Duplicate entry '5-2026-01-01-3' for key 'reserv_slots.uk_reserv_slots_classroom_time'"));
        }
    }

    @Mock private Environment env;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // Handlers that branch on isDev() only affect 5xx message filtering, not the `code`
        // this test pins down — but they still call env.acceptsProfiles, which a bare mock
        // returns false for by default. lenient() avoids failing on stubs unused by a given test.
        lenient().when(env.acceptsProfiles(org.springframework.core.env.Profiles.of("dev"))).thenReturn(false);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new ProbeController())
                .setControllerAdvice(new GlobalExceptionHandler(env))
                .build();
    }

    private static final String VALID_REGISTER_JSON = """
            {
              "firstName": "Juan",
              "lastNames": "Perez Lopez",
              "username": "jperez123",
              "email": "juan@example.com",
              "password": "Abcdefg1!"
            }
            """;

    @Test
    void validationFailure_returns400WithFieldErrorCode() throws Exception {
        // email is well-formed but outside @icf.unam.mx — triggers the DOMAIN pattern,
        // not the generic FIELD_INVALID_FORMAT one, proving specific codes survive the mapping.
        mockMvc.perform(post("/probe/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_REGISTER_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.fieldErrors.email").value("USER_EMAIL_DOMAIN_INVALID"));
    }

    @Test
    void requiredFieldMissing_degradesToGenericFieldRequiredCode() throws Exception {
        // null (not "") so only @NotBlank fires — @Size treats null as valid (delegates the
        // null-check to @NotBlank/@NotNull), so this isolates the FIELD_REQUIRED code alone.
        String missingUsername = VALID_REGISTER_JSON.replace("\"username\": \"jperez123\",", "\"username\": null,");

        mockMvc.perform(post("/probe/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(missingUsername))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.fieldErrors.username").value("FIELD_REQUIRED"));
    }

    @Test
    void domainException_returnsItsOwnCode() throws Exception {
        mockMvc.perform(get("/probe/domain-error"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true))
                .andExpect(jsonPath("$.code").value("USER_EMAIL_TAKEN"));
    }

    @Test
    void preAuthorizeRejection_returns403WithAccessDeniedCode() throws Exception {
        mockMvc.perform(get("/probe/access-denied"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(true))
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void reservationConflict_returns409WithStructuredData() throws Exception {
        mockMvc.perform(get("/probe/slot-conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_SLOT_CONFLICT"))
                .andExpect(jsonPath("$.data.date").value("2026-01-01"))
                .andExpect(jsonPath("$.data.timeSlotId").value(3));
    }

    /**
     * Pins the MySQL {@code Duplicate entry '...' for key 'table.constraint'} parsing added to
     * {@code handleDataIntegrityViolation} — the piece most likely to silently stop matching if
     * a driver upgrade changes the message format, since a parse failure fails open (falls back
     * to the generic code) rather than throwing.
     */
    @Test
    void knownConstraintRace_returns409WithSpecificCode() throws Exception {
        mockMvc.perform(get("/probe/db-constraint"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_SLOT_CONFLICT"));
    }
}
