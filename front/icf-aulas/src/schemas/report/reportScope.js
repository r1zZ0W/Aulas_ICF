/**
 * @fileoverview Validation schema for report scope options.
 */
import { z } from 'zod';

/**
 * Analysis period granularity for statistics reports.
 * Must be either monthly ('MONTHLY') or per semester ('SEMESTER').
 * Values mirror the backend `StatisticsScope` enum (query param `scope`).
 *
 * NOTE: this project's zod v4.4.3 silently ignores the old `errorMap` option (no error, no
 * warning — the message just never applies, falling back to Zod's generic English message).
 * Use the v4 `error` option instead.
 */
export const ReportScopeEnum = z.enum(['MONTHLY', 'SEMESTER'], {
  error: 'Scope debe ser MONTHLY o SEMESTER',
});

export default ReportScopeEnum;
