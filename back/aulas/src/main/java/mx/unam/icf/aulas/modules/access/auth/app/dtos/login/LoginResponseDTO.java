package mx.unam.icf.aulas.modules.access.auth.app.dtos.login;

import java.util.UUID;

/**
 * Payload returned by a successful login.
 *
 * <p>Exposes the public {@link UUID} of the authenticated user — the internal numeric ID
 * is intentionally omitted to avoid leaking implementation details to clients.</p>
 *
 * @param token  signed JWT to be sent in subsequent {@code Authorization: Bearer} headers
 * @param uuid   public identifier of the authenticated user
 * @param nombre full name for display purposes
 * @param role   role name (e.g. "MAESTRO", "ADMIN")
 * @param email  login email of the authenticated user
 */
public record LoginResponseDTO(
        String token,
        UUID   uuid,
        String name,
        String role,
        String email
) {}
