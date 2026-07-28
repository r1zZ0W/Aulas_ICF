package mx.unam.icf.aulas.modules.reservations.instances.infrastructure;

import mx.unam.icf.aulas.kernel.infrastructure.exceptions.GlobalExceptionHandler;
import mx.unam.icf.aulas.modules.reservations.instances.app.ReservInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Binding-level slice test for the multipart {@code POST /api/v1/reservations/booking}.
 *
 * <p>This test exists to <em>pin down empirically</em> which exception Spring's multipart
 * binding raises in each failure mode — instead of assuming it — and to prove that
 * {@link GlobalExceptionHandler} maps every one of them to a controlled 400 with the
 * standard {@code ApiResponse} envelope, never falling through to the 500 catch-all:</p>
 * <ul>
 *   <li>a {@code data} part that violates a Bean Validation constraint
 *       ({@code MethodArgumentNotValidException} or {@code HandlerMethodValidationException}
 *       depending on the framework's binding path — both are handled),</li>
 *   <li>a {@code data} part with malformed JSON ({@code HttpMessageNotReadableException}),</li>
 *   <li>a missing {@code file} part ({@code MissingServletRequestPartException}).</li>
 * </ul>
 *
 * <p>The service is mocked and must never be reached: every case fails during binding,
 * before the controller body executes. {@code @PreAuthorize} is out of scope here
 * (standalone setup), matching {@code ReportControllerTest}'s approach.</p>
 */
@ExtendWith(MockitoExtension.class)
class ReservInstanceControllerBindingTest {

    private static final String BOOKING_URL = "/api/v1/reservations/booking";

    /** A structurally valid data part (all constraints satisfied). */
    private static final String VALID_DATA_JSON = """
            {
              "classroomUuid": "11111111-1111-1111-1111-111111111111",
              "attendeeCount": 25,
              "timeSlotIds": [1, 2],
              "startDate": "2099-01-04",
              "title": "Programación I"
            }
            """;

    @Mock private ReservInstanceService service;
    @Mock private Environment           env;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        // The handlers consult isDev() (env.acceptsProfiles); unstubbed it's fine since a bare
        // mock returns false for boolean methods, but lenient() keeps this test independent
        // of that default if a case here ever needs isDev() to be true.
        lenient().when(env.acceptsProfiles(org.springframework.core.env.Profiles.of("dev"))).thenReturn(false);

        ReservInstanceController controller = new ReservInstanceController(service);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(env))
                .build();
    }

    private MockMultipartFile dataPart(String json) {
        return new MockMultipartFile(
                "data", "", "application/json", json.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile filePart() {
        return new MockMultipartFile(
                "file", "roster.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{0x50, 0x4B, 0x03, 0x04, 0x00});
    }

    // ── (a) invalid DTO field → 400, not 500 ─────────────────────────────────

    @Test
    void booking_invalidDtoField_returns400WithEnvelope() throws Exception {
        String invalid = VALID_DATA_JSON.replace("\"attendeeCount\": 25", "\"attendeeCount\": -1");

        mockMvc.perform(multipart(BOOKING_URL)
                        .file(dataPart(invalid))
                        .file(filePart()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true));

        verifyNoInteractions(service);
    }

    // ── (b) malformed JSON in the data part → 400, not 500 ──────────────────

    @Test
    void booking_malformedJsonDataPart_returns400WithEnvelope() throws Exception {
        mockMvc.perform(multipart(BOOKING_URL)
                        .file(dataPart("{ this is not json"))
                        .file(filePart()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true));

        verifyNoInteractions(service);
    }

    // ── (c) missing file part → 400, not 500 ────────────────────────────────

    @Test
    void booking_missingFilePart_returns400WithEnvelope() throws Exception {
        mockMvc.perform(multipart(BOOKING_URL)
                        .file(dataPart(VALID_DATA_JSON)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true));

        verifyNoInteractions(service);
    }
}
