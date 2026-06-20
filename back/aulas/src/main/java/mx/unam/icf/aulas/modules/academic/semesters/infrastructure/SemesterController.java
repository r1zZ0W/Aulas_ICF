package mx.unam.icf.aulas.modules.academic.semesters.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.academic.semesters.app.SemesterService;
import mx.unam.icf.aulas.modules.academic.semesters.app.dtos.SemesterRequestDTO;
import mx.unam.icf.aulas.modules.academic.semesters.app.dtos.SemesterResponseDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing the academic semester catalog.
 *
 * <p>Read operations are available to any authenticated user.
 * Write operations require the {@code ADMIN} role.
 * All endpoints are exposed under {@code /api/v1/semesters}.</p>
 *
 * <p>{@code GET /active} returns a <strong>single</strong> {@link SemesterResponseDTO}
 * representing the operationally current semester, or {@code 404} if no semester covers
 * today's date. Clients must handle both cases.</p>
 *
 * @author Ithera
 * @version 2.0
 */
@RestController
@RequestMapping(value = "/api/v1/semesters", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SemesterController implements ResponseHandler {

    private final SemesterService service;

    /**
     * Retrieves all semesters in the catalog with their derived {@code isActive} status.
     *
     * <p>GET /api/v1/semesters</p>
     *
     * @return list of all semesters
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SemesterResponseDTO>>> findAll() {
        return ok(service.findAll());
    }

    /**
     * Retrieves the single currently active semester.
     *
     * <p>GET /api/v1/semesters/active</p>
     *
     * <p>Returns a single {@link SemesterResponseDTO} (not a list) for the semester whose
     * {@code [startDate, endDate]} range contains today. If multiple overlapping semesters
     * exist (data anomaly), the one with the latest {@code endDate} is returned.
     * Returns {@code 404} when no semester covers the current date.</p>
     *
     * @return the active semester
     * @throws mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException
     *         when no semester is currently active
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<SemesterResponseDTO>> findActive() {
        return ok(service.findActive());
    }

    /**
     * Creates a new semester. Requires ADMIN role.
     *
     * <p>POST /api/v1/semesters</p>
     *
     * @param dto creation payload; dates must be ISO-8601 ({@code "yyyy-MM-dd"})
     * @return the created semester with HTTP 201
     * @throws mx.unam.icf.aulas.kernel.domain.exceptions.DomainException
     *         when the name is already taken or the date rules are violated
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SemesterResponseDTO>> save(
            @Valid @RequestBody SemesterRequestDTO dto) {
        return created(service.save(dto));
    }

    /**
     * Updates an existing semester. Requires ADMIN role.
     *
     * <p>PUT /api/v1/semesters/{uuid}</p>
     *
     * @param uuid the public UUID of the semester to update
     * @param dto  update payload; see {@link SemesterRequestDTO} for field rules
     * @return the updated semester
     * @throws mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException
     *         when the semester is not found
     * @throws mx.unam.icf.aulas.kernel.domain.exceptions.DomainException
     *         when the name is already taken or the date rules are violated
     */
    @PutMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SemesterResponseDTO>> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody SemesterRequestDTO dto) {
        return ok(service.update(uuid, dto));
    }
}
