/**
 * @fileoverview Compatibility barrel file re-exporting reservation-related validation schemas.
 */
export { DayOfWeekEnum, ReservationGroupStatusEnum, ReservInstanceStatusEnum } from './reservation/enums.js';
export { default as default, ReservationGroupRequestSchema } from './reservation/reservationGroupRequest.js';
export { ReservationGroupResponseSchema } from './reservation/reservationGroupResponse.js';
export { ReservInstanceRequestSchema } from './reservation/reservInstanceRequest.js';
export { ReservInstanceResponseSchema } from './reservation/reservInstanceResponse.js';
export { BookingRequestSchema } from './reservation/bookingRequest.js';
export { ReassignRequestSchema } from './reservation/reassignRequest.js';
export { ReservSlotRequestSchema } from './reservation/reservSlotRequest.js';
export { ReservSlotResponseSchema } from './reservation/reservSlotResponse.js';
export { StudentResponseSchema } from './reservation/studentResponse.js';
