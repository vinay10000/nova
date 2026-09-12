import type { PrismaClient } from '@prisma/client';
import type { ChatStore } from './chatService.js';

// §29/§46 per-user isolation: EVERY query is scoped by userId. Never fetch by id alone.
export function prismaChatStore(db: PrismaClient): ChatStore {
  return {
    async loadMessages(conversationId, userId) {
      const conv = await db.conversation.findFirst({ where: { id: conversationId, userId } });
      if (!conv) throw new Error('conversation_not_found');
      const rows = await db.message.findMany({
        where: { conversationId },
        orderBy: { createdAt: 'asc' },
        select: { role: true, content: true },
      });
      return rows;
    },

    async appendMessage(conversationId, userId, role, content, model) {
      const conv = await db.conversation.findFirst({ where: { id: conversationId, userId } });
      if (!conv) throw new Error('conversation_not_found');
      const msg = await db.message.create({ data: { conversationId, role, content, model } });
      await db.conversation.update({ where: { id: conversationId }, data: { updatedAt: new Date() } });
      return msg.id;
    },

    async titleConversation(conversationId, title) {
      await db.conversation.update({ where: { id: conversationId }, data: { title } });
    },
  };
}
