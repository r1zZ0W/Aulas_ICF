/**
 * Normalized error shape every API call rejects with, replacing the old pattern of
 * flattening HttpError into a plain Error (which discarded status/code/data before the
 * UI ever saw them).
 */
export class ApiError extends Error {
  /**
   * @param {Object} params
   * @param {string} [params.code] - Stable ErrorCode name from the backend (or a client-side
   *   code like CONTRACT_MISMATCH), used to look up Spanish text in errorCatalog.
   * @param {string} params.message - Spanish text ready to show the user (never raw server prose).
   * @param {number} params.status - HTTP status, or 0 for network/client-side errors.
   * @param {Record<string, string>|null} [params.fieldErrors] - field name -> Spanish text,
   *   already translated from ErrorCode names via the catalog.
   * @param {*} [params.data] - Typed payload from the server (e.g. ConflictDetailDTO), for
   *   call sites that need more than the message.
   */
  constructor({ code, message, status, fieldErrors = null, data = null }) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.data = data;
  }
}
