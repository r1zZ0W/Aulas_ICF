package mx.unam.icf.aulas.modules.resources.classrooms.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.domain.exceptions.DomainException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.resources.classrooms.app.ClassroomService;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomRequestDTO;
import mx.unam.icf.aulas.modules.resources.classrooms.app.dtos.ClassroomResponseDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
 * REST controller for managing classroom API endpoints.
 * <p>
 * This controller provides HTTP endpoints for performing CRUD operations on classrooms.
 * All endpoints are exposed under the base path {@code /api/v1/classrooms}.
 * Responses are wrapped with the {@link ApiResponse} format for consistency.
 *
 * @author Ithera
 * @version 1.0
 * @see ClassroomService
 * @see ClassroomResponseDTO
 */
@RestController
@RequestMapping("/api/v1/classrooms")
@RequiredArgsConstructor
public class ClassroomController implements ResponseHandler {

    /**
     * Service instance for classroom business logic operations.
     */
    private final ClassroomService classroomService;

    /**
     * Retrieves all classrooms from the system.
     * <p>
     * GET /api/v1/classrooms
     *
     * @return a successful response containing a list of all classrooms
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ClassroomResponseDTO>>> findAll() {
        return ok(classroomService.findAll());
    }

    /**
     * Retrieves a specific classroom by its public UUID.
     * <p>
     * GET /api/v1/classrooms/{uuid}
     *
     * @param uuid the public UUID of the classroom to retrieve
     * @return a successful response containing the requested classroom
     * @throws ResourceNotFoundException when the classroom is not found
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<ApiResponse<ClassroomResponseDTO>> findByUuid(@PathVariable UUID uuid) {
        return ok(classroomService.findByUuid(uuid));
    }

    /**
     * Creates a new classroom in the system.
     * <p>
     * POST /api/v1/classrooms
     *
     * @param dto the classroom data to create
     * @return a created (201) response containing the newly created classroom
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ClassroomResponseDTO>> save(@Valid @RequestBody ClassroomRequestDTO dto) {
        return created(classroomService.save(dto));
    }

    /**
     * Updates an existing classroom with new data.
     * <p>
     * PUT /api/v1/classrooms
     *
     * @param dto the classroom data containing the update information
     * @return a successful response containing the updated classroom
     * @throws DomainException when the UUID is not valid.
     * @throws ResourceNotFoundException when the classroom is not found
     */
    @PutMapping("/{uuid}")
    public ResponseEntity<ApiResponse<ClassroomResponseDTO>> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody ClassroomRequestDTO dto
    ) {
        return ok(classroomService.update(uuid, dto));
    }

    /**
     * Deletes a classroom from the system by its public UUID.
     * <p>
     * DELETE /api/v1/classrooms/{uuid}
     *
     * @param uuid the public UUID of the classroom to delete
     * @return a successful response with a confirmation message
     * @throws DomainException when the UUID is null
     * @throws ResourceNotFoundException when the classroom is not found
     */
    @DeleteMapping("/{uuid}")
    public ResponseEntity<ApiResponse<Void>> deleteByUuid(@PathVariable UUID uuid) {
        classroomService.deleteByUuid(uuid);
        return ok("Classroom deleted successfully");
    }
}

