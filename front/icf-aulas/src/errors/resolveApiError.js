import { z } from 'zod';
import { HttpError } from '../api/base.js';
import { ApiError } from './ApiError.js';
import { ERROR_CATALOG, STATUS_FALLBACK } from './errorCatalog.js';

/**
 * Strips array indices and takes the terminal segment of a (possibly nested) field path —
 * e.g. `"roster[0].email"` -> `"email"`. Today no `@Valid` in the backend produces nested
 * paths (verified: every DTO is flat), but the normalization is one line and makes the
 * frontend adapter tolerant if that ever changes, instead of silently dropping the error.
 */
function normalizeFieldPath(path) {
  const last = path.split('.').pop();
  return last.replace(/\[\d+\]$/, '');
}

/** Translates a `{ field: ERROR_CODE }` map (as sent by the backend) into `{ field: Spanish text }`. */
function translateFieldErrors(rawFieldErrors) {
  if (!rawFieldErrors) return null;
  const translated = {};
  for (const [rawField, code] of Object.entries(rawFieldErrors)) {
    const field = normalizeFieldPath(rawField);
    // First error wins per field (N:1 case — two DTO fields mapped to the same UI control).
    if (translated[field]) continue;
    translated[field] = ERROR_CATALOG[code]?.text ?? `Error de validación (${code}).`;
  }
  return translated;
}

/**
 * Builds the human-readable message for the structured 409/422 payloads
 * (`ConflictDetailDTO`, `StudentCountMismatchDTO`, `StudentValidationErrorDTO`) — moved here
 * from the old per-call ad-hoc formatting in `api/reservations.js` so it isn't duplicated.
 */
function buildStructuredMessage(code, data) {
  if (code === 'RESERVATION_SLOT_CONFLICT' && data?.date && data?.timeSlotId != null) {
    return `Ya existe una reserva el ${data.date} en el bloque de horario ${data.timeSlotId}. ` +
      'Elige otro horario o fecha.';
  }
  if (code === 'ROSTER_COUNT_MISMATCH' && typeof data?.expected === 'number') {
    return `El Excel tiene ${data.actual} alumnos pero indicaste ${data.expected} asistentes. ` +
      'Corrige la lista o el número de asistentes.';
  }
  if (code === 'ROSTER_DUPLICATE_STUDENT' && data?.row != null) {
    return `El alumno "${data.value}" está repetido en la fila ${data.row} del Excel.`;
  }
  return null;
}

/**
 * Precedence for the plain (non-structured) message:
 * 1. overrides[code]   — override keyed by ErrorCode, from the call site
 * 2. ERROR_CATALOG[code]
 * 3. overrides[status]  — override keyed by HTTP status, from the call site
 * 4. STATUS_FALLBACK[status]
 * 5. generic "Error inesperado (status)."
 *
 * The server's raw English `message` is deliberately never used here — see ApiError's docs.
 */
function resolveMessage(code, status, overrides) {
  if (code && overrides[code]) return overrides[code];
  if (code && ERROR_CATALOG[code]) return ERROR_CATALOG[code].text;
  if (overrides[status]) return overrides[status];
  if (STATUS_FALLBACK[status]) return STATUS_FALLBACK[status];
  return `Error inesperado (${status}).`;
}

/**
 * Converts whatever an API call rejected with into an {@link ApiError} the UI can render:
 * an `HttpError` (server responded with a non-2xx), a `ZodError` from a *response* schema
 * that no longer matches the server's payload (contract drift), or anything else, which is
 * passed through unchanged — including an `ApiError` that already went through this resolver
 * (e.g. from {@link assertValidRequestPayload}), so calling it twice is always safe.
 *
 * @param {unknown} error
 * @param {Record<string, string>} [overrides] - per-call text overrides keyed by ErrorCode
 *   name or HTTP status.
 * @returns {ApiError|unknown}
 */
export function resolveApiError(error, overrides = {}) {
  if (error instanceof ApiError) return error;

  if (error instanceof z.ZodError) {
    // A *response* schema rejected the server's payload: the backend's contract drifted
    // from what the frontend expects. Distinct from a *request* schema failing on a
    // client-built payload, which never reaches this branch — see assertValidRequestPayload.
    if (import.meta.env.DEV) console.error('[Zod contract drift — response payload]', error.issues);
    return new ApiError({
      code: 'CONTRACT_MISMATCH',
      message: ERROR_CATALOG.CONTRACT_MISMATCH.text,
      status: 0,
    });
  }

  if (!(error instanceof HttpError)) return error;

  const code = error.data?.code ?? null;
  const rawFieldErrors = error.data?.data?.fieldErrors ?? null;
  const fieldErrors = translateFieldErrors(rawFieldErrors);

  const message = buildStructuredMessage(code, error.data?.data)
    ?? resolveMessage(code, error.status, overrides);

  // A business-error code with a known single field (e.g. USER_EMAIL_TAKEN -> 'email') still
  // highlights its input even when the backend didn't send a structured fieldErrors map.
  const catalogField = code ? ERROR_CATALOG[code]?.field : undefined;
  const resolvedFieldErrors = fieldErrors ?? (catalogField ? { [catalogField]: message } : null);

  return new ApiError({
    code,
    message,
    status: error.status,
    fieldErrors: resolvedFieldErrors,
    data: error.data?.data ?? null,
  });
}

/**
 * Validates a client-constructed request payload (one built from transformed/derived data,
 * not a straight pass-through of already-validated form state — see the two categories this
 * covers in `api/reservations.js`: `createBooking`/`reassignReservation`).
 *
 * A failure here is a frontend bug, not a user mistake or a server rejection: the UI already
 * told the user their input was valid. It must never be phrased as "your data is invalid" —
 * that blames the user for something they can't fix — so it always resolves to the generic
 * INTERNAL_ERROR text. In dev, the Zod issues are logged so the actual broken field is visible
 * without guessing from a blank toast.
 *
 * @param {import('zod').ZodSchema} schema
 * @param {*} payload
 * @returns {*} the parsed, typed payload
 * @throws {ApiError} with code INTERNAL_ERROR
 */
export function assertValidRequestPayload(schema, payload) {
  const result = schema.safeParse(payload);
  if (result.success) return result.data;

  if (import.meta.env.DEV) {
    console.error('[Invalid client-built request payload]', result.error.issues);
  }
  throw new ApiError({
    code: 'INTERNAL_ERROR',
    message: ERROR_CATALOG.INTERNAL_ERROR.text,
    status: 0,
  });
}
