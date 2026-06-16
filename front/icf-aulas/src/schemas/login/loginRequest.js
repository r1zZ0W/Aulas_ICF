import { z } from 'zod';

/**
 * @fileoverview Schema for validating login request.
 * Validates the structure of a login request.
 */

/**
 * @typedef {z.infer<typeof LoginRequestSchema>} LoginRequest
 * Mantain a consistent naming pattern with backend: username, password.
 */
const LoginRequestSchema = z.object({
    username: z
        .string(),
    password: z
        .string()
});

export default LoginRequestSchema;