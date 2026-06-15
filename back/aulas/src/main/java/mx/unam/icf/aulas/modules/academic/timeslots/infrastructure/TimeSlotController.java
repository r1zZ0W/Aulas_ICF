package mx.unam.icf.aulas.modules.academic.timeslots.infrastructure;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.academic.timeslots.app.TimeSlotService;
import mx.unam.icf.aulas.modules.academic.timeslots.app.dtos.TimeSlotDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing the pre-seeded time slot catalog.
 *
 * <p>Read-only; time slots are managed at the database level.
 * Available to any authenticated user for use in the reservation form.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/timeslots", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class TimeSlotController implements ResponseHandler {

    private final TimeSlotService service;

    /**
     * Retrieves all available time slots in chronological order.
     * GET /api/v1/timeslots
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<TimeSlotDTO>>> findAll() {
        return ok(service.findAll());
    }
}
