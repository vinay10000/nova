import type { PrismaClient } from '@prisma/client';
import type { AttachmentSource } from './chatService.js';

// §10: attachments resolve from the DB, always ownership-scoped (§29). Bytes live in
// Neon bytea (see fileService.ts) so the Vercel lambda needs no disk.
export function prismaAttachmentSource(db: PrismaClient): AttachmentSource {
  return {
    async loadAttachments(userId, ids) {
      if (!ids.length) return [];
      const rows = await db.attachment.findMany({
        where: { id: { in: ids }, userId }, // isolation in the filter
        select: { id: true, mime: true, status: true, extractedText: true, data: true },
      });
      return rows.map((r) => ({
        id: r.id,
        mime: r.mime,
        status: r.status,
        inlineData: r.status === 'ready' && r.data ? { data: Buffer.from(r.data).toString('base64'), mime: r.mime } : undefined,
        extractedText: r.extractedText ?? undefined,
      }));
    },

    async linkAttachments(messageId, ids) {
      if (ids.length) await db.attachment.updateMany({ where: { id: { in: ids } }, data: { messageId } });
    },
  };
}
