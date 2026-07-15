/**
 * Central re-export barrel for all Zod validation schemas and helpers.
 * Import schemas from this file rather than from individual schema modules.
 */

// User schemas
export { UserCreateSchema, UserUpdateSchema, default as UserRequestSchema, UserSelfEditSchema } from './user/userRequest.js';
export { default as UserResponseSchema } from './user/userResponse.js';
export { default as UserStatsSchema } from './user/userStats.js';

// Login schemas
export { default as LoginRequestSchema } from './login/loginRequest.js';
export { default as LoginResponseSchema } from './login/loginResponse.js';

// Role schemas
export { default as RoleResponseSchema } from './role.js';

// Generic Paged Result helper
export { PagedResultSchema } from './pagedResult.js';

// Classroom schemas & helpers
export {
  CLASSROOM_TYPES_MAP,
  CLASSROOM_TYPES,
  typeLabel,
  default as ClassroomRequestSchema,
  ClassroomResponseSchema,
  ClassroomStatsSchema,
} from './classroom.js';

// Report schemas
export {
  ReportScopeEnum,
  AvailableMonthsSchema,
  default as ReservationStatisticsSchema,
} from './report.js';

// Reservation schemas
export {
  DayOfWeekEnum,
  ReservationGroupStatusEnum,
  ReservInstanceStatusEnum,
  default as ReservationGroupRequestSchema,
  ReservationGroupResponseSchema,
  ReservInstanceRequestSchema,
  ReservInstanceResponseSchema,
  BookingRequestSchema,
  ReassignRequestSchema,
  ReservSlotRequestSchema,
  ReservSlotResponseSchema,
} from './reservation.js';

// Resource schemas
export {
  default as ResourceSchema,
  ClassroomResourceRequestSchema,
  ClassroomResourceResponseSchema,
  ClassroomResourceMutationSchema,
  ResourceCatalogItemSchema,
} from './resource.js';

// Semester schemas
export {
  default as SemesterRequestSchema,
  SemesterResponseSchema,
} from './semester.js';

// TimeSlot schemas
export {
  default as TimeSlotSchema,
} from './timeSlot.js';
