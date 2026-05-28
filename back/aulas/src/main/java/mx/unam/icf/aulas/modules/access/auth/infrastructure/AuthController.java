package mx.unam.icf.aulas.modules.access.auth.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.login.LoginRequestDTO;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.login.LoginResponseDTO;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.register.RegisterRequestDTO;
import mx.unam.icf.aulas.modules.access.auth.app.services.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 * All routes under {@code /api/v1/auth} are publicly accessible (no JWT required).
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements ResponseHandler {

    private final AuthService authService;

    /**
     * Authenticates a user with email and password, returning a signed JWT
     * alongside the basic session data needed by the client (UUID, name, role).
     *
     * <pre>
     * POST /api/v1/auth/login
     * Body: { "email": "user@icf.unam.mx", "password": "secretSecretoso" }
     * </pre>
     *
     * @param request the login credentials
     * @return HTTP 200 with a {@link LoginResponseDTO} payload on success,
     *         or HTTP 401 when credentials are invalid
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO request
    ) {
        return ok(authService.login(request));
    }

    /**
     *
     *
     * @param request the register data
     * @return
     */
    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Void>> register(
            @Valid @RequestBody RegisterRequestDTO request
    ) {
        authService.register(request);
        return created();
    }
}
