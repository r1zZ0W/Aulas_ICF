package mx.unam.icf.aulas.modules.reservations.history.infrastructure;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.PageCriteria;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.SortWhitelist;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.reservations.history.app.ReservationHistoryService;
import mx.unam.icf.aulas.modules.reservations.history.app.dtos.ReservationHistoryResponseDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Read-only REST controller for reservation change history.
 *
 * <p>All endpoints are restricted to the {@code ADMIN} role — history is an
 * internal audit tool, not a teacher-facing feature. History rows can never be
 * created, modified, or deleted through this API; the module is append-only by design.</p>
 *
 * <p>Both endpoints are paginated from day one (default: 10 records per page,
 * newest first) because history tables grow fastest in transactional systems and
 * returning unbounded lists is a denial-of-service risk as the data volume grows.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET /api/v1/reservations/history/instance/{instanceUuid}} — history for one occurrence.</li>
 *   <li>{@code GET /api/v1/reservations/history/group/{groupUuid}} — history across all occurrences of a group.</li>
 * </ul>
 *
 * @author Ithera
 * @version 1.0
 */
@RestController
@RequestMapping(value = "/api/v1/reservations/history", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ReservationHistoryController implements ResponseHandler {

    private final ReservationHistoryService service;

    /**
     * Returns the paginated change history for a specific reservation instance.
     *
     * @param instanceUuid public UUID of the target instance
     * @param criteria     validated pagination and sort parameters
     * @return paginated list of history records, newest first by default
     */
    @GetMapping("/instance/{instanceUuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResultDTO<ReservationHistoryResponseDTO>>> byInstance(
            @PathVariable UUID instanceUuid,
            @SortWhitelist(value = {"createdAt", "eventType"}, defaultSort = "createdAt", defaultDirection = "desc")
            PageCriteria criteria) {
        return ok(service.findByInstance(instanceUuid, criteria.toPageable()));
    }

    /**
     * Returns the paginated change history for all instances belonging to a reservation group.
     *
     * @param groupUuid public UUID of the target group
     * @param criteria  validated pagination and sort parameters
     * @return paginated list of history records, newest first by default
     */
    @GetMapping("/group/{groupUuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResultDTO<ReservationHistoryResponseDTO>>> byGroup(
            @PathVariable UUID groupUuid,
            @SortWhitelist(value = {"createdAt", "eventType"}, defaultSort = "createdAt", defaultDirection = "desc")
            PageCriteria criteria) {
        return ok(service.findByGroup(groupUuid, criteria.toPageable()));
    }
}
