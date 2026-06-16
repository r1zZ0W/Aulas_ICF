import { z } from 'zod';

/**
 * @fileoverview Schema for validating login responses.
 * Validates the structure of a login response.
 */

/**
 * @typedef {z.infer<typeof LoginResponseSchema>} LoginResponse
 */
const LoginResponseSchema = z.object({
    token: z.string(),
    refreshToken: z.string(),
    uuid: z.string(),
    name: z.string(),
    role: z.string(),
    email: z.string()
});

export default LoginResponseSchema;