package mx.unam.icf.aulas.modules.access.auth.app;

import lombok.RequiredArgsConstructor;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.auth.InvalidCredentialsException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.auth.InvalidTokenException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.auth.MissingTokenException;
import mx.unam.icf.aulas.kernel.infrastructure.exceptions.auth.TokenRevokedException;
import mx.unam.icf.aulas.kernel.infrastructure.jwt.JwtProvider;
import mx.unam.icf.aulas.kernel.infrastructure.jwt.TokenBlacklist;
import mx.unam.icf.aulas.kernel.infrastructure.services.MailSender;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.login.LoginRequestDTO;
import mx.unam.icf.aulas.modules.access.auth.app.dtos.login.LoginResponseDTO;
import mx.unam.icf.aulas.modules.access.users.app.UserService;
import mx.unam.icf.aulas.modules.access.users.infrastructure.UserRepository;
import mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails.UserDetailsImp;
import mx.unam.icf.aulas.modules.access.users.infrastructure.userdetails.UserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Business logic for authentication operations: login, logout, token refresh,
 * forgot-password and reset-password.
 *
 * <p>Every method that receives user-supplied credentials applies a fast-fail
 * validation before issuing any database or token-provider call, reducing both
 * unnecessary I/O and attack surface.</p>
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtProvider            jwtProvider;
    private final TokenBlacklist          blacklistService;
    private final UserDetailsServiceImpl userDetailsService;
    private final UserRepository         userRepository;
    private final UserService            userService;
    private final MailSender             mailSender;
    private final AuthUtils              authUtils;

    @Value("${app.frontend-url:http://localhost:5173}")
    private String frontendUrl;

    /**
     * Authenticates a user and returns access + refresh tokens with basic profile info.
     *
     * @param request login credentials
     * @return login response with tokens and user info
     * @throws InvalidCredentialsException with a neutral message on any credential failure
     */
    public LoginResponseDTO login(LoginRequestDTO request) {
        authUtils.guardCredentials(request.username(), request.password());

        Authentication authentication = authUtils.authenticate(request.username(), request.password());

        UserDetailsImp principal = (UserDetailsImp) authentication.getPrincipal();
        if (principal == null)
            throw new InvalidCredentialsException("Authentication succeeded but principal is null");

        String token        = jwtProvider.generateToken(authentication);
        String refreshToken = jwtProvider.generateRefreshToken(authentication);

        return new LoginResponseDTO(
                token,
                refreshToken,
                principal.getUuid(),
                principal.getNombreCompleto(),
                principal.getRoleName(),
                principal.getEmail()
        );
    }

    /**
     * Revokes the access token from the Authorization header and optionally a refresh token.
     * The authHeader can be either a raw token or a Bearer header value.
     *
     * @param authHeader   Authorization header or raw token
     * @param refreshToken optional refresh token to revoke
     */
    public void logout(String authHeader, String refreshToken) {
        String authToken = authUtils.extractBearerToken(authHeader);
        authUtils.safeBlacklist(authToken);
        if (refreshToken != null && !refreshToken.isBlank())
            authUtils.safeBlacklist(refreshToken);
    }

    /**
     * Issues a new access token by validating and rotating a refresh token.
     *
     * @param rawRefreshToken refresh token provided by the client
     * @return new access/refresh token pair with user info
     * @throws MissingTokenException   if the token is absent
     * @throws InvalidTokenException   if the token is invalid, expired, or wrong type
     * @throws TokenRevokedException   if the token has been blacklisted
     */
    public LoginResponseDTO refresh(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank())
            throw new MissingTokenException("Refresh token is required");

        if (jwtProvider.isTokenInvalid(rawRefreshToken)
                || !"refresh".equals(jwtProvider.getTypeFromToken(rawRefreshToken)))
            throw new InvalidTokenException("Refresh token is invalid or has expired");

        String jti = jwtProvider.getJtiFromToken(rawRefreshToken);
        if (blacklistService.isBlacklisted(jti))
            throw new TokenRevokedException("Refresh token has been revoked");

        String uuid = jwtProvider.getUuidFromToken(rawRefreshToken);
        UserDetailsImp user = (UserDetailsImp) userDetailsService.loadUserByUuid(uuid);

        Authentication auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        String newToken        = jwtProvider.generateToken(auth);
        String newRefreshToken = jwtProvider.generateRefreshToken(auth);

        // Rotate: invalidate the used refresh token
        blacklistService.blacklist(jti, jwtProvider.getRemainingTtlSeconds(rawRefreshToken));

        return new LoginResponseDTO(
                newToken,
                newRefreshToken,
                user.getUuid(),
                user.getNombreCompleto(),
                user.getRoleName(),
                user.getEmail()
        );
    }

    /**
     * Sends a reset-password link if the email exists, and remains silent otherwise.
     * This avoids account enumeration while keeping the same outward behavior.
     *
     * @param email account email
     */
    public void forgotPassword(String email) {
        if (email == null || email.isBlank()) return;

        userRepository.findByEmailIgnoreCase(email.strip()).ifPresent(user -> {
            String resetToken = jwtProvider.generateResetToken(user.getUuid().toString());
            String link = frontendUrl + "/reset-password?token=" + resetToken;
            mailSender.sendHtml(
                    email,
                    "Restablecer contraseña",
                    "<p>Haz clic en el siguiente enlace para restablecer tu contraseña. "
                  + "El enlace es válido durante 1 hora.</p>"
                  + "<p><a href=\"" + link + "\">Restablecer contraseña</a></p>"
                  + "<p>Si no solicitaste este cambio, ignora este correo.</p>"
            );
        });
        // Always returns successfully to prevent email enumeration
    }

    /**
     * Validates a reset token, updates the password, and invalidates the token.
     *
     * @param rawToken    reset token from the email link
     * @param newPassword new password to set
     * @throws MissingTokenException  if the token is absent
     * @throws InvalidTokenException  if the token is invalid, expired, or wrong type
     * @throws TokenRevokedException  if the token has already been used
     */
    public void resetPassword(String rawToken, String newPassword) {
        if (rawToken == null || rawToken.isBlank())
            throw new MissingTokenException("Password reset token is required");

        if (jwtProvider.isTokenInvalid(rawToken)
                || !"reset".equals(jwtProvider.getTypeFromToken(rawToken)))
            throw new InvalidTokenException("Password reset token is invalid or has expired");

        String jti = jwtProvider.getJtiFromToken(rawToken);
        if (blacklistService.isBlacklisted(jti))
            throw new TokenRevokedException("Password reset token has already been used");

        UUID uuid = UUID.fromString(jwtProvider.getUuidFromToken(rawToken));
        userService.updatePassword(uuid, newPassword);

        // Burn the token so it cannot be reused
        blacklistService.blacklist(jti, jwtProvider.getRemainingTtlSeconds(rawToken));
    }
}
