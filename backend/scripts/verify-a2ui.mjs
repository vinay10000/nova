/**
 * End-to-end proof that the deployed backend emits A2UI frames.
 *
 * Registers a throwaway account, opens a conversation, streams one turn that
 * should call present_surface, and prints the raw SSE frames. Run against the
 * production alias the release APK actually calls.
 *
 *   node --env-file-if-exists=.env scripts/verify-a2ui.mjs [baseUrl]
 *
 * NOTE: every run creates a real user row and a real conversation. That is the
 * point — the probe has to own a conversation to stream one — but it means this
 * is not something to run in a loop against production. Point it at a preview
 * URL while iterating and save production for one confirming run.
 */
const base = process.argv[2] ?? 'https://nova-backend-beige.vercel.app';
const stamp = Date.now().toString(36);
const email = `a2ui-probe-${stamp}@nova.test`;
const password = `Pr0be-${stamp}-Aa`;

const call = async (path, init = {}) => {
  const res = await fetch(`${base}${path}`, {
    ...init,
    headers: { 'content-type': 'application/json', ...(init.headers ?? {}) },
  });
  const text = await res.text();
  let body;
  try { body = JSON.parse(text); } catch { body = text; }
  return { status: res.status, body };
};

const fail = (where, r) => {
  console.error(`FAIL at ${where}: HTTP ${r.status}`);
  console.error(typeof r.body === 'string' ? r.body.slice(0, 400) : JSON.stringify(r.body).slice(0, 400));
  process.exit(1);
};

// 1. An account to own the conversation.
let r = await call('/v1/auth/register', { method: 'POST', body: JSON.stringify({ email, password }) });
if (r.status !== 200 && r.status !== 201) r = await call('/v1/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) });
if (r.status >= 400) fail('auth', r);
const token = r.body?.token ?? r.body?.accessToken ?? r.body?.jwt;
if (!token) { console.error('FAIL: no token in auth response:', JSON.stringify(r.body).slice(0, 300)); process.exit(1); }
console.log(`auth ok as ${email}`);

// 2. A conversation to hang the surface off.
r = await call('/v1/conversations', {
  method: 'POST',
  headers: { authorization: `Bearer ${token}` },
  body: JSON.stringify({}),
});
if (r.status >= 400) fail('create conversation', r);
const conversationId = r.body?.id ?? r.body?.conversation?.id;
if (!conversationId) { console.error('FAIL: no conversation id:', JSON.stringify(r.body).slice(0, 300)); process.exit(1); }
console.log(`conversation ${conversationId}`);

// 3. One turn, asked for something with a shape rather than a table.
const res = await fetch(`${base}/v1/chat/stream`, {
  method: 'POST',
  headers: { 'content-type': 'application/json', authorization: `Bearer ${token}` },
  body: JSON.stringify({
    conversationId,
    message:
      'Show me my trip to Tokyo as a surface. Use present_surface with one trip card ' +
      '(destination "Tokyo, Japan", dates "Jun 3 - Jun 9"), one checklist card with three items ' +
      'and all done true, and one actions card whose prompt label is "Add packing list" and name is ' +
      '"add_packing_list". Do not answer in prose.',
  }),
});
if (!res.ok) { console.error(`FAIL stream: HTTP ${res.status}`); process.exit(1); }

// 4. Read the SSE frames.
const reader = res.body.getReader();
const decoder = new TextDecoder();
let buffer = '';
const frames = [];
const tokenText = [];

while (true) {
  const { done, value } = await reader.read();
  if (done) break;
  buffer += decoder.decode(value, { stream: true });
  const parts = buffer.split('\n\n');
  buffer = parts.pop() ?? '';
  for (const part of parts) {
    const line = part.split('\n').find((l) => l.startsWith('data:'));
    if (!line) continue;
    let chunk;
    try { chunk = JSON.parse(line.slice(5).trim()); } catch { continue; }
    if (chunk.type === 'a2ui') frames.push(chunk.frame);
    if (chunk.type === 'token') tokenText.push(chunk.text ?? '');
  }
}

const prose = tokenText.join('');
console.log(`\nprose: ${prose.length} chars${prose ? ` — "${prose.slice(0, 160)}"` : ''}`);
console.log(`a2ui frames: ${frames.length}`);

if (!frames.length) {
  console.error('\nFAIL: no a2ui frames on the wire.');
  console.error('Either present_surface did not fire, or the frames were dropped.');
  process.exit(2);
}

// 5. Show what actually went over the wire.
for (const [i, f] of frames.entries()) {
  const key = Object.keys(f).find((k) => k !== 'version');
  console.log(`\nframe ${i + 1}: version=${f.version} ${key}`);
  console.log(JSON.stringify(f[key], null, 2).split('\n').slice(0, 26).join('\n'));
}

// 6. Assert the three frames, in order, with a root and real bindings.
const [create, update, data] = frames;
const problems = [];
if (!create?.createSurface) problems.push('frame 1 is not createSurface');
if (!create?.createSurface?.catalogId) problems.push('createSurface has no catalogId');
if (!update?.updateComponents) problems.push('frame 2 is not updateComponents');
const components = update?.updateComponents?.components ?? [];
if (!components.some((c) => c.id === 'root')) problems.push('no component with id "root"');
for (const childId of components.find((c) => c.id === 'root')?.children ?? []) {
  if (!components.some((c) => c.id === childId)) problems.push(`dangling child ${childId}`);
}
if (!data?.updateDataModel) problems.push('frame 3 is not updateDataModel');

// Every binding must point somewhere that has a value behind it.
const flat = (obj, prefix = '') =>
  Object.entries(obj ?? {}).flatMap(([k, v]) =>
    v && typeof v === 'object' && !Array.isArray(v) ? flat(v, `${prefix}/${k}`) : [`${prefix}/${k}`, v]);
const model = data?.updateDataModel?.value ?? {};
const lookup = (pointer) =>
  pointer.split('/').filter(Boolean).reduce((acc, seg) => (acc == null ? acc : acc[seg]), model);
for (const c of components) {
  for (const [prop, value] of Object.entries(c)) {
    if (prop === 'id' || prop === 'component') continue;
    if (value && typeof value === 'object' && typeof value.path === 'string') {
      if (lookup(value.path) === undefined) problems.push(`${c.id}.${prop} binds ${value.path} but the data model has nothing there`);
    }
  }
}

if (problems.length) {
  console.error('\nFAIL:');
  for (const p of problems) console.error(`  - ${p}`);
  process.exit(3);
}

console.log('\nPASS: three frames in order, rooted tree, every binding resolves.');