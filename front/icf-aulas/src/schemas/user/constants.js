/**
 * @fileoverview Validation constants and regular expressions for user-related fields.
 * Mirrors requirements and validation rules defined on the backend.
 */

/** Restricts email to institutional ICF UNAM domains. */
export const ICF_EMAIL_REGEX = /^[^\s@]+@icf\.unam\.mx$/;

/**
 * Enforces a strong password policy matching backend @Pattern annotations.
 * Requires at least 8 and up to 128 characters, with at least:
 * - One lowercase letter
 * - One uppercase letter
 * - One digit
 * - One special character from the set [@$!%*?&#^()_\-+=]
 */
export const STRONG_PASSWORD_REGEX =
  /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&#^()_\-+=])[A-Za-z\d@$!%*?&#^()_\-+=]{8,128}$/;

/**
 * Validates usernames to ensure they:
 * - Start with an alphanumeric character
 * - Contain only letters, numbers, periods, hyphens, and underscores
 * - Are between 3 and 50 characters in length
 */
export const USERNAME_REGEX = /^[a-zA-Z0-9][a-zA-Z0-9._-]{2,49}$/;

/** Validates names and surnames to allow letters (including accented characters), spaces, and hyphens up to 100 characters. */
export const NAME_REGEX = /^[a-zA-ZÀ-ÿ '-]{1,100}$/;
