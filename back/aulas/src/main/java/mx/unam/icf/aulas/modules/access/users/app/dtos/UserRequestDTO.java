package mx.unam.icf.aulas.modules.access.users.app.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Request payload for creating or updating a user account.
 *
 * <p>The {@code password} field carries the plain-text value; the service layer
 * is responsible for hashing it with BCrypt before persisting.</p>
 *
 * @param firstName first name of the user
 * @param lastNames paternal and maternal last names of the user
 * @param email     unique email address used as the login identifier
 * @param password  plain-text password to be hashed before storage
 * @param roleId    internal identifier of the role to assign
 *
 * @author Ithera
 * @version 2.0
 */
public record UserRequestDTO(

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(max = 100, message = "FIELD_OUT_OF_RANGE")
        String firstName,

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(max = 100, message = "FIELD_OUT_OF_RANGE")
        String lastNames,

        @NotBlank(message = "FIELD_REQUIRED")
        @Email(message = "FIELD_INVALID_FORMAT")
        String email,

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(min = 8, max = 128, message = "FIELD_OUT_OF_RANGE")
        String password,

        @NotNull(message = "FIELD_REQUIRED")
        Long roleId
) {}
