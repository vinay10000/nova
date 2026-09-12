import type { AIProvider, StreamChunk } from '../ai/AIProvider.js';

export interface ChatStore {
  loadMessages(conversationId: string, userId: string): Promise<{ role: string; content: string }[]>;
  appendMessage(conversationId: string, userId: string, role: string, content: string, model?: string): Promise<string>;
  titleConversation(conversationId: string, title: string): Promise<void>;
}

// §48 Chat Service -> Gemini Gateway. Persists Conversation/Message (§8), streams via SSE.
export function createChatService(ai: AIProvider, store: ChatStore) {
  return {
    async *stream(
      conversationId: string,
      userId: string,
      message: string,
      model?: string,
    ): AsyncGenerator<StreamChunk> {
      // history first, so the model sees the full conversation (backend is the source of truth).
      const history = await store.loadMessages(conversationId, userId);
      await store.appendMessage(conversationId, userId, 'user', message);

      if (history.length === 0) {
        // §8 auto-title after the first exchange; failure must not break the chat.
        ai.titleFor(message)
          .then((t) => store.titleConversation(conversationId, t))
          .catch(() => {});
      }

      let full = '';
      for await (const chunk of ai.streamChat(
        [...history.map((m) => ({ role: m.role as 'user' | 'model', content: m.content })), { role: 'user' as const, content: message }],
        { model },
      )) {
        if (chunk.type === 'token') full += chunk.text;
        yield chunk;
      }

      // Persist only real content — never an empty assistant row on a failed stream.
      if (full.trim()) await store.appendMessage(conversationId, userId, 'model', full, model);
    },
  };
}
