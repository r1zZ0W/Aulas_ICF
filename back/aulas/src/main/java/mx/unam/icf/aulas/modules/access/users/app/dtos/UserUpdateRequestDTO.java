package mx.unam.icf.aulas.modules.access.users.app.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for an administrator updating a user's profile.
 *
 * <p>All fields except {@code isActive} are required. The role can be changed
 * by providing a different {@code roleId}.</p>
 *
 * @param firstName first name of the user
 * @param lastNames last name(s) of the user
 * @param username  unique login username
 * @param email     unique email address
 * @param roleId    internal identifier of the role to assign
 * @param isActive  optional override for the account active flag
 *
 * @author Ithera
 * @version 2.0
 */
public record UserUpdateRequestDTO(

        @NotBlank(message = "First name is required")
        @Size(max = 100, message = "First name must be at most 100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(max = 100, message = "Last name must be at most 100 characters")
        String lastNames,

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
        @Pattern(
                regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]{2,49}$",
                message = "Username may only contain letters, numbers, dots, hyphens and underscores"
        )
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Invalid email format")
        String email,

        @NotNull(message = "Role is required")
        Long roleId,

        Boolean isActive,

        /** Department or area the user belongs to (optional). */
        @Size(max = 100, message = "Department must be at most 100 characters")
        String departamento
) {}
