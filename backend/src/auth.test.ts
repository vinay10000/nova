// Self-check for the security boundary + auth (ponytail: one runnable check, no framework).
// Run: npx tsx src/auth.test.ts
import assert from 'node:assert/strict';
import { hashPassword, verifyPassword, issueToken } from './auth.js';
import { jwtVerify } from 'jose';

// §29 password hashing must be salted and actually reject wrong passwords.
const h1 = await hashPassword('correct-horse-battery');
const h2 = await hashPassword('correct-horse-battery');
assert.notEqual(h1, h2, 'same password must produce different hashes (salt)');
assert.equal(await verifyPassword('correct-horse-battery', h1), true);
assert.equal(await verifyPassword('wrong-password-here', h1), false);
assert.equal(await verifyPassword('x', 'garbage'), false, 'malformed hash must not throw');

// Token must round-trip the user id — this is the ONLY source of identity (§29).
process.env.AUTH_JWT_SECRET = 'test-secret';
const token = await issueToken('user_123');
const { payload } = await jwtVerify(token, new TextEncoder().encode('test-secret'));
assert.equal(payload.sub, 'user_123');
await assert.rejects(
  () => jwtVerify(token, new TextEncoder().encode('wrong-secret')),
  'token signed with a different secret must be rejected',
);

console.log('auth checks passed');
