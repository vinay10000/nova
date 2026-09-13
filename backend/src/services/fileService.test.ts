// Self-check for the §10 pipeline's risky logic: magic-byte sniff + DOCX zip extraction
// (ponytail: one runnable check, no framework). Run: npx tsx src/services/fileService.test.ts
import assert from 'node:assert/strict';
import { deflateRawSync } from 'node:zlib';
import { detectMime, extractText, zipEntry } from './fileService.js';

const crc32 = (b: Buffer) => {
  let c = ~0;
  for (const x of b) {
    c ^= x;
    for (let k = 0; k < 8; k++) c = (c >>> 1) ^ (0xedb88320 & -(c & 1));
  }
  return ~c >>> 0;
};

/** Build a real (deflate-compressed) zip archive containing one entry. */
function zipOf(name: string, content: string): Buffer {
  const raw = Buffer.from(content);
  const comp = deflateRawSync(raw);
  const nbuf = Buffer.from(name, 'utf8');
  const local = Buffer.alloc(30 + nbuf.length);
  local.writeUInt32LE(0x04034b50, 0);
  local.writeUInt16LE(20, 4);
  local.writeUInt16LE(8, 8); // deflate
  local.writeUInt32LE(crc32(raw), 14);
  local.writeUInt32LE(comp.length, 18);
  local.writeUInt32LE(raw.length, 22);
  local.writeUInt16LE(nbuf.length, 26);
  nbuf.copy(local, 30);
  const central = Buffer.alloc(46 + nbuf.length);
  central.writeUInt32LE(0x02014b50, 0);
  central.writeUInt16LE(20, 4);
  central.writeUInt16LE(20, 6);
  central.writeUInt16LE(8, 10);
  central.writeUInt32LE(crc32(raw), 16);
  central.writeUInt32LE(comp.length, 20);
  central.writeUInt32LE(raw.length, 24);
  central.writeUInt16LE(nbuf.length, 28);
  central.writeUInt32LE(0, 42); // local header offset
  nbuf.copy(central, 46);
  const eocd = Buffer.alloc(22);
  eocd.writeUInt32LE(0x06054b50, 0);
  eocd.writeUInt16LE(1, 8); // entry count
  eocd.writeUInt16LE(1, 10);
  eocd.writeUInt32LE(central.length, 12);
  eocd.writeUInt32LE(local.length + comp.length, 16); // central dir offset = after local part + data
  return Buffer.concat([local, comp, central, eocd]);
}

// PNG magic — even with a lying declared mime.
assert.equal(detectMime(Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), 'application/octet-stream'), 'image/png');
assert.equal(detectMime(Buffer.from('%PDF-1.4 junk'), 'text/plain'), 'application/pdf');

// Declared text/plain that is actually binary must be rejected (extension trust is not sniffing).
assert.equal(detectMime(Buffer.from([0x00, 0x01, 0x02, 0xff]), 'text/plain'), null);

// Plain text accepted.
assert.equal(detectMime(Buffer.from('hello world', 'utf8'), 'text/plain'), 'text/plain');

// DOCX: a zip with word/document.xml is DOCX regardless of declared mime.
const docx = zipOf('word/document.xml', '<w:document><w:p><w:t>Hello</w:t></w:p><w:p><w:t>Nova resume</w:t></w:p></w:document>');
assert.equal(detectMime(docx, 'application/octet-stream'), 'application/vnd.openxmlformats-officedocument.wordprocessingml.document');

// A zip WITHOUT word/document.xml (e.g. XLSX/jar) is not on the allowlist.
assert.equal(detectMime(zipOf('xl/workbook.xml', '<x/>'), 'application/octet-stream'), null);

// Round-trip: the zip reader returns the exact compressed payload.
assert.equal(zipEntry(docx, 'word/document.xml')?.toString('utf8').includes('Nova resume'), true);

// DOCX -> plain text, paragraphs preserved.
const text = extractText(docx, detectMime(docx, '')!);
assert.equal(text, 'Hello\nNova resume');

console.log('file checks passed');