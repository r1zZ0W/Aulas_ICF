/**
 * @fileoverview Compatibility barrel file re-exporting classroom-related schemas and helpers.
 */
export { CLASSROOM_TYPES_MAP, CLASSROOM_TYPES, typeLabel } from './classroom/classroomTypes.js';
export { default as default, ClassroomRequestSchema } from './classroom/classroomRequest.js';
export { ClassroomResponseSchema } from './classroom/classroomResponse.js';
export { ClassroomStatsSchema } from './classroom/classroomStats.js';
