package mx.unam.icf.aulas.modules.access.users.infrastructure;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import mx.unam.icf.aulas.kernel.app.dtos.PagedResultDTO;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.PageCriteria;
import mx.unam.icf.aulas.kernel.infrastructure.web.paging.SortWhitelist;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.access.users.app.UserService;
import mx.unam.icf.aulas.modules.access.users.app.dtos.RegisterRequestDTO;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserResponseDTO;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserSelfEditRequestDTO;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserStatsDTO;
import mx.unam.icf.aulas.modules.access.users.app.dtos.UserUpdateRequestDTO;
import mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails.UserDetailsImp;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * REST controller for managing user account endpoints.
 *
 * <p>Registration, listing, admin update, and deactivation are restricted to the {@code ADMIN} role.
 * The self-edit endpoint ({@code PUT /me}) is available to any authenticated user.
 * All endpoints are exposed under {@code /api/v1/users}.</p>
 */
@RestController
@RequestMapping(value = "/api/v1/users", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class UserController implements ResponseHandler {

    private final UserService userService;

    /**
     * Registers a new user account. Requires ADMIN role.
     * POST /api/v1/users/register
     */
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequestDTO request
    ) {
        userService.save(request);
        return created();
    }

    /**
     * Returns aggregated statistics for the admin dashboard. Requires ADMIN role.
     * GET /api/v1/users/stats
     *
     * <p>Resolves total, active, inactive, and admin user counts in a single database
     * round-trip. Spring MVC matches the literal path {@code /stats} before the
     * template {@code /{uuid}}, so there is no route conflict.</p>
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserStatsDTO>> stats() {
        return ok(userService.getStats());
    }

    /**
     * Retrieves all user accounts, paginated. Requires ADMIN role.
     * GET /api/v1/users[?search=text&page=0&size=20&sort=createdAt&direction=desc]
     *
     * <p>When {@code page} and {@code size} are omitted the response contains all users
     * in a single page (format stays consistent: {@code { items, totalElements, ... }}).
     * Allowed sort fields: {@code createdAt}, {@code email}, {@code username},
     * {@code matricula}, {@code firstName}.</p>
     *
     * <p>When {@code search} is provided, a case-insensitive {@code LIKE} filter is
     * applied over {@code firstName}, {@code lastNames}, {@code email}, {@code username},
     * and {@code matricula}. {@code totalElements} reflects the filtered count.</p>
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PagedResultDTO<UserResponseDTO>>> findAll(
            @RequestParam(value = "search", required = false) String search,
            @SortWhitelist(
                    value = {"createdAt", "email", "username", "matricula", "firstName"},
                    defaultSort = "createdAt",
                    defaultDirection = "desc")
            PageCriteria criteria) {
        return ok(userService.findAll(search, criteria.toPageable()));
    }

    /**
     * Retrieves a single user by their public UUID. Requires ADMIN role.
     * GET /api/v1/users/{uuid}
     *
     * @throws mx.unam.icf.aulas.kernel.infrastructure.exceptions.ResourceNotFoundException when the user is not found
     */
    @GetMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponseDTO>> findByUuid(@PathVariable UUID uuid) {
        return ok(userService.findByUuid(uuid));
    }

    /**
     * Updates a user's profile information. Requires ADMIN role.
     * An administrator cannot modify their own role or active status (DFR §3.2).
     * PUT /api/v1/users/{uuid}
     *
     * @throws mx.unam.icf.aulas.kernel.domain.exceptions.DomainException when the email or username is taken
     *         or when the admin attempts to modify their own account
     */
    @PutMapping("/{uuid}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<UserResponseDTO>> update(
            @PathVariable UUID uuid,
            @Valid @RequestBody UserUpdateRequestDTO dto,
            @AuthenticationPrincipal UserDetailsImp principal
    ) {
        return ok(userService.update(uuid, dto, principal.getUuid()));
    }

    /**
     * Soft-deactivates a user account. Requires ADMIN role.
     * PATCH /api/v1/users/{uuid}/deactivate
     *
     * @throws mx.unam.icf.aulas.kernel.domain.exceptions.DomainException when the admin attempts to deactivate their own account
     */
    @PatchMapping("/{uuid}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deactivate(
            @PathVariable UUID uuid,
            @AuthenticationPrincipal UserDetailsImp principal
    ) {
        userService.deactivate(uuid, principal.getUuid());
        return ok("User deactivated successfully");
    }

    /**
     * Allows the authenticated user to update their own username and/or password.
     * PUT /api/v1/users/me
     */
    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponseDTO>> selfEdit(
            @Valid @RequestBody UserSelfEditRequestDTO dto,
            @AuthenticationPrincipal UserDetailsImp principal
    ) {
        return ok(userService.selfEdit(principal.getUuid(), dto));
    }
}
