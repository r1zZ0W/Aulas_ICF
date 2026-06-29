package mx.unam.icf.aulas.modules.access.users.app.dtos;

import jakarta.validation.constraints.Size;

/**
 * Request payload for a user editing their own profile.
 *
 * <p>All account fields are required so the profile form can submit a complete
 * payload. Password is optional and may be {@code null} to leave it unchanged.</p>
 *
 * @param firstName  updated first name
 * @param lastNames  updated last names
 * @param username   new username to set (3–50 characters)
 * @param email      updated email address
 * @param extension  optional phone/office extension
 * @param password   new plaintext password to hash and store (8–128 characters); {@code null} to leave unchanged
 *
 * @author Ithera
 * @version 2.0
 */
public record UserSelfEditRequestDTO(

        @jakarta.validation.constraints.NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @jakarta.validation.constraints.NotBlank(message = "Last names are required")
        @Size(max = 100, message = "Last names must be at most 100 characters")
        String lastNames,

        @jakarta.validation.constraints.NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        String username,

        @jakarta.validation.constraints.NotBlank(message = "Email is required")
        @jakarta.validation.constraints.Email(message = "Invalid email format")
        String email,

        @Size(max = 20, message = "Extension must be at most 20 characters")
        String extension,

        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password
) {}
