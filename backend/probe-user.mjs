// Throwaway: reproduce the CONNECTED-user plugin path against the live backend.
// Signs a JWT with the local AUTH_JWT_SECRET for an existing user (probe only).
import { SignJWT } from 'jose';

const base = process.argv[2] ?? 'https://nova-backend-beige.vercel.app';
const userId = process.argv[3] ?? 'cmtziqwzl000004la5j7qs7x4';
const secret = new TextEncoder().encode(process.env.AUTH_JWT_SECRET);

const token = await new SignJWT({ sub: userId })
  .setProtectedHeader({ alg: 'HS256' })
  .setIssuedAt()
  .setExpirationTime('1h')
  .sign(secret);

const me = await fetch(`${base}/v1/me`, { headers: { Authorization: `Bearer ${token}` } });
console.log('/v1/me:', me.status, (await me.text()).slice(0, 120));
if (!me.ok) process.exit(1);

const conns = await fetch(`${base}/v1/connections`, { headers: { Authorization: `Bearer ${token}` } });
console.log('/v1/connections:', conns.status, (await conns.text()).slice(0, 300));

const conv = await fetch(`${base}/v1/conversations`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
  body: '{}',
});
const { id: conversationId } = await conv.json();
console.log('conversation:', conversationId);

async function stream(label, message) {
  console.log(`\n=== ${label}: "${message}"`);
  const t0 = Date.now();
  const res = await fetch(`${base}/v1/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
    body: JSON.stringify({ conversationId, message }),
  });
  if (!res.ok) {
    console.log('  HTTP', res.status, (await res.text()).slice(0, 300));
    return;
  }
  const reader = res.body.getReader();
  const dec = new TextDecoder();
  let raw = '';
  const deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    const { done, value } = await reader.read();
    if (done) break;
    raw += dec.decode(value, { stream: true });
  }
  const frames = raw.split('\n\n').filter(Boolean).map((f) => f.replace(/^data: /, ''));
  const types = {};
  for (const f of frames) { try { const t = JSON.parse(f).type; types[t] = (types[t] ?? 0) + 1; } catch {} }
  console.log('  ', ((Date.now() - t0) / 1000).toFixed(1) + 's', 'types:', JSON.stringify(types));
  const text = frames.map((f) => { try { const o = JSON.parse(f); return o.type === 'token' ? o.text : ''; } catch { return ''; } }).join('');
  console.log('   text:', text.slice(0, 400).replace(/\n/g, ' '));
  console.log('   steps:', frames.filter((f) => f.includes('"step"')).join(' | ').slice(0, 300));
  console.log('   errors:', frames.filter((f) => f.includes('"error"')).join(' | ').slice(0, 400));
}

await stream('github repos (connected)', '@github list my repositories');
await stream('gmail inbox (connected)', '@gmail summarize my recent emails');