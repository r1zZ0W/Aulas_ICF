/**
 * Client-side validation helpers for user-facing forms.
 * Constraints are aligned with the backend Bean Validation rules defined in the DTOs.
 * Each function returns a string error message on failure, or null when the value is valid.
 */

/**
 * Validates the email from the login form.
 * It only checks the presence and basic format.
 * @returns {string|null} Error message or null if valid
 */
export function validateLoginUsername(username) {
  if (!username || username.trim().length === 0)
    return "El usuario es obligatorio";

  if (isNotAValidCharacter(username))
    return "No se permiten caracteres especiales";

  if (username.trim().length > 50)
    return "El usuario no puede exceder los 50 caracteres";

  if (username.trim().length < 3)
    return "El usuario debe tener al menos 5 caracteres";

  return null;
}

/**
 * Validates the password from the login form.
 * It only checks the presence.
 * @returns {string|null} Error message or null if valid
 */
export function validatePasswordLogin(contrasena) {
  if (!contrasena || contrasena.trim().length === 0) {
    return "La contraseña es obligatoria";
  }
  return null;
}

function isNotAValidCharacter(val) {
  if (val.includes(",")) return true;
  if (val.includes("/")) return true;
  if (val.includes("\\")) return true;
  if (val.includes("*")) return true;
  if (val.includes("+")) return true;
  if (val.includes("=")) return true;
  if (val.includes("!")) return true;
  if (val.includes("?")) return true;
  if (val.includes("¡")) return true;
  if (val.includes("¿")) return true;
  if (val.includes("#")) return true;
  if (val.includes("%")) return true;
  if (val.includes('"')) return true;
  if (val.includes("'")) return true;
  if (val.includes("$")) return true;
  if (val.includes("&")) return true;
  if (val.includes("@")) return true;
  if (val.includes("{")) return true;
  if (val.includes("}")) return true;
  if (val.includes("[")) return true;
  if (val.includes("]")) return true;
  if (val.includes("(")) return true;
  if (val.includes(")")) return true;
  if (val.includes("|")) return true;
  if (val.includes("<")) return true;
  if (val.includes(">")) return true;
  if (val.includes("~")) return true;
  if (val.includes("`")) return true;
  if (val.includes("^")) return true;
  return false;
}

export function handleOnChangeName(e, setForm, setErrores) {
  const value = e.target.value;

  if (isNotAValidCharacter(value) || !isNaN(value)) {
    setErrores((prev) => ({
      ...prev,
      nombre:
        "No se permiten puntos, comas, guiones, barras, asteriscos, signos de exclamación, interrogación o números",
    }));
    return;
  }

  setForm((prev) => ({ ...prev, nombre: value }));
  setErrores((prev) => ({ ...prev, nombre: null }));
}

export function handleOnChangeCurp(e, setForm, setErrores) {
  const value = e.target.value;

  if (isNotAValidCharacter(value)) {
    setErrores((prev) => ({
      ...prev,
      curp: "No se permiten puntos, comas, guiones, barras, asteriscos, signos de exclamación o interrogación",
    }));
    return;
  }

  setForm((prev) => ({ ...prev, curp: value.toUpperCase() }));
  setErrores((prev) => ({ ...prev, curp: null }));
}
