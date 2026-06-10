package mx.unam.icf.aulas.modules.access.auth.infrastructure;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.web.controllers.ResponseHandler;
import mx.unam.icf.aulas.kernel.infrastructure.web.responses.ApiResponse;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.login.LoginRequestDTO;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.login.LoginResponseDTO;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.logout.LogoutRequestDTO;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.password.ForgotPasswordRequestDTO;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.password.ResetPasswordRequestDTO;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.refresh.RefreshTokenRequestDTO;
import mx.unam.icf.aulas.modules.access.auth.app.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
     * Authenticates a user and returns a signed access token and refresh token.
     *
     * <pre>POST /api/v1/auth/login</pre>
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> login(
            @Valid @RequestBody LoginRequestDTO request
    ) {
        return ok(authService.login(request));
    }

    /**
     * Revokes the provided access token and (optionally) the refresh token.
     * Requires a valid {@code Authorization: Bearer <token>} header.
     *
     * <pre>POST /api/v1/auth/logout</pre>
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody(required = false) LogoutRequestDTO body
    ) {
        authService.logout(authHeader, body != null ? body.refreshToken() : null);
        return ok("Successful logout");
    }

    /**
     * Exchanges a valid refresh token for a new access token and a rotated refresh token.
     *
     * <pre>POST /api/v1/auth/refresh</pre>
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<LoginResponseDTO>> refresh(
            @Valid @RequestBody RefreshTokenRequestDTO body
    ) {
        return ok(authService.refresh(body.refreshToken()));
    }

    /**
     * Sends a password-reset link to the given email address.
     * Always returns 200 regardless of whether the email exists (prevents enumeration).
     *
     * <pre>POST /api/v1/auth/forgot-password</pre>
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<Void>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequestDTO body
    ) {
        authService.forgotPassword(body.email());
        return ok("If the email is registered, a password reset link has been sent.");
    }

    /**
     * Sets a new password using the one-time reset token received by email.
     *
     * <pre>POST /api/v1/auth/reset-password</pre>
     */
    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<Void>> resetPassword(
            @Valid @RequestBody ResetPasswordRequestDTO body
    ) {
        authService.resetPassword(body.token(), body.newPassword());
        return ok("Password reset done successfully.");
    }
}
