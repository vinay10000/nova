// Throwaway probe: what actually happens on the @plugin chat path.
//   node probe-plugin.mjs <baseUrl>
const base = process.argv[2] ?? 'http://127.0.0.1:3000';
const email = `probe_plugin_${Date.now()}@example.com`;

const j = async (path, init) => {
  const res = await fetch(`${base}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  return { status: res.status, body: await res.text() };
};

const reg = await j('/v1/auth/register', { method: 'POST', body: JSON.stringify({ email, password: 'probe-password-123' }) });
if (reg.status !== 200) {
  console.log('register failed:', reg.status, reg.body.slice(0, 200));
  process.exit(1);
}
const token = JSON.parse(reg.body).token;
const auth = { Authorization: `Bearer ${token}` };
const conv = await j('/v1/conversations', { method: 'POST', headers: auth, body: '{}' });
const conversationId = JSON.parse(conv.body).id;
console.log('conversation:', conversationId);

async function stream(label, message) {
  console.log(`\n=== ${label}: "${message}"`);
  const t0 = Date.now();
  const res = await fetch(`${base}/v1/chat/stream`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...auth },
    body: JSON.stringify({ conversationId, message }),
  });
  if (!res.ok) {
    console.log('  HTTP', res.status, (await res.text()).slice(0, 200));
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
  console.log('    raw tail:', raw.slice(-300).replace(/\n/g, ' '));
  const counts = {};
  for (const f of frames) {
    try { const t = JSON.parse(f).type; counts[t] = (counts[t] ?? 0) + 1; } catch {}
  }
  console.log('  ', ((Date.now() - t0) / 1000).toFixed(1) + 's', 'chunk types:', JSON.stringify(counts));
  const text = frames.filter((f) => f.includes('"token"')).map((f) => JSON.parse(f).text).join('');
  console.log('   text:', text.slice(0, 300).replace(/\n/g, ' '));
  const errs = frames.filter((f) => f.includes('"error"'));
  if (errs.length) console.log('   ERROR frames:', errs.slice(0, 3).join(' | ').slice(0, 400));
  const steps = frames.filter((f) => f.includes('"step"'));
  if (steps.length) console.log('   STEP frames:', steps.join(' | ').slice(0, 400));
}

await stream('baseline plain', 'Say hi in exactly three words.');
await stream('explicit mention', '@github list my repositories');
await stream('keyword fallback', 'show me my github issues');