/**
 * @fileoverview Validation schema for login responses.
 */
import { z } from 'zod';

/**
 * Zod schema for validating the authentication success payload.
 * Matches backend LoginResponse contract exactly, including tokens and user metadata.
 */
export const LoginResponseSchema = z.object({
  token: z.string(),
  refreshToken: z.string(),
  uuid: z.string(),
  name: z.string(),
  role: z.string(),
  email: z.string(),
});

export default LoginResponseSchema;