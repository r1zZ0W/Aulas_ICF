package mx.unam.icf.aulas.modules.access.auth.app.dtos.register;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for the {@code POST /api/v1/auth/register} endpoint.
 *
 * <p>Registers a new user account. The email must belong to the institutional
 * {@code @icf.unam.mx} domain. The password is accepted as plain-text and hashed
 * by the service layer before persistence.</p>
 *
 * @param firstName first name of the new user (max 100 chars)
 * @param lastNames paternal and maternal last names (max 100 chars)
 * @param username  unique username for the account (max 100 chars)
 * @param email     institutional email; must match {@code @icf.unam.mx}
 * @param password  plain-text password (8–128 chars), hashed before storage
 * @param roleId    internal identifier of the role to assign to this user
 */
public record RegisterRequestDTO(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @NotBlank(message = "Last names are required")
        @Size(max = 100, message = "Last names must be at most 100 characters")
        String lastNames,

        @NotBlank(message = "Username is required")
        @Size(max = 100, message = "Username must be at most 100 characters")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        @Pattern(regexp = "^[^\\s@]+@icf\\.unam\\.mx$", message = "Email must belong to @icf.unam.mx domain")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
        String password,

        @NotNull(message = "Role id is required")
        Long roleId
) {}
