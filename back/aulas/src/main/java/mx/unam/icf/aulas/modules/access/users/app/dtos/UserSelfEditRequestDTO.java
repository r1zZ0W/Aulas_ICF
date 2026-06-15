package mx.unam.icf.aulas.modules.access.users.app.dtos;

import jakarta.validation.constraints.Size;

/**
 * Request payload for a user editing their own profile.
 *
 * <p>Both fields are optional. Omitting or sending {@code null} for a field
 * leaves the corresponding value unchanged.</p>
 *
 * @param username new username to set (3–50 characters); {@code null} to leave unchanged
 * @param password new plaintext password to hash and store (8–128 characters); {@code null} to leave unchanged
 *
 * @author Ithera
 * @version 2.0
 */
public record UserSelfEditRequestDTO(

        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,

        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password
) {}
