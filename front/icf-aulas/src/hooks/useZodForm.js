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
 *
 * @returns {{
 *   formData: T,
 *   errors: Record<string, string | undefined>,
 *   touched: Record<string, boolean>,
 *   handleChange: (field: string, value: unknown) => void,
 *   handleBlur: (field: string) => void,
 *   validateAll: () => boolean,
 *   reset: (newValues?: T) => void,
 * }}
 */
export function useZodForm(initialValues, schema, { preprocess } = {}) {
  const [formData, setFormData] = useState(initialValues);
  const [touched, setTouched] = useState({});
  // allErrors holds every Zod error for the current formData, whether the
  // field has been touched or not. Visibility is controlled by `touched`.
  const [allErrors, setAllErrors] = useState({});

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
  for (const [key, val] of Object.entries(allErrors)) {
    if (touched[key]) errors[key] = val;
  }

  // ── Handlers ────────────────────────────────────────────────────────────────

  /**
   * Updates a single field value.
   * If the field has already been touched, immediately re-validates the whole
   * form so the error disappears while the user is typing (no second blur needed).
   */
  const handleChange = useCallback(
    (field, value) => {
      const next = { ...formDataRef.current, [field]: value };
      formDataRef.current = next;
      setFormData(next);

      // Re-validate only when the field has already been marked as touched.
      // Using the functional updater lets us read the latest `touched` without
      // creating a closure over it.
      setTouched((prevTouched) => {
        if (prevTouched[field]) {
          setAllErrors(extractErrors(runParse(next)));
        }
        return prevTouched; // touched itself does not change here
      });
    },
    [runParse],
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
    },
    [initialValues],
  );

  return { formData, errors, touched, handleChange, handleBlur, validateAll, reset };
}
