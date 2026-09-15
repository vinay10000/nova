// Throwaway end-to-end check of the chat plugin path against a LOCAL backend.
// Creates a probe user, copies the real connection blobs onto it, then chats.
import pg from 'pg';
const { Client } = pg;
const base = process.argv[2] ?? 'http://127.0.0.1:3000';
const email = `probe_e2e_${Date.now()}@example.com`;

const c = new Client({ connectionString: process.env.DATABASE_URL });
await c.connect();

const j = async (path, init) => {
  const res = await fetch(`${base}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  const text = await res.text();
  let body; try { body = JSON.parse(text); } catch { body = text; }
  return { status: res.status, body };
};

const reg = await j('/v1/auth/register', { method: 'POST', body: JSON.stringify({ email, password: 'probe-password-123' }) });
if (reg.status !== 200) { console.log('register failed', reg.status, String(reg.body).slice(0, 200)); process.exit(1); }
const token = reg.body.token;
const auth = { Authorization: `Bearer ${token}` };
const me = await j('/v1/me', { headers: auth });
const userId = me.body.id;
console.log('probe user:', userId);

// Copy the real user's encrypted connection blobs onto the probe user so the
// live GitHub token (revoked) and Gmail token (valid) are exercised for real.
const copied = await c.query(
  `INSERT INTO "Connection" (id, "userId", provider, status, scopes, "providerLogin", "encryptedToken", "createdAt", "updatedAt")
   SELECT gen_random_uuid()::text, $1, provider, status, scopes, "providerLogin", "encryptedToken", now(), now()
   FROM "Connection" WHERE "userId" = (SELECT "userId" FROM "Connection" WHERE provider = 'github' ORDER BY "createdAt" ASC LIMIT 1)
   RETURNING provider, status`,
  [userId],
);
console.log('connections copied:', copied.rows);

console.log('\n/v1/connections/providers:', JSON.stringify((await j('/v1/connections/providers', { headers: auth })).body).slice(0, 400));
console.log('/v1/plugins:', JSON.stringify((await j('/v1/plugins', { headers: auth })).body).slice(0, 400));
console.log('/v1/connections/github/health:', JSON.stringify((await j('/v1/connections/github/health', { headers: auth })).body).slice(0, 200));
console.log('/v1/connections/gmail/health:', JSON.stringify((await j('/v1/connections/gmail/health', { headers: auth })).body).slice(0, 200));

const conv = await j('/v1/conversations', { method: 'POST', headers: auth, body: '{}' });
const conversationId = conv.body.id;

async function stream(label, message) {
  console.log(`\n=== ${label}: "${message}"`);
  const res = await fetch(`${base}/v1/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...auth },
    body: JSON.stringify({ conversationId, message }),
  });
  if (!res.ok) { console.log('  HTTP', res.status, (await res.text()).slice(0, 200)); return; }
  const reader = res.body.getReader();
  const dec = new TextDecoder();
  let raw = '';
  const deadline = Date.now() + 90_000;
  while (Date.now() < deadline) {
    const { done, value } = await reader.read();
    if (done) break;
    raw += dec.decode(value, { stream: true });
  }
  const frames = raw.split('\n\n').filter(Boolean).map((f) => f.replace(/^data: /, ''));
  const parsed = frames.map((f) => { try { return JSON.parse(f); } catch { return null; } }).filter(Boolean);
  const text = parsed.filter((p) => p.type === 'token').map((p) => p.text).join('');
  console.log('   text:', text.replace(/\n/g, ' ').slice(0, 400));
  console.log('   steps:', parsed.filter((p) => p.type === 'step').map((p) => p.label).join(', ') || '(none)');
  console.log('   notices:', JSON.stringify(parsed.filter((p) => p.type === 'notice')));
  const errs = parsed.filter((p) => p.type === 'error');
  if (errs.length) console.log('   ERRORS:', JSON.stringify(errs));
}

await stream('github (token revoked)', '@github list my repositories');
console.log('\ngithub status after the failed call:', JSON.stringify((await j('/v1/connections/github/status', { headers: auth })).body));
await stream('gmail (token valid)', '@gmail list my 3 most recent emails and their senders');
await stream('leetcode (server keys)', '@leetcode how many problems has alfaarghya solved?');
await stream('cache check (same list twice)', 'show me my github issues');

await c.query('DELETE FROM "User" WHERE id = $1', [userId]);
await c.query('DELETE FROM "Conversation" WHERE "userId" = $1', [userId]).catch(() => {});
console.log('\nprobe user deleted');
await c.end();