// §51 acceptance probe: upload real files -> chat with attachments -> assert real Gemini response.
//   npx tsx src/probe-files.ts [baseUrl]
import { deflateRawSync } from 'node:zlib';
import { readFileSync } from 'node:fs';

const base = process.argv[2] ?? 'http://127.0.0.1:3000';
const email = `probe2_${Date.now()}@example.com`;
const crc32 = (b: Buffer) => {
  let c = ~0;
  for (const x of b) { c ^= x; for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1)); }
  return ~c >>> 0;
};

function zipOf(name: string, content: string): Buffer {
  const raw = Buffer.from(content), comp = deflateRawSync(raw), n = Buffer.from(name);
  const local = Buffer.alloc(30 + n.length);
  local.writeUInt32LE(0x04034b50, 0); local.writeUInt16LE(20, 4); local.writeUInt16LE(8, 8);
  local.writeUInt32LE(crc32(raw), 14); local.writeUInt32LE(comp.length, 18); local.writeUInt32LE(raw.length, 22);
  local.writeUInt16LE(n.length, 26); n.copy(local, 30);
  const central = Buffer.alloc(46 + n.length);
  central.writeUInt32LE(0x02014b50, 0); central.writeUInt16LE(20, 4); central.writeUInt16LE(20, 6);
  central.writeUInt16LE(8, 10); central.writeUInt32LE(crc32(raw), 16); central.writeUInt32LE(comp.length, 20);
  central.writeUInt32LE(raw.length, 24); central.writeUInt16LE(n.length, 28); central.writeUInt32LE(0, 42);
  n.copy(central, 46);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0); eocd.writeUInt16LE(1, 8); eocd.writeUInt16LE(1, 10);
  eocd.writeUInt32LE(central.length, 12); eocd.writeUInt32LE(local.length + comp.length, 16);
  return Buffer.concat([local, comp, central, eocd]);
}

/** Minimal single-page PDF with correct xref offsets. */
function pdfOf(text: string): Buffer {
  const stream = `BT /F1 12 Tf 72 720 Td (${text}) Tj ET`;
  const objs = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >>',
    `<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}\nendstream`,
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
  ];
  const offsets: number[] = [];
  let body = '';
  objs.forEach((o, i) => { offsets.push(9 + body.length); body += `${i + 1} 0 obj\n${o}\nendobj\n`; });
  const xref = offsets.map((o) => `${String(o).padStart(10, '0')} 00000 n \n`).join('');
  return Buffer.from(
    `%PDF-1.4\n${body}xref\n0 ${objs.length + 1}\n0000000000 65535 f \n${xref}trailer\n<< /Size ${objs.length + 1} /Root 1 0 R >>\nstartxref\n${9 + body.length}\n%%EOF\n`,
  );
}

const screenshot = readFileSync(new URL('../../nova-screen.png', import.meta.url));

const res = await fetch(`${base}/v1/auth/register`, {
  method: 'POST', headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email, password: 'probe-password-123' }),
});
const token = (await res.json() as { token: string }).token;
const auth = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
const conv = await (await fetch(`${base}/v1/conversations`, { method: 'POST', headers: auth, body: '{}' })).json() as { id: string };

async function upload(name: string, mime: string, buf: Buffer) {
  const fd = new FormData();
  fd.append('file', new Blob([new Uint8Array(buf)], { type: mime }), name);
  const r = await fetch(`${base}/v1/files`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: fd });
  const body = await r.json() as { id?: string; filename?: string; status?: string; error?: string };
  console.log(`upload ${name.padEnd(12)}:`, r.status, body.status ?? body.error, body.filename);
  return body.id!;
}

// §9 vision acceptance: a real app screenshot -> "explain the error".
// §10 document acceptance: resume PDF -> rewrite, DOCX + CSV -> extraction path.
const png = await upload('screenshot.png', 'image/png', screenshot);
const pdf = await upload('resume.pdf', 'application/pdf', pdfOf('Nova Resume: software engineer, 5 years of Kotlin and TypeScript, built agentic apps.'));
const docx = await upload('summary.docx', 'application/octet-stream', zipOf('word/document.xml', '<w:doc><w:p><w:t>Summary: I ship small things fast.</w:t></w:p><w:p><w:t>I like boring code.</w:t></w:p></w:doc>'));
const csv = await upload('data.csv', 'text/csv', Buffer.from('month,revenue\nJan,10\nFeb,25\nMar,40\n'));

// §46/§10: a zip that is NOT DOCX (jar/xlsx flavour) must be rejected by magic sniffing.
const fdRej = new FormData();
fdRej.append('file', new Blob([new Uint8Array([0x50, 0x4b, 0x03, 0x04, 0x00])], { type: 'application/octet-stream' }), 'app.jar');
const rej = await fetch(`${base}/v1/files`, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, body: fdRej });
console.log('reject jar  :', rej.status, (await rej.json() as { error?: string }).error, '(expect unsupported_type)');

async function chat(label: string, message: string, attachmentIds: string[]) {
  const sres = await fetch(`${base}/v1/chat/stream`, {
    method: 'POST', headers: auth,
    body: JSON.stringify({ conversationId: conv.id, message, attachmentIds }),
  });
  const reader = sres.body!.getReader();
  const dec = new TextDecoder();
  let raw = '', deadline = Date.now() + 60_000;
  while (Date.now() < deadline) {
    const { done, value } = await reader.read();
    if (done) break;
    raw += dec.decode(value, { stream: true });
    if (raw.includes('"done"') || raw.includes('"error"')) break;
  }
  const tokens = raw.split('\n\n').filter((f) => f.includes('"token"')).length;
  const bad = raw.includes('"error"');
  console.log(`chat ${label.padEnd(10)}: tokens=${tokens} terminal=${bad ? 'ERROR' : 'done'}`);
  const firstText = raw.match(/"text":"([^"]{0,90})/)?.[1];
  if (firstText) console.log('             first text:', firstText);
  return !bad && tokens > 0;
}

const okVision = await chat('vision', 'What is this screenshot of? Answer in one sentence.', [png]);
const okResume = await chat('resume', 'Based on the attached resume, suggest a one-line improvement to the summary.', [pdf]);
const okDocx = await chat('docx', 'Rewrite the attached summary in one punchy sentence.', [docx]);
const okCsv = await chat('csv', 'What was revenue in Feb according to the attached CSV? Answer with just the number.', [csv]);

console.log(okVision && okResume && okDocx && okCsv ? 'ALL FILE PROBES PASSED' : 'FILE PROBE FAILURES PRESENT');
if (!(okVision && okResume && okDocx && okCsv)) process.exit(1);