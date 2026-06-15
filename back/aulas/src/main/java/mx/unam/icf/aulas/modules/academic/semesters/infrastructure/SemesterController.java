package mx.unam.icf.aulas.modules.academic.semesters.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.academic.semesters.app.SemesterService;
import mx.unam.icf.aulas.modules.academic.semesters.app.dtos.SemesterDTO;
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

/**
 * REST controller for managing the academic semester catalog.
 *
 * <p>Read operations are available to any authenticated user.
 * Write operations require the {@code ADMIN} role.
 * All endpoints are exposed under {@code /api/v1/semesters}.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/semesters", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class SemesterController implements ResponseHandler {

    private final SemesterService service;

    /**
     * Retrieves all semesters in the catalog.
     * GET /api/v1/semesters
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SemesterDTO>>> findAll() {
        return ok(service.findAll());
    }

    /**
     * Retrieves all currently active semesters.
     * GET /api/v1/semesters/active
     */
    @GetMapping("/active")
    public ResponseEntity<ApiResponse<List<SemesterDTO>>> findActive() {
        return ok(service.findActive());
    }

    /**
     * Creates a new semester. Requires ADMIN role.
     * POST /api/v1/semesters
     *
     * @throws mx.unam.icf.aulas.kernel.domain.exceptions.DomainException when the semester name is already taken
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SemesterDTO>> save(@Valid @RequestBody SemesterDTO dto) {
        return created(service.save(dto));
    }

    /**
     * Updates an existing semester. Requires ADMIN role.
     * PUT /api/v1/semesters/{id}
     *
     * @throws mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException when the semester is not found
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<SemesterDTO>> update(
            @PathVariable Long id,
            @Valid @RequestBody SemesterDTO dto) {
        return ok(service.update(id, dto));
    }
}
