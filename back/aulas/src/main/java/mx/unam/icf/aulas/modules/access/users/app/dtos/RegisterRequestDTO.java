package mx.unam.icf.aulas.modules.access.users.app.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request payload for the {@code POST /api/v1/users/register} endpoint.
 *
 * <p>All fields carry Bean Validation constraints applied before the service layer is
 * invoked. Validation failures surface as {@code 400} responses via
 * {@code GlobalExceptionHandler}.</p>
 *
 * @param firstName first name — letters, spaces and hyphens only (max 100)
 * @param lastNames paternal/maternal last names — same rules as firstName
 * @param username  unique handle — alphanumeric + dots, underscores and hyphens (3–50)
 * @param email     institutional address; must match {@code @icf.unam.mx}
 * @param password  plain-text password (hashed before storage); must satisfy strength rules
 * @param roleId    internal identifier of the role to assign
 */
public record RegisterRequestDTO(

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(max = 100, message = "FIELD_OUT_OF_RANGE")
        @Pattern(
                regexp = "^[a-zA-ZÀ-ÿ '\\-]{1,100}$",
                message = "PERSON_NAME_CHARSET_INVALID"
        )
        String firstName,

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(max = 100, message = "FIELD_OUT_OF_RANGE")
        @Pattern(
                regexp = "^[a-zA-ZÀ-ÿ '\\-]{1,100}$",
                message = "PERSON_NAME_CHARSET_INVALID"
        )
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
        @Pattern(
                regexp = "^[^\\s@]+@icf\\.unam\\.mx$",
                message = "USER_EMAIL_DOMAIN_INVALID"
        )
        String email,

        @NotBlank(message = "FIELD_REQUIRED")
        @Size(min = 8, max = 128, message = "FIELD_OUT_OF_RANGE")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_\\-+=])"
                       + "[A-Za-z\\d@$!%*?&#^()_\\-+=]{8,128}$",
                message = "USER_PASSWORD_WEAK"
        )
        String password,

        /**
         * Optional department or area. Free-text, maximum 100 characters.
         */
        @Size(max = 100, message = "FIELD_OUT_OF_RANGE")
        String departamento,

        /**
         * Optional role identifier. When {@code null}, the system defaults to the
         * {@code TEACHER} role (DFR §3.1).
         */
        Long roleId
) {}
