import type { AIProvider, ChatMessage, InlinePart, StreamChunk } from '../ai/AIProvider.js';
import type { PrismaClient } from '@prisma/client';
import { detectPlugin, pluginToolDefs, executePluginTool, MAX_PLUGIN_STEPS, type ChatPlugin } from './chatPlugins.js';

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
// §42: supports @plugin mentions that activate tool-backed conversations.
export function createChatService(ai: AIProvider, store: ChatStore, attachments?: AttachmentSource, db?: PrismaClient) {
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

      // §42: detect @plugin mentions (e.g. @github, @gmail)
      const pluginMatch = detectPlugin(message);
      const activePlugin: ChatPlugin | null = pluginMatch?.plugin ?? null;
      const userQuery = pluginMatch?.cleanedMessage ?? message;

      // Build the message history for Gemini
      const chatHistory: ChatMessage[] = [
        ...history.map((m) => ({ role: m.role as 'user' | 'model', content: m.content })),
        { role: 'user' as const, content: userQuery },
      ];

      // If a plugin is active, add system instruction and tools
      const pluginTools = activePlugin ? pluginToolDefs(activePlugin) : undefined;
      const systemMessage = activePlugin ? activePlugin.systemInstruction : undefined;

      let full = '';
      let toolSteps = 0;

      // §42: tool loop for plugins — stream, execute tools, feed back, repeat.
      // Bounded by MAX_PLUGIN_STEPS to keep chat responsive (§46).
      const messages: ChatMessage[] = systemMessage
        ? [{ role: 'system' as const, content: systemMessage }, ...chatHistory]
        : chatHistory;

      let currentMessages = [...messages];
      let pendingResults: { type: 'function_result'; name: string; call_id: string; result: string; is_error?: boolean }[] | undefined;

      while (toolSteps <= MAX_PLUGIN_STEPS) {
        if (signal?.aborted) break;

        const streamOpts: Record<string, unknown> = {
          model: resolvedModel,
          signal,
          attachments: inlineParts,
          extractedText,
        };
        if (pluginTools?.length) streamOpts.tools = pluginTools;
        if (pendingResults?.length) streamOpts.functionResults = pendingResults;

        pendingResults = undefined;
        let sawToolCall = false;
        const toolCalls: { toolId: string; callId: string; args: unknown }[] = [];

        for await (const chunk of ai.streamChat(currentMessages, streamOpts as Parameters<AIProvider['streamChat']>[1])) {
          if (signal?.aborted) break;

          if (chunk.type === 'token') {
            full += chunk.text;
            yield chunk;
          } else if (chunk.type === 'tool_call' && chunk.toolId) {
            sawToolCall = true;
            toolCalls.push({ toolId: chunk.toolId, callId: chunk.callId ?? `plugin:${toolSteps}:${toolCalls.length}`, args: chunk.args ?? {} });
            // Yield a step event so the UI can show "Checking GitHub..."
            yield { type: 'step', label: chunk.toolId };
          } else if (chunk.type === 'done') {
            // Forward done to the client so the UI can finalize the streaming message.
            yield chunk;
          } else if (chunk.type === 'error') {
            yield chunk;
            return;
          }
        }

        // If no tool calls, we're done — the model produced a text response
        if (!sawToolCall || !toolCalls.length) break;
        if (toolSteps >= MAX_PLUGIN_STEPS) {
          yield { type: 'step', label: 'Plugin step limit reached' };
          break;
        }

        // Execute tool calls and prepare results for the next turn
        if (db) {
          const results: { type: 'function_result'; name: string; call_id: string; result: string; is_error?: boolean }[] = [];
          for (const tc of toolCalls) {
            const { result, isError } = await executePluginTool(db, userId, tc.toolId, tc.args);
            results.push({
              type: 'function_result',
              name: tc.toolId,
              call_id: tc.callId,
              result,
              ...(isError ? { is_error: true } : {}),
            });
          }
          pendingResults = results;
        }

        toolSteps++;
        // Add assistant message with tool calls to history, then user message with results
        // (OpenAI format requires this sequence for multi-turn tool use)
        currentMessages = [
          ...currentMessages,
          { role: 'model' as const, content: '' },
          { role: 'user' as const, content: 'Here are the tool results. Summarize them for the user.' },
        ];
      }

      // Persist only real content — never an empty assistant row on a failed stream.
      if (full.trim()) await store.appendMessage(conversationId, userId, 'model', full, model);
    },
  };
}
