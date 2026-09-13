// §10 upload pipeline: validate (magic bytes, not extension trust) -> store -> extract -> status.
// Bytes live in Neon bytea — the deployed backend is a Vercel lambda with a read-only FS,
// so disk storage was not survivable (upload in one invocation, read back in another).
import { createHash, randomUUID } from 'node:crypto';
import { inflateRawSync } from 'node:zlib';
import type { PrismaClient } from '@prisma/client';

export const MAX_FILE_BYTES = 20 * 1024 * 1024; // 20 MB cap

// §46/§10 allowlist, matched by magic-byte signature.
const MAGIC: [number[], string][] = [
  [[0x89, 0x50, 0x4e, 0x47], 'image/png'],
  [[0xff, 0xd8, 0xff], 'image/jpeg'],
  [[0x47, 0x49, 0x46, 0x38], 'image/gif'],
  [[0x42, 0x4d], 'image/bmp'],
  [[0x25, 0x50, 0x44, 0x46], 'application/pdf'], // %PDF
  [[0x49, 0x49, 0x2a, 0x00], 'image/tiff'],
  [[0x4d, 0x4d, 0x00, 0x2a], 'image/tiff'],
];

/** Sniff by magic bytes first; text types accepted by content check, never by declared mime alone. */
export function detectMime(buf: Buffer, declared: string): string | null {
  for (const [sig, mime] of MAGIC) {
    if (sig.every((b, i) => buf[i] === b)) return mime;
  }
  // WebP: RIFF....WEBP
  if (buf.length > 12 && buf.toString('ascii', 0, 4) === 'RIFF' && buf.toString('ascii', 8, 12) === 'WEBP') return 'image/webp';
  if (buf.length > 4 && buf.toString('ascii', 0, 2) === 'PK') {
    // OOXML = zip; only the DOCX flavour is on the §10 allowlist. Identify by the
    // entry name (word/document.xml), never by the declared client mime.
    return zipEntry(buf, 'word/document.xml')
      ? 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'
      : null;
  }
  if (declared === 'text/plain' || declared === 'text/csv') {
    // Text: valid UTF-8 with no replacement runs. CSV allowed as Gemini document type too.
    return buf.toString('utf8').includes('\ufffd') ? null : declared;
  }
  return null;
}

/**
 * Read one entry from a zip by parsing the central directory. Stdlib has no zip reader and
 * §10 requires DOCX, which is a zip. ~30 lines beats a new dependency here.
 * ponytail: ceiling — no zip64, no encrypted/ZIP-split archives; irrelevant below the 20 MB cap.
 */
export function zipEntry(buf: Buffer, name: string): Buffer | null {
  for (let i = buf.length - 22; i >= 0; i--) {
    if (buf.readUInt32LE(i) !== 0x06054b50) continue; // end-of-central-directory
    const count = buf.readUInt16LE(i + 10);
    let p = buf.readUInt32LE(i + 16); // central directory offset
    for (let n = 0; n < count; n++) {
      if (buf.readUInt32LE(p) !== 0x02014b50 || p + 46 > buf.length) return null;
      const method = buf.readUInt16LE(p + 10);
      const csize = buf.readUInt32LE(p + 20);
      const nlen = buf.readUInt16LE(p + 28);
      const elen = buf.readUInt16LE(p + 30);
      const clen = buf.readUInt16LE(p + 32);
      const lho = buf.readUInt32LE(p + 42);
      if (buf.toString('utf8', p + 46, p + 46 + nlen) === name) {
        const lnlen = buf.readUInt16LE(lho + 26);
        const lelen = buf.readUInt16LE(lho + 28);
        const start = lho + 30 + lnlen + lelen;
        const comp = buf.subarray(start, start + csize);
        return method === 0 ? Buffer.from(comp) : inflateRawSync(comp);
      }
      p += 46 + nlen + elen + clen;
    }
    return null;
  }
  return null;
}

/** Word paragraphs -> newlines, then strip the remaining markup. */
function docxText(xml: Buffer): string {
  return xml
    .toString('utf8')
    .replace(/<\/w:p>|<w:br[^>]*\/>/g, '\n')
    .replace(/<[^>]+>/g, '')
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/&quot;/g, '"').replace(/&apos;/g, "'")
    .replace(/\n{3,}/g, '\n\n')
    .slice(0, 200_000)
    .trim();
}

/** Convert a document to plain text so any model can read it (§9 backend preprocessing). */
export function extractText(buf: Buffer, mime: string): string | null {
  if (mime === 'text/plain' || mime === 'text/csv') return buf.toString('utf8').slice(0, 200_000);
  if (mime === 'application/vnd.openxmlformats-officedocument.wordprocessingml.document') {
    const xml = zipEntry(buf, 'word/document.xml');
    return xml ? docxText(xml) : null;
  }
  return null; // images/PDF go inline to Gemini directly, no text extraction needed
}

export interface StoredFile {
  id: string;
  filename: string;
  mime: string;
  size: number;
  status: string;
  url: string;
}

export function createFileService(db: PrismaClient) {
  return {
    async store(userId: string, filename: string, declaredMime: string, buf: Buffer): Promise<StoredFile> {
      if (buf.length === 0) throw new Error('empty_file');
      if (buf.length > MAX_FILE_BYTES) throw new Error('file_too_large');
      const mime = detectMime(buf, declaredMime);
      if (!mime) throw new Error('unsupported_type');

      const id = randomUUID();
      const text = extractText(buf, mime);
      const status = text ? 'extracted' : 'ready';

      await db.attachment.create({
        data: {
          id,
          userId,
          filename,
          mime,
          size: buf.length,
          url: `/v1/files/${id}`,
          data: new Uint8Array(buf),
          sha256: createHash('sha256').update(buf).digest('hex'),
          ...(text ? { extractedText: text } : {}),
          status,
        },
      });

      return { id, filename, mime, size: buf.length, status, url: `/v1/files/${id}` };
    },

    /** Load a stored attachment, ownership-checked. */
    async get(userId: string, id: string) {
      return db.attachment.findFirst({ where: { id, userId } });
    },
  };
}

export type FileService = ReturnType<typeof createFileService>;
