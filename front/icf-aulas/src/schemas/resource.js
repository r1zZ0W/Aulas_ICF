/**
 * @fileoverview Compatibility barrel file re-exporting inventory resource validation schemas.
 */
export { default as ResourceRequestSchema } from './resource/resourceRequest.js';
export { default as ResourceResponseSchema } from './resource/resourceResponse.js';
export { default as ResourceStatsSchema } from './resource/resourceStats.js';
export { ClassroomResourceResponseSchema } from './resource/classroomResourceResponse.js';
export { ClassroomResourceMutationSchema } from './resource/classroomResourceMutation.js';
export { ResourceCatalogItemSchema } from './resource/resourceCatalogItem.js';
