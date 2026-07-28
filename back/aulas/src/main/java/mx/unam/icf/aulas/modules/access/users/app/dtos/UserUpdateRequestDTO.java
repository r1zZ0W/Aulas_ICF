package mx.unam.icf.aulas.modules.access.users.app.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for an administrator updating a user's profile.
 *
 * <p>All profile fields are required. Password is optional and, when present,
 * will be re-hashed before saving. The role can be changed by providing a
 * different {@code roleId}.</p>
 *
 * @param firstName first name of the user
 * @param lastNames last name(s) of the user
 * @param username  unique login username
 * @param email     unique email address
 * @param roleId    internal identifier of the role to assign
 * @param password  optional new password for admin-managed accounts
 *
 * @author Ithera
 * @version 2.0
 */
public record UserUpdateRequestDTO(

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(max = 100, message = "FIELD_OUT_OF_RANGE")
        String firstName,

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(max = 100, message = "FIELD_OUT_OF_RANGE")
        String lastNames,

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(min = 3, max = 50, message = "FIELD_OUT_OF_RANGE")
        @Pattern(
                regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]{2,49}$",
                message = "USER_USERNAME_CHARSET_INVALID"
        )
        String username,

        @NotBlank(message = "FIELD_REQUIRED")
        @Email(message = "FIELD_INVALID_FORMAT")
        String email,

        @NotNull(message = "FIELD_REQUIRED")
        Long roleId,

        @Size(min = 8, max = 128, message = "FIELD_OUT_OF_RANGE")
        String password
) {}
