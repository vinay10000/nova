import type { AIProvider, StreamChunk } from '../ai/AIProvider.js';

export interface ChatStore {
  loadMessages(conversationId: string, userId: string): Promise<{ role: string; content: string }[]>;
  appendMessage(conversationId: string, userId: string, role: string, content: string, model?: string): Promise<string>;
  titleConversation(conversationId: string, userId: string, title: string): Promise<void>;
}

export class ConversationNotFoundError extends Error {
  constructor() {
    super('conversation_not_found');
    this.name = 'ConversationNotFoundError';
  }
}

export interface StreamOptions {
  conversationId: string;
  userId: string;
  message: string;
  model?: string;
  signal?: AbortSignal;
}

// §48 Chat Service -> Gemini Gateway. Persists Conversation/Message (§8), streams via SSE.
export function createChatService(ai: AIProvider, store: ChatStore) {
  return {
    async *stream(opts: StreamOptions): AsyncGenerator<StreamChunk> {
      const { conversationId, userId, message, model, signal } = opts;
      signal?.throwIfAborted?.();
      // history first, so the model sees the full conversation (backend is the source of truth).
      const history = await store.loadMessages(conversationId, userId);
      signal?.throwIfAborted?.();
      await store.appendMessage(conversationId, userId, 'user', message);

      if (history.length === 0) {
        // §8 auto-title after the first exchange; failure must not break the chat.
        ai.titleFor(message)
          .then((t) => store.titleConversation(conversationId, userId, t))
          .catch(() => {});
      }

      let full = '';
      for await (const chunk of ai.streamChat(
        [...history.map((m) => ({ role: m.role as 'user' | 'model', content: m.content })), { role: 'user' as const, content: message }],
        { model, signal },
      )) {
        if (signal?.aborted) break;
        if (chunk.type === 'token') full += chunk.text;
        yield chunk;
      }

      // Persist only real content — never an empty assistant row on a failed stream.
      if (full.trim()) await store.appendMessage(conversationId, userId, 'model', full, model);
    },
  };
}
