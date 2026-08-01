package mx.unam.icf.aulas.modules.reservations.instances.infrastructure;

import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.GlobalExceptionHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.PageCriteriaArgumentResolver;
import mx.unam.icf.aulas.modules.reservations.instances.app.ReservInstanceService;
import mx.unam.icf.aulas.modules.reservations.instances.app.dtos.ReservInstanceFilter;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservInstanceStatus;
import mx.unam.icf.aulas.modules.reservations.instances.domain.ReservationTimeframe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Binding-level slice test for {@code GET /api/v1/reservations}, covering the two request-facing
 * changes introduced alongside the "Finalizada" timeframe and the multi-value {@code status}
 * filter: repeating the {@code status} param must bind to a {@link List} (not just the last
 * occurrence), and {@code timeframe} must bind to {@link ReservationTimeframe}. An invalid value
 * on either enum param must still fail closed with a controlled 400 — never a 500 — exactly like
 * the existing {@code ReservInstanceControllerBindingTest} verifies for the booking endpoint.
 *
 * <p>The service is mocked; only the binding layer (query params → {@link ReservInstanceFilter})
 * is under test here.</p>
 */
@ExtendWith(MockitoExtension.class)
class ReservInstanceFindAllBindingTest {

    @Mock private ReservInstanceService service;
    @Mock private Environment           env;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        lenient().when(env.acceptsProfiles(org.springframework.core.env.Profiles.of("dev"))).thenReturn(false);

        ReservInstanceController controller = new ReservInstanceController(service);
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(env))
                .setCustomArgumentResolvers(new PageCriteriaArgumentResolver())
                .build();
    }

    @Test
    void repeatedStatusParam_bindsAsMultiValueList_inRequestOrder() throws Exception {
        when(service.findAll(any(ReservInstanceFilter.class), any(Pageable.class)))
                .thenReturn(PagedResultDTO.empty());

        mockMvc.perform(get("/api/v1/reservations")
                        .param("status", "CANCELLED_BY_USER")
                        .param("status", "CANCELLED_BY_ADMIN"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReservInstanceFilter> captor = ArgumentCaptor.forClass(ReservInstanceFilter.class);
        verify(service).findAll(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue().statuses())
                .containsExactly(ReservInstanceStatus.CANCELLED_BY_USER, ReservInstanceStatus.CANCELLED_BY_ADMIN);
    }

    @Test
    void timeframeParam_bindsToEnum() throws Exception {
        when(service.findAll(any(ReservInstanceFilter.class), any(Pageable.class)))
                .thenReturn(PagedResultDTO.empty());

        mockMvc.perform(get("/api/v1/reservations").param("timeframe", "PAST"))
                .andExpect(status().isOk());

        ArgumentCaptor<ReservInstanceFilter> captor = ArgumentCaptor.forClass(ReservInstanceFilter.class);
        verify(service).findAll(captor.capture(), any(Pageable.class));
        assertThat(captor.getValue().timeframe()).isEqualTo(ReservationTimeframe.PAST);
    }

    @Test
    void invalidStatusValue_returns400WithEnvelope_notServiceCall() throws Exception {
        mockMvc.perform(get("/api/v1/reservations").param("status", "GARBAGE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true));
    }

    @Test
    void invalidTimeframeValue_returns400WithEnvelope_notServiceCall() throws Exception {
        mockMvc.perform(get("/api/v1/reservations").param("timeframe", "GARBAGE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(true));
    }
}
