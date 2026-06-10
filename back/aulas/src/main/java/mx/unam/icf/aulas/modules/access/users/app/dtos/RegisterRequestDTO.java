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

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre debe tener como máximo 100 caracteres")
        @Pattern(
                regexp = "^[a-zA-ZÀ-ÿ '\\-]{1,100}$",
                message = "El nombre solo puede contener letras, espacios y guiones"
        )
        String firstName,

        @NotBlank(message = "Los apellidos son obligatorios")
        @Size(max = 100, message = "Los apellidos deben tener como máximo 100 caracteres")
        @Pattern(
                regexp = "^[a-zA-ZÀ-ÿ '\\-]{1,100}$",
                message = "Los apellidos solo pueden contener letras, espacios y guiones"
        )
        String lastNames,

        @NotBlank(message = "El nombre de usuario es obligatorio")
        @Size(min = 3, max = 50, message = "El nombre de usuario debe tener entre 3 y 50 caracteres")
        @Pattern(
                regexp = "^[a-zA-Z0-9][a-zA-Z0-9._-]{2,49}$",
                message = "El nombre de usuario solo puede contener letras, números, puntos, guiones y guiones bajos, y debe comenzar con una letra o número"
        )
        String username,

        @NotBlank(message = "El correo electrónico es obligatorio")
        @Email(message = "Formato de correo electrónico inválido")
        @Pattern(
                regexp = "^[^\\s@]+@icf\\.unam\\.mx$",
                message = "El correo debe pertenecer al dominio @icf.unam.mx"
        )
        String email,

        @NotBlank(message = "La contraseña es obligatoria")
        @Size(min = 8, max = 128, message = "La contraseña debe tener entre 8 y 128 caracteres")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&#^()_\\-+=])"
                       + "[A-Za-z\\d@$!%*?&#^()_\\-+=]{8,128}$",
                message = "La contraseña debe contener al menos una letra minúscula, una mayúscula, un número y un carácter especial (@$!%*?&#^()_-+=)"
        )
        String password,

        @NotNull(message = "El rol es obligatorio")
        Long roleId
) {}
