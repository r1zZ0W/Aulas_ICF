/**
 * @fileoverview Shared hook for Zod-backed form validation across the app.
 *
 * Validation strategy:
 *   - onBlur  → marks the field as "touched" and runs a full safeParse. The
 *               field's error becomes visible from this point on.
 *   - onChange → always updates the value. If the field is already touched it
 *               immediately re-validates so the red border disappears the
 *               instant the user fixes the value (no second blur needed).
 *   - validateAll → marks EVERY key as touched and runs a full safeParse.
 *               Called at submit time so a user who never touched a field
 *               still sees all required-field errors at once.
 *
 * Key implementation detail:
 *   Zod always validates the *whole* object (schema.safeParse(formData)), never
 *   a single field in isolation. This preserves cross-field refinements (.refine)
 *   and avoids false positives from shape[field].safeParse(value).
 *
 * Server errors:
 *   `setServerErrors` (called from useApiMutation's onError) injects errors the
 *   backend found that Zod never could — a duplicate email, a slot conflict — and
 *   they take priority over Zod's own errors for the same field, since a server
 *   error is more specific than "this looked fine locally".
 *
 *   Two functions change a field's value: `handleChange` is for user-driven
 *   input (wired to onChange/onSelect handlers) and clears that field's server
 *   error (and its whole 1:N group, see `dtoMap` below) on the assumption the
 *   user is fixing it. `syncFieldValue` is for *programmatic* updates — a
 *   `useEffect` autoselecting a room, a computed default — and never touches
 *   server errors, because the user didn't do anything that should invalidate
 *   the server's verdict. Connecting a UI control (Select, DatePicker, Combobox)
 *   to `syncFieldValue` instead of `handleChange` is the one way to reintroduce
 *   the stuck-error bug this split exists to prevent — if a control's value
 *   changes because of a person, it goes through `handleChange`.
 *
 * dtoMap (optional):
 *   Maps backend DTO field names to form field names, needed wherever they
 *   diverge (e.g. `classroomUuid` -> `roomId`) or where one DTO field maps to
 *   several form controls (`timeSlotIds` -> `['startLabel', 'endLabel']`, a 1:N
 *   group). `setServerErrors` is called with the *raw* fieldErrors map keyed by
 *   DTO field names — this hook does the translation, so callers never
 *   hand-translate keys. When a field belongs to a group, clearing one member's
 *   server error clears every member's, and the group's remaining Zod error (if
 *   any) is revealed on the same render — the indicator is replaced, not removed
 *   for an instant.
 */

import { useState, useRef, useCallback } from 'react';

// ── Helpers ────────────────────────────────────────────────────────────────────

/**
 * Reduces a ZodError's issues into a flat `{ fieldName: firstMessage }` map.
 * Only the first error per field is kept to avoid saturating the UI.
 *
 * @param {import('zod').SafeParseReturnType} result
 * @returns {Record<string, string>}
 */
function extractErrors(result) {
  if (result.success) return {};
  return result.error.issues.reduce((acc, issue) => {
    const field = issue.path[0];
    if (field != null && !acc[field]) acc[field] = issue.message;
    return acc;
  }, {});
}

/**
 * Returns every form field in the same dtoMap group as `field` (including itself).
 * A field with no group of its own is just `[field]`.
 *
 * @param {string} field - a *form* field name
 * @param {Record<string, string|string[]|null>|undefined} dtoMap
 * @returns {string[]}
 */
function groupOf(field, dtoMap) {
  if (!dtoMap) return [field];
  for (const target of Object.values(dtoMap)) {
    if (Array.isArray(target) && target.includes(field)) return target;
  }
  return [field];
}

/**
 * Translates a backend `{ dtoField: message }` map into form field messages,
 * expanding 1:N groups so every member control gets the same message, and keeping
 * the *first* message when several DTO fields resolve to the same form field (N:1 —
 * e.g. two backend fields feeding one combined picker), matching extractErrors'
 * own "first issue per field" rule.
 *
 * A DTO field explicitly mapped to `null` in `dtoMap` (documented as "no control
 * for this") produces no form entry — its message is reported back as an "orphan"
 * instead, so the caller (useApiMutation) can still surface it via toast rather
 * than silently dropping information the server did send.
 *
 * @param {Record<string, string>} rawFieldErrors - keyed by DTO field name
 * @param {Record<string, string|string[]|null>|undefined} dtoMap
 * @returns {{ formErrors: Record<string, string>, orphanMessages: string[] }}
 */
function translateServerErrors(rawFieldErrors, dtoMap) {
  const formErrors = {};
  const orphanMessages = [];
  for (const [dtoField, message] of Object.entries(rawFieldErrors)) {
    const hasMapping = dtoMap && dtoField in dtoMap;
    const target = hasMapping ? dtoMap[dtoField] : dtoField;
    if (target == null) {
      orphanMessages.push(message);
      continue;
    }
    const formFields = Array.isArray(target) ? target : [target];
    for (const formField of formFields) {
      if (!(formField in formErrors)) formErrors[formField] = message;
    }
  }
  return { formErrors, orphanMessages };
}

// ── Hook ───────────────────────────────────────────────────────────────────────

/**
 * @template {Record<string, unknown>} T
 *
 * @param {T} initialValues - Initial form state (plain object).
 * @param {import('zod').ZodSchema<any>} schema - Zod schema to validate against.
 * @param {object} [options]
 * @param {(data: T) => object} [options.preprocess] - Optional transform applied to
 *   the form data **before** calling safeParse. Use this when the schema expects
 *   already-typed values (e.g. `number`) while the form state holds raw strings.
 *   The preprocess runs inside every blur, change (when touched), and validateAll.
 * @param {Record<string, string|string[]|null>} [options.dtoMap] - Backend DTO field
 *   name -> form field name(s). See the file-level doc comment.
 *
 * @returns {{
 *   formData: T,
 *   errors: Record<string, string | undefined>,
 *   touched: Record<string, boolean>,
 *   handleChange: (field: string, value: unknown) => void,
 *   syncFieldValue: (field: string, value: unknown) => void,
 *   handleBlur: (field: string) => void,
 *   validateAll: () => boolean,
 *   reset: (newValues?: T) => void,
 *   setServerErrors: (rawFieldErrors: Record<string, string>) => void,
 * }}
 */
export function useZodForm(initialValues, schema, { preprocess, dtoMap } = {}) {
  const [formData, setFormData] = useState(initialValues);
  const [touched, setTouched] = useState({});
  // allErrors holds every Zod error for the current formData, whether the
  // field has been touched or not. Visibility is controlled by `touched`.
  const [allErrors, setAllErrors] = useState({});
  // Errors the *server* reported, already translated to form field names.
  // Take priority over allErrors for the same field — see file-level doc comment.
  const [serverErrors, setServerErrorsState] = useState({});

  // Ref so validateAll / handleChange can always read the *latest* formData
  // without capturing a stale closure.
  const formDataRef = useRef(initialValues);

  /** Applies the optional preprocess transform and calls safeParse. */
  const runParse = useCallback(
    (data) => schema.safeParse(preprocess ? preprocess(data) : data),
    [schema, preprocess],
  );

  // ── Derived errors (only for fields already touched) ────────────────────────
  const errors = {};
  for (const key of new Set([...Object.keys(allErrors), ...Object.keys(serverErrors)])) {
    if (!touched[key]) continue;
    errors[key] = serverErrors[key] ?? allErrors[key];
  }

  // ── Handlers ────────────────────────────────────────────────────────────────

  /**
   * Updates a single field value without touching server errors. For
   * *programmatic* updates only (useEffect autoselection, computed defaults) —
   * see the file-level doc comment for why this must never be wired to a UI
   * control's onChange.
   */
  const syncFieldValue = useCallback(
    (field, value) => {
      const next = { ...formDataRef.current, [field]: value };
      formDataRef.current = next;
      setFormData(next);

      setTouched((prevTouched) => {
        if (prevTouched[field]) {
          setAllErrors(extractErrors(runParse(next)));
        }
        return prevTouched;
      });
    },
    [runParse],
  );

  /**
   * Updates a single field value in response to user interaction.
   * If the field has already been touched, immediately re-validates the whole
   * form so the error disappears while the user is typing (no second blur needed).
   * Also clears the server error for this field's whole dtoMap group — see the
   * file-level doc comment on why this is scoped to the group, not just the field.
   */
  const handleChange = useCallback(
    (field, value) => {
      syncFieldValue(field, value);

      setServerErrorsState((prev) => {
        const group = groupOf(field, dtoMap);
        if (!group.some((f) => f in prev)) return prev; // nothing to clear
        const next = { ...prev };
        for (const f of group) delete next[f];
        return next;
      });
    },
    [syncFieldValue, dtoMap],
  );

  /**
   * Marks a field as touched (errors become visible for it) and runs a full
   * validation pass so the error message is ready when the field is rendered.
   */
  const handleBlur = useCallback(
    (field) => {
      const result = runParse(formDataRef.current);
      setAllErrors(extractErrors(result));
      setTouched((prev) => ({ ...prev, [field]: true }));
    },
    [runParse],
  );

  /**
   * Forces every form key to be "touched" and performs a full validation pass.
   * Call this at submit time so that a user who clicked "Guardar" without ever
   * interacting with any field still sees every required-field error highlighted.
   *
   * @returns {boolean} `true` when the form passes validation; `false` otherwise.
   */
  const validateAll = useCallback(() => {
    const current = formDataRef.current;
    const result = runParse(current);
    const newErrors = extractErrors(result);

    setAllErrors(newErrors);
    // A fresh submit re-validates from scratch — any prior server verdict no
    // longer applies to whatever the user has changed since.
    setServerErrorsState({});

    // Mark every key as touched so all visible errors appear simultaneously.
    const allTouched = {};
    for (const key of Object.keys(current)) allTouched[key] = true;
    // Also touch any extra keys that only appear in the error map (e.g. from
    // schema-level refinements that produce paths not present in the raw state).
    for (const key of Object.keys(newErrors)) allTouched[key] = true;
    setTouched(allTouched);

    return result.success;
  }, [runParse]);

  /**
   * Resets the form to a fresh state, clearing touched flags and errors.
   * @param {T} [newValues] - New initial values. Defaults to the original `initialValues`.
   */
  const reset = useCallback(
    (newValues) => {
      const vals = newValues ?? initialValues;
      formDataRef.current = vals;
      setFormData(vals);
      setTouched({});
      setAllErrors({});
      setServerErrorsState({});
    },
    [initialValues],
  );

  /**
   * Injects server-reported field errors (raw, DTO-field-keyed) — see the
   * file-level doc comment. Marks every affected form field as touched so the
   * message is visible immediately, without waiting for a blur.
   *
   * @param {Record<string, string>} rawFieldErrors - `{ dtoField: message }`
   * @returns {{ applied: string[], orphanMessages: string[] }} `applied` lists the
   *   form fields that received a message; `orphanMessages` lists messages whose
   *   DTO field maps to no control (`dtoMap[dtoField] === null`) — useApiMutation
   *   surfaces those via toast instead of silently dropping them.
   */
  const setServerErrors = useCallback(
    (rawFieldErrors) => {
      const { formErrors, orphanMessages } = translateServerErrors(rawFieldErrors, dtoMap);
      const applied = Object.keys(formErrors);

      if (applied.length > 0) {
        setServerErrorsState((prev) => ({ ...prev, ...formErrors }));
        setTouched((prev) => {
          const next = { ...prev };
          for (const f of applied) next[f] = true;
          return next;
        });
      }
      return { applied, orphanMessages };
    },
    [dtoMap],
  );

  return {
    formData, errors, touched,
    handleChange, syncFieldValue, handleBlur, validateAll, reset,
    setServerErrors,
  };
}
