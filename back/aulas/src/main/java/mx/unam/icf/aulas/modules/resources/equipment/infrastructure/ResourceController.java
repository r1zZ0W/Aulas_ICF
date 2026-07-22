package mx.unam.icf.aulas.modules.resources.equipment.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.PageCriteria;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.SortWhitelist;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.resources.equipment.app.ResourceService;
import mx.unam.icf.aulas.modules.resources.equipment.app.dtos.ResourceRequestDTO;
import mx.unam.icf.aulas.modules.resources.equipment.app.dtos.ResourceResponseDTO;
import mx.unam.icf.aulas.modules.resources.equipment.app.dtos.ResourceStatsDTO;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for managing the global equipment resource catalog.
 *
 * <p>Read operations are available to any authenticated user.
 * Write and delete operations require the {@code ADMIN} role.
 * All endpoints are exposed under {@code /api/v1/resources} and are keyed by
 * the resource's public UUID — never its internal numeric id.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/resources", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class ResourceController implements ResponseHandler {

    private final ResourceService service;

    /**
     * Returns aggregated resource statistics for the admin dashboard.
     * GET /api/v1/resources/stats
     *
     * <p>Spring MVC matches the literal path {@code /stats} before the template
     * {@code /{uuid}}, so there is no route conflict.</p>
     */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<ResourceStatsDTO>> stats() {
        return ok(service.getStats());
    }

    /**
     * Retrieves all equipment resources in the catalog, paginated.
     * GET /api/v1/resources[?search=text&page=0&size=20&sort=name&direction=asc]
     *
     * <p>When {@code search} is provided, a case-insensitive {@code LIKE} filter is
     * applied over {@code name} and {@code description}. Allowed sort fields:
     * {@code name}, {@code quantity}.</p>
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PagedResultDTO<ResourceResponseDTO>>> findAll(
            @RequestParam(value = "search", required = false) String search,
            @SortWhitelist(
                    value = {"name", "quantity"},
                    defaultSort = "name",
                    defaultDirection = "asc")
            PageCriteria criteria) {
        return ok(service.findAll(search, criteria.toPageable()));
    }

    /**
     * Retrieves a single equipment resource by its public UUID.
     * GET /api/v1/resources/{uuid}
     *
     * @throws mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException when the resource is not found
     */
    @GetMapping("/{uuid}")
    public ResponseEntity<ApiResponse<ResourceResponseDTO>> findByUuid(@PathVariable UUID uuid) {
        return ok(service.findByUuid(uuid));
    }

    /**
     * Creates a new equipment resource in the catalog. Requires ADMIN role.
     * POST /api/v1/resources
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ResourceResponseDTO>> save(@Valid @RequestBody ResourceRequestDTO dto) {
        return created(service.save(dto));
    }

    /**
     * Updates an existing equipment resource. Requires ADMIN role.
     * PUT /api/v1/resources/{uuid}
     */
    @PutMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ResourceResponseDTO>> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody ResourceRequestDTO dto) {
        return ok(service.update(uuid, dto));
    }

    /**
     * Deletes an equipment resource from the catalog. Requires ADMIN role.
     *
     * <p>Any classroom allocations referencing this resource are removed by the
     * database's {@code ON DELETE CASCADE} constraint (see {@link ResourceService#delete}).</p>
     *
     * DELETE /api/v1/resources/{uuid}
     */
    @DeleteMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID uuid) {
        service.delete(uuid);
        return ok("Equipment resource deleted successfully");
    }
}
