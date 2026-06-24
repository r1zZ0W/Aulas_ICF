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

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @NotBlank(message = "Last names are required")
        @Size(max = 100, message = "Last names must be at most 100 characters")
        String lastNames,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password,

        @NotNull(message = "Role id is required")
        Long roleId
) {}
