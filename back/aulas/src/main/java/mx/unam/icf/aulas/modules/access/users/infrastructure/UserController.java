package mx.unam.icf.aulas.modules.access.users.infrastructure;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.access.users.app.dtos.RegisterRequestDTO;
import mx.unam.icf.aulas.modules.access.users.app.UserService;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing user API endpoints.
 * All endpoints are exposed under the base path {@code /api/v1/users}.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController implements ResponseHandler {

    private final UserService userService;

    /**
     * Registers a new user in the system. Restricted to ADMIN role to prevent
     * privilege escalation (any authenticated user assigning arbitrary roles).
     * POST /api/v1/users/register
     *
     * @param request the register data
     * @return a created (201) response with no body payload
     */
    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequestDTO request
    ) {
        userService.save(request);
        return created();
    }
}
