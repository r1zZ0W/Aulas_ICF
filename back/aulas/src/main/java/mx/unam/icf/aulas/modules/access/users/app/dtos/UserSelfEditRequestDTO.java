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

        @jakarta.validation.constraints.NotBlank(message = "FIELD_REQUIRED")
        @Size(max = 100, message = "FIELD_OUT_OF_RANGE")
        String firstName,

        @jakarta.validation.constraints.NotBlank(message = "FIELD_REQUIRED")
        @Size(max = 100, message = "FIELD_OUT_OF_RANGE")
        String lastNames,

        @jakarta.validation.constraints.NotBlank(message = "FIELD_REQUIRED")
        @Size(min = 3, max = 50, message = "FIELD_OUT_OF_RANGE")
        String username,

        @jakarta.validation.constraints.NotBlank(message = "FIELD_REQUIRED")
        @jakarta.validation.constraints.Email(message = "FIELD_INVALID_FORMAT")
        String email,

        @Size(max = 20, message = "FIELD_OUT_OF_RANGE")
        String extension,

        @Size(min = 8, max = 128, message = "FIELD_OUT_OF_RANGE")
        String password
) {}
