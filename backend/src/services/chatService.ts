import type { AIProvider, InlinePart, StreamChunk } from '../ai/AIProvider.js';

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

/** §10 attachment lookup — resolved by the store, never trusted from the client. */
export interface AttachmentSource {
  /** Returns owned attachments; unowned ids are silently dropped (isolation §29). */
  loadAttachments(userId: string, ids: string[]): Promise<{ id: string; mime: string; status: string; inlineData?: { data: string; mime: string }; extractedText?: string }[]>;
  linkAttachments(messageId: string, ids: string[]): Promise<void>;
}

export interface StreamOptions {
  conversationId: string;
  userId: string;
  message: string;
  model?: string;
  signal?: AbortSignal;
  /** §10 attachment ids uploaded via /v1/files, attached to this user message. */
  attachmentIds?: string[];
}

// §48 Chat Service -> Gemini Gateway. Persists Conversation/Message (§8), streams via SSE.
export function createChatService(ai: AIProvider, store: ChatStore, attachments?: AttachmentSource) {
  return {
    async *stream(opts: StreamOptions): AsyncGenerator<StreamChunk> {
      const { conversationId, userId, message, model, signal } = opts;
      signal?.throwIfAborted?.();
      // history first, so the model sees the full conversation (backend is the source of truth).
      const history = await store.loadMessages(conversationId, userId);
      signal?.throwIfAborted?.();
      const userMessageId = await store.appendMessage(conversationId, userId, 'user', message);

      // §10: attach files to this message. Inline data only flows to Gemini, never to the client.
      const files = attachments && opts.attachmentIds?.length
        ? await attachments.loadAttachments(userId, opts.attachmentIds)
        : [];
      if (files.length) await attachments!.linkAttachments(userMessageId, files.map((f) => f.id));

      if (history.length === 0) {
        // §8 auto-title after the first exchange; failure must not break the chat.
        ai.titleFor(message)
          .then((t) => store.titleConversation(conversationId, userId, t))
          .catch(() => {});
      }

      const inlineParts: InlinePart[] = [];
      let extractedText: string | undefined;
      for (const f of files) {
        if (f.status === 'ready' && f.inlineData) inlineParts.push({ mime: f.inlineData.mime, data: f.inlineData.data });
        if (f.status === 'extracted' && f.extractedText) extractedText = (extractedText ? extractedText + '\n\n' : '') + f.extractedText;
      }

      // Auto-route image messages to stepfun-3.7-flash (vision-capable) when no explicit model is chosen.
      const hasImage = inlineParts.some((p) => p.mime.startsWith('image/'));
      const resolvedModel = hasImage && !model ? 'stepfun-3.7-flash' : model;

      let full = '';
      for await (const chunk of ai.streamChat(
        [...history.map((m) => ({ role: m.role as 'user' | 'model', content: m.content })), { role: 'user' as const, content: message }],
        { model: resolvedModel, signal, attachments: inlineParts, extractedText },
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
