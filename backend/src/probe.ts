// Live end-to-end probe (§50 milestone): register -> create conversation -> stream.
//   npx tsx src/probe.ts <baseUrl>
const base = process.argv[2] ?? 'http://127.0.0.1:3000';
const email = `probe_${Date.now()}@example.com`;

const j = async (path: string, init?: RequestInit) => {
  const res = await fetch(`${base}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  return { status: res.status, body: await res.text() };
};

// 1. register (unique email per run)
const reg = await j('/v1/auth/register', { method: 'POST', body: JSON.stringify({ email, password: 'probe-password-123' }) });
console.log('register  :', reg.status, reg.body.slice(0, 100));
if (reg.status !== 200) process.exit(1);
const token = (JSON.parse(reg.body) as { token: string }).token;
const auth = { Authorization: `Bearer ${token}` };

// 2. duplicate email must be rejected
const dupe = await j('/v1/auth/register', { method: 'POST', body: JSON.stringify({ email, password: 'probe-password-123' }) });
console.log('duplicate :', dupe.status, dupe.body.slice(0, 60));

// 3. login round-trips
const login = await j('/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password: 'probe-password-123' }) });
console.log('login     :', login.status);

// 4. wrong password must fail
const bad = await j('/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password: 'wrong-password-xx' }) });
console.log('bad login :', bad.status, bad.body.slice(0, 60));

// 5. create conversation
const conv = await j('/v1/conversations', { method: 'POST', headers: auth, body: '{}' });
console.log('create    :', conv.status, conv.body.slice(0, 100));
const conversationId = (JSON.parse(conv.body) as { id: string }).id;

// 6. isolation: a SECOND user must not read the first user's conversation (§29)
const reg2 = await j('/v1/auth/register', { method: 'POST', body: JSON.stringify({ email: `other_${Date.now()}@example.com`, password: 'probe-password-123' }) });
const token2 = (JSON.parse(reg2.body) as { token: string }).token;
const steal = await j(`/v1/conversations/${conversationId}`, { headers: { Authorization: `Bearer ${token2}` } });
console.log('isolation :', steal.status, steal.body.slice(0, 60), '(expect 404)');

// 7. rename
const ren = await j(`/v1/conversations/${conversationId}`, { method: 'PATCH', headers: auth, body: JSON.stringify({ title: 'Renamed by probe' }) });
console.log('rename    :', ren.status);

// 8. streaming (needs GEMINI_API_KEY; the SSE framing is what we assert on)
console.log('stream    : opening SSE...');
const sres = await fetch(`${base}/v1/chat/stream`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', ...auth },
  body: JSON.stringify({ conversationId, message: 'Say hello in exactly three words.' }),
});
console.log('stream    : content-type =', sres.headers.get('content-type'));
if (!sres.body) { console.log('stream    : NO BODY'); process.exit(1); }
const reader = sres.body.getReader();
const dec = new TextDecoder();
let raw = '';
const deadline = Date.now() + 45_000;
while (Date.now() < deadline) {
  const { done, value } = await reader.read();
  if (done) break;
  raw += dec.decode(value, { stream: true });
  if (raw.includes('"done"') || raw.includes('"error"')) break;
}
const frames = raw.split('\n\n').filter(Boolean);
console.log('stream    : frames =', frames.length);
for (const f of frames.slice(0, 6)) console.log('           ', f.replace(/\n/g, ' ').slice(0, 110));
const doneFrame = frames.find((f) => f.includes('"done"'));
console.log('stream    : terminal frame =', doneFrame ? 'done' : frames.some((f) => f.includes('"error"')) ? 'error' : 'none');
