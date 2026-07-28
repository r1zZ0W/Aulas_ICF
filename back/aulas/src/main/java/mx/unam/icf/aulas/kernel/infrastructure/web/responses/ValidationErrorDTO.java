package mx.unam.icf.aulas.kernel.infrastructure.web.responses;

import java.util.Map;

/**
 * Structured payload for a 400 bean-validation failure: one {@link mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode}
 * per offending field, keyed by the DTO's property name.
 *
 * <p>Replaces the previous behavior of flattening every field error into a single
 * semicolon-joined string, which the frontend could never reliably split back into
 * per-field messages. The frontend resolves each code to Spanish text via its own
 * catalog and — using a per-form {@code DTO_MAP} — highlights the matching input.</p>
 *
 * @param fieldErrors map of DTO property name → {@link mx.unam.icf.aulas.kernel.domain.exceptions.ErrorCode} name.
 *                     Only the first violation per field is kept, mirroring the frontend's
 *                     own {@code useZodForm} behavior of surfacing one error per field at a time.
 */
public record ValidationErrorDTO(Map<String, String> fieldErrors) {}
