import { randomBytes, createCipheriv, createDecipheriv } from 'node:crypto';

const ALGORITHM = 'aes-256-gcm';
const IV_LENGTH = 12;
const AUTH_TAG_LENGTH = 16;

function getMasterKey(): Buffer {
  const key = process.env.OAUTH_ENCRYPTION_KEY;
  if (!key) throw new Error('OAUTH_ENCRYPTION_KEY is required for token encryption');
  // Support hex (64 hex chars = 32 bytes) or base64
  if (/^[0-9a-f]{64}$/i.test(key)) return Buffer.from(key, 'hex');
  return Buffer.from(key, 'base64');
}

/**
 * §46: AES-256-GCM envelope encryption for OAuth tokens at rest.
 * Format: iv(12) + authTag(16) + ciphertext — concat into a single base64 string.
 */
export function encryptToken(plaintext: string): string {
  const key = getMasterKey();
  const iv = randomBytes(IV_LENGTH);
  const cipher = createCipheriv(ALGORITHM, key, iv, { authTagLength: AUTH_TAG_LENGTH });
  const encrypted = Buffer.concat([cipher.update(plaintext, 'utf8'), cipher.final()]);
  const authTag = cipher.getAuthTag();
  return Buffer.concat([iv, authTag, encrypted]).toString('base64');
}

/**
 * §46: Decrypt an envelope-encrypted token. Used only inside tool execution —
 * never in logs, never in ExecutionStep.metadata, never returned by any API.
 */
export function decryptToken(encoded: string): string {
  const key = getMasterKey();
  const buf = Buffer.from(encoded, 'base64');
  if (buf.length < IV_LENGTH + AUTH_TAG_LENGTH + 1) throw new Error('encrypted token too short');
  const iv = buf.subarray(0, IV_LENGTH);
  const authTag = buf.subarray(IV_LENGTH, IV_LENGTH + AUTH_TAG_LENGTH);
  const ciphertext = buf.subarray(IV_LENGTH + AUTH_TAG_LENGTH);
  const decipher = createDecipheriv(ALGORITHM, key, iv, { authTagLength: AUTH_TAG_LENGTH });
  decipher.setAuthTag(authTag);
  const decrypted = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
  return decrypted.toString('utf8');
}

/**
 * §38: Generate a cryptographically random state parameter for OAuth CSRF protection.
 */
export function generateOAuthState(): string {
  return randomBytes(32).toString('hex');
}

/**
 * §38 PKCE: Generate a code_verifier (43-128 chars from unreserved characters).
 */
export function generateCodeVerifier(): string {
  const buf = randomBytes(32);
  return buf.toString('base64url');
}
