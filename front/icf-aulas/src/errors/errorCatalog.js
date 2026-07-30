/**
 * @fileoverview The single source of Spanish error text for the whole app.
 *
 * Every value here must mirror a constant of `ErrorCode` in the backend
 * (`back/aulas/.../kernel/domain/exceptions/ErrorCode.java`) — the paridad script
 * (`scripts/checkErrorCodeParity.mjs`) fails the build if either side drifts from the other.
 *
 * `field` is only set on codes that identify a specific input WITHOUT the backend sending a
 * structured `fieldErrors` map (e.g. a plain 409 "email already taken"). Codes that always
 * arrive already keyed by field inside `fieldErrors` (FIELD_REQUIRED, USER_PASSWORD_WEAK, ...)
 * don't need one — resolveApiError already knows which field they belong to from the map key.
 */
export const ERROR_CATALOG = {
  // ── Generic / fallback ────────────────────────────────────────────────────
  INTERNAL_ERROR: { text: 'Ocurrió un error inesperado. Intenta de nuevo.' },
  VALIDATION_FAILED: { text: 'Los datos enviados no son válidos.' },
  MALFORMED_REQUEST: { text: 'La solicitud no se pudo procesar. Intenta de nuevo.' },
  INVALID_PARAMETERS: { text: 'Los parámetros enviados no son válidos.' },
  ENDPOINT_NOT_FOUND: { text: 'El recurso solicitado no existe.' },
  DB_CONSTRAINT_VIOLATION: { text: 'La operación entra en conflicto con datos existentes.' },
  CONTRACT_MISMATCH: { text: 'La respuesta del servidor no tiene el formato esperado.' },
  FIELD_REQUIRED: { text: 'Este campo es obligatorio.' },
  FIELD_INVALID_FORMAT: { text: 'El formato de este campo no es válido.' },
  FIELD_OUT_OF_RANGE: { text: 'El valor de este campo está fuera del rango permitido.' },

  // ── Auth ───────────────────────────────────────────────────────────────────
  INVALID_CREDENTIALS: { text: 'Usuario o contraseña incorrectos.' },
  ACCOUNT_LOCKED: { text: 'Tu cuenta está temporalmente bloqueada por demasiados intentos fallidos. Intenta de nuevo en 10 minutos.' },
  ACCOUNT_DISABLED: { text: 'Tu cuenta está deshabilitada. Contacta al administrador.' },
  RATE_LIMITED: { text: 'Demasiados intentos. Espera un momento antes de volver a intentarlo.' },
  TOKEN_MISSING: { text: 'Debes iniciar sesión para continuar.' },
  TOKEN_INVALID: { text: 'Tu sesión no es válida o ha expirado.' },
  TOKEN_REVOKED: { text: 'Tu sesión fue cerrada desde otro lugar. Vuelve a iniciar sesión.' },
  TOKEN_WRONG_TYPE: { text: 'Tu sesión no es válida. Vuelve a iniciar sesión.' },
  ACCESS_DENIED: { text: 'No tienes permisos para realizar esta acción.' },

  // ── Users / roles ────────────────────────────────────────────────────────
  USER_NOT_FOUND: { text: 'El usuario solicitado no existe.' },
  USER_EMAIL_TAKEN: { text: 'Ese correo ya está registrado.', field: 'email' },
  USER_USERNAME_TAKEN: { text: 'Ese nombre de usuario ya está en uso.', field: 'username' },
  USER_CANNOT_DELETE_SELF: { text: 'No puedes eliminar tu propia cuenta.' },
  USER_PASSWORD_CHANGE_FORBIDDEN: { text: 'Solo un administrador puede cambiar la contraseña desde este perfil.' },
  USER_SELF_ROLE_EDIT_FORBIDDEN: { text: 'No puedes modificar tu propio rol o estado desde aquí; usa la edición de tu perfil.' },
  USER_MATRICULA_GENERATION_FAILED: { text: 'No se pudo generar una matrícula única. Intenta de nuevo.' },
  USER_EMAIL_DOMAIN_INVALID: { text: 'El correo debe pertenecer al dominio @icf.unam.mx.' },
  USER_PASSWORD_WEAK: { text: 'La contraseña debe incluir minúscula, mayúscula, número y carácter especial (@$!%*?&#^()_-+=).' },
  USER_USERNAME_CHARSET_INVALID: { text: 'El usuario solo puede contener letras, números, puntos, guiones y guiones bajos.' },
  PERSON_NAME_CHARSET_INVALID: { text: 'Este campo solo puede contener letras, espacios y guiones.' },
  ROLE_NOT_FOUND: { text: 'El rol solicitado no existe.' },

  // ── Semesters ────────────────────────────────────────────────────────────
  SEMESTER_NOT_FOUND: { text: 'El semestre solicitado no existe.' },
  SEMESTER_NAME_TAKEN: { text: 'Ya existe un semestre con ese nombre.', field: 'name' },
  SEMESTER_NO_ACTIVE: { text: 'No hay un semestre activo actualmente.' },
  SEMESTER_END_BEFORE_START: { text: 'La fecha de fin debe ser posterior a la de inicio.' },
  SEMESTER_DURATION_TOO_LONG: { text: 'La duración del semestre excede el máximo permitido.' },
  SEMESTER_START_IN_PAST: { text: 'La fecha de inicio no puede estar en el pasado.' },
  SEMESTER_END_IN_PAST: { text: 'La fecha de fin no puede estar en el pasado.' },

  // ── Classrooms / resources / allocations ────────────────────────────────
  CLASSROOM_NOT_FOUND: { text: 'El aula solicitada no existe.' },
  CLASSROOM_NAME_TAKEN: { text: 'Ya existe un aula con ese nombre.', field: 'name' },
  CLASSROOM_NAME_CHARSET_INVALID: { text: 'El nombre contiene caracteres no permitidos.' },
  CLASSROOM_IMAGE_URL_INVALID: { text: 'La URL de la imagen no es válida.' },
  CLASSROOM_INACTIVE: { text: 'El aula está inactiva y no puede reservarse.' },
  CLASSROOM_CHILDREN_NOT_FOUND: { text: 'Una o más aulas hijas no existen.' },
  CLASSROOM_CHILD_INACTIVE: { text: 'Un aula hija seleccionada está inactiva.' },
  CLASSROOM_CYCLE_DETECTED: { text: 'Esa asignación crearía un ciclo entre aulas.' },
  RESOURCE_NOT_FOUND: { text: 'El recurso solicitado no existe.' },
  RESOURCE_NAME_TAKEN: { text: 'Ya existe un recurso con ese nombre.', field: 'name' },
  ALLOCATION_NOT_FOUND: { text: 'Esa asignación de recurso no existe.' },
  ALLOCATION_QUANTITY_INVALID: { text: 'La cantidad debe ser al menos 1.', field: 'quantity' },

  // ── Reservations ─────────────────────────────────────────────────────────
  RESERVATION_NOT_FOUND: { text: 'La reserva solicitada no existe.' },
  RESERVATION_GROUP_NOT_FOUND: { text: 'El grupo de reservas no existe.' },
  RESERVATION_GROUP_ALREADY_CANCELLED: { text: 'Ese grupo de reservas ya está cancelado.' },
  RESERVATION_GROUP_SCHEDULE_MISMATCH: { text: 'La fecha no coincide con los días programados del grupo.' },
  TIMESLOT_NOT_FOUND: { text: 'Uno o más horarios seleccionados no existen.', field: 'timeSlotIds' },
  RESERVATION_SLOT_CONFLICT: { text: 'Ese horario ya está ocupado. Elige otro horario o fecha.', field: 'timeSlotIds' },
  RESERVATION_OWN_CONFLICT: { text: 'Ya tienes una reserva en ese horario.', field: 'timeSlotIds' },
  RESERVATION_DATE_IN_PAST: { text: 'No se pueden hacer reservas en fechas pasadas.' },
  RESERVATION_ON_SUNDAY: { text: 'No se pueden hacer reservas en domingo.' },
  RESERVATION_DATE_OUT_OF_SEMESTER: { text: 'La fecha está fuera del periodo del semestre activo.' },
  RESERVATION_NO_VALID_DATES: { text: 'No hay fechas válidas para los días seleccionados.' },
  RESERVATION_TOO_SOON: { text: 'La reserva debe hacerse con al menos 15 minutos de anticipación.' },
  RESERVATION_ALREADY_CANCELLED: { text: 'Esa reserva ya está cancelada.' },
  RESERVATION_NOT_ACTIVE: { text: 'Solo las reservas activas pueden reasignarse.' },
  REASSIGN_TARGET_REQUIRED: { text: 'Debes indicar un aula u horario nuevo para reasignar.' },
  REASSIGN_CLASSROOM_INACTIVE: { text: 'El aula destino está inactiva.' },

  // ── Student roster ───────────────────────────────────────────────────────
  ROSTER_EMPTY: { text: 'La lista de alumnos está vacía o no es válida.' },
  ROSTER_DUPLICATE_STUDENT: { text: 'Hay un alumno duplicado en la lista.' },
  ROSTER_COUNT_MISMATCH: { text: 'El número de alumnos en el Excel no coincide con los asistentes indicados.' },
  ROSTER_FILE_INVALID: { text: 'El archivo no es un .xlsx válido.' },
  ROSTER_FILE_UNREADABLE: { text: 'No se pudo leer el archivo subido.' },

  // ── Infra ────────────────────────────────────────────────────────────────
  FILE_TOO_LARGE: { text: 'El archivo excede el tamaño máximo permitido.' },
  FILE_PART_MISSING: { text: 'Falta un archivo requerido en la solicitud.' },
  FILE_STORAGE_ERROR: { text: 'No se pudo procesar el archivo. Intenta de nuevo más tarde.' },
  MAIL_SENDING_ERROR: { text: 'No se pudo enviar el correo. Intenta de nuevo más tarde.' },
  INVALID_SORT_FIELD: { text: 'El criterio de ordenamiento no es válido.' },
};

/** Fallback by HTTP status when a code is missing or unrecognized. */
export const STATUS_FALLBACK = {
  0: 'No se pudo conectar con el servidor. Verifica tu conexión.',
  400: 'Los datos enviados no son válidos.',
  401: 'No autorizado.',
  403: 'No tienes permisos para realizar esta acción.',
  404: 'El recurso solicitado no existe.',
  409: 'La operación entra en conflicto con datos existentes.',
  413: 'El archivo excede el tamaño máximo permitido.',
  422: 'Los datos enviados no pudieron ser procesados.',
  429: 'Demasiados intentos. Espera un momento antes de volver a intentarlo.',
  500: 'Error interno del servidor. Intenta de nuevo más tarde.',
};
