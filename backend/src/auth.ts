import { randomBytes, scrypt, timingSafeEqual } from 'node:crypto';
import { promisify } from 'node:util';
import { SignJWT, jwtVerify } from 'jose';
import type { FastifyReply, FastifyRequest } from 'fastify';

const scryptAsync = promisify(scrypt);
const secret = () => new TextEncoder().encode(process.env.AUTH_JWT_SECRET ?? 'dev-only-change-me');

// scrypt is stdlib — no bcrypt/argon2 dependency for the same guarantee.
export async function hashPassword(password: string): Promise<string> {
  const salt = randomBytes(16);
  const key = (await scryptAsync(password, salt, 64)) as Buffer;
  return `${salt.toString('hex')}:${key.toString('hex')}`;
}

export async function verifyPassword(password: string, stored: string): Promise<boolean> {
  const [saltHex, keyHex] = stored.split(':');
  if (!saltHex || !keyHex) return false;
  const key = (await scryptAsync(password, Buffer.from(saltHex, 'hex'), 64)) as Buffer;
  const expected = Buffer.from(keyHex, 'hex');
  return key.length === expected.length && timingSafeEqual(key, expected);
}

export async function issueToken(userId: string): Promise<string> {
  return new SignJWT({ sub: userId })
    .setProtectedHeader({ alg: 'HS256' })
    .setIssuedAt()
    .setExpirationTime('30d')
    .sign(secret());
}

/**
 * §29: every request is tied to the authenticated session. The client-supplied
 * userId is NEVER trusted — it is not read at all.
 */
export async function requireUser(req: FastifyRequest, reply: FastifyReply): Promise<string | null> {
  const header = req.headers.authorization ?? '';
  const token = header.startsWith('Bearer ') ? header.slice(7) : '';
  if (!token) {
    reply.code(401).send({ error: 'unauthenticated' });
    return null;
  }
  try {
    const { payload } = await jwtVerify(token, secret());
    return String(payload.sub);
  } catch {
    reply.code(401).send({ error: 'invalid_token' });
    return null;
  }
}
