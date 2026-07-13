/**
 * @fileoverview Validation schema for report scope options.
 */
import { z } from 'zod';

/**
 * Analysis period granularity for statistics reports.
 * Must be either monthly ('MONTHLY') or per semester ('SEMESTER').
 * Values mirror the backend `StatisticsScope` enum (query param `scope`).
 */
export const ReportScopeEnum = z.enum(['MONTHLY', 'SEMESTER'], {
  errorMap: () => ({ message: 'Scope debe ser MONTHLY o SEMESTER' }),
});

export default ReportScopeEnum;
