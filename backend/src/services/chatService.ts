import type { AIProvider, ChatMessage, InlinePart, StreamChunk } from '../ai/AIProvider.js';
import type { PrismaClient } from '@prisma/client';
import { detectPlugin, pluginToolDefs, executePluginTool, MAX_PLUGIN_STEPS, connectedProviders, isPluginUsable, type ChatPlugin } from './chatPlugins.js';
import { MODELS } from '../ai/models.js';
import { uiBlocksFromToolResult, blocksFromPresentUiInput, presentUiParamsSchema, type UiBlock } from '../ui/UiBlocks.js';

/** §45 generative UI: the model calls this instead of printing UI JSON in prose. */
const PRESENT_UI_DEF = {
  name: 'present_ui',
  description:
    'Render rich UI cards (summary, metrics, list, table) under your reply. ' +
    'Use it ONLY for structured content: lists, comparisons, tabular data, numeric metrics, or a short factual summary with key/value facts. ' +
    'Do NOT put refusals, apologies, limitations or conversational filler in a card — those stay in plain prose. ' +
    'Titles are short noun phrases (about 6 words, sentence case). ' +
    'NEVER print UI JSON in your text answer — call this tool instead.',
  parameters: presentUiParamsSchema,
};

export interface ChatStore {
  loadMessages(conversationId: string, userId: string): Promise<{ role: string; content: string }[]>;
  appendMessage(conversationId: string, userId: string, role: string, content: string, model?: string, metadata?: unknown): Promise<string>;
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

      // Auto-route image messages to the vision model when no explicit model is chosen.
      // Tool calls are re-routed to the tools model inside the provider; images
      // need the vision model here.
      const hasImage = inlineParts.some((p) => p.mime.startsWith('image/'));
      const resolvedModel = hasImage && !model ? MODELS.vision : model;

      // §42: detect @plugin mentions (e.g. @github, @gmail)
      // The connected set decides whether a *keyword* match may route to a
      // plugin; an explicit @mention always routes, so the user gets an honest
      // "connect this first" answer instead of silence.
      const connected = db ? await connectedProviders(db, userId) : undefined;
      const pluginMatch = detectPlugin(message, connected);
      const activePlugin: ChatPlugin | null = pluginMatch?.plugin ?? null;
      const userQuery = pluginMatch?.cleanedMessage ?? message;
      if (activePlugin) {
        const ready = !connected || isPluginUsable(activePlugin, connected);
        yield {
          type: 'notice',
          code: ready ? 'plugin' : 'reconnect',
          provider: activePlugin.requires ?? activePlugin.id,
          message: ready
            ? activePlugin.name
            : `${activePlugin.name} is not connected yet — connect it to get real data.`,
        };
      }

      // Build the message history for Gemini
      const chatHistory: ChatMessage[] = [
        ...history.map((m) => ({ role: m.role as 'user' | 'model', content: m.content })),
        { role: 'user' as const, content: userQuery },
      ];

      // If a plugin is active, add system instruction and tools. present_ui rides on
      // every chat (plugin or not) so "show me a dashboard" never degrades to prose JSON.
      const pluginTools = activePlugin ? pluginToolDefs(activePlugin) : undefined;
      const chatToolDefs = [...(pluginTools ?? []), PRESENT_UI_DEF];
      const UI_HINT =
        'The app renders tool results and present_ui calls as rich UI cards automatically. ' +
        'Answer in plain prose. NEVER print raw JSON or a code block describing a UI — call present_ui instead. ' +
        'Cards are for structured facts: metrics for numbers, list for items, table for rows, summary for a short ' +
        'factual overview with key/value metadata (under 600 characters). Refusals, apologies and limitations stay in prose.';
      const systemMessage = activePlugin ? `${activePlugin.systemInstruction} ${UI_HINT}` : UI_HINT;

      let full = '';
      let uiBlocks: UiBlock[] = [];
      let toolSteps = 0;
      // Gemini's Interactions API requires the interaction that produced a
      // function call on the next function_result request. Keep it across the
      // bounded plugin loop instead of starting a disconnected upstream turn.
      let previousInteractionId: string | undefined;

      // §42: tool loop for plugins — stream, execute tools, feed back, repeat.
      // Bounded by MAX_PLUGIN_STEPS to keep chat responsive (§46).
      const messages: ChatMessage[] = systemMessage
        ? [{ role: 'system' as const, content: systemMessage }, ...chatHistory]
        : chatHistory;

      let currentMessages = [...messages];
      let pendingResults: { type: 'function_result'; name: string; call_id: string; result: string; is_error?: boolean }[] | undefined;

      while (toolSteps <= MAX_PLUGIN_STEPS) {
        if (signal?.aborted) break;

        // The function_result path chains on previous_interaction_id; an extra
        // OpenAI-style tool transcript in `input` would double-feed the model.
        // Follow-up guidance lives in the system instruction instead.
        const loopMessages = pendingResults?.length ? messages : currentMessages;

        const streamOpts: Record<string, unknown> = {
          model: resolvedModel,
          signal,
          attachments: inlineParts,
          extractedText,
        };
        if (previousInteractionId) streamOpts.previousInteractionId = previousInteractionId;
        streamOpts.tools = chatToolDefs;
        if (pendingResults?.length) streamOpts.functionResults = pendingResults;

        pendingResults = undefined;
        let sawToolCall = false;
        const toolCalls: { toolId: string; callId: string; args: unknown }[] = [];
        let completionChunk: StreamChunk | undefined;

        for await (const chunk of ai.streamChat(loopMessages, streamOpts as Parameters<AIProvider['streamChat']>[1])) {
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
            // A provider turn can finish because it emitted a tool call. Do
            // not close the client stream until the tool result has been fed
            // back and the final natural-language response is complete.
            previousInteractionId = chunk.interactionId ?? previousInteractionId;
            completionChunk = chunk;
          } else if (chunk.type === 'error') {
            yield chunk;
            return;
          }
        }

        // If no tool calls, we're done — the model produced a text response
        if (!sawToolCall || !toolCalls.length) {
          if (completionChunk) yield completionChunk;
          break;
        }
        if (toolSteps >= MAX_PLUGIN_STEPS) {
          yield { type: 'step', label: 'Plugin step limit reached' };
          break;
        }

        // Execute tool calls and prepare results for the next turn.
        // present_ui is local (no DB needed) — validate + emit blocks immediately.
        {
          const results: { type: 'function_result'; name: string; call_id: string; result: string; is_error?: boolean }[] = [];
          for (const tc of toolCalls) {
            if (tc.toolId === 'present_ui') {
              const blocks = blocksFromPresentUiInput(tc.args);
              if (blocks?.length) {
                uiBlocks = [...uiBlocks, ...blocks].slice(0, 8);
                yield { type: 'ui', blocks };
                results.push({ type: 'function_result', name: tc.toolId, call_id: tc.callId, result: JSON.stringify({ rendered: blocks.length }) });
              } else {
                results.push({ type: 'function_result', name: tc.toolId, call_id: tc.callId, result: 'invalid blocks — check the schema and retry with valid types', is_error: true });
              }
              continue;
            }
            if (!db) {
              results.push({ type: 'function_result', name: tc.toolId, call_id: tc.callId, result: 'tool unavailable', is_error: true });
              continue;
            }
            const { result, isError, reconnect, retryable } = await executePluginTool(db, userId, tc.toolId, tc.args);
            if (!isError) {
              const blocks = uiBlocksFromToolResult(tc.toolId, result);
              if (blocks.length) {
                uiBlocks = [...uiBlocks, ...blocks].slice(0, 8);
                yield { type: 'ui', blocks };
              }
            }
            results.push({
              type: 'function_result',
              name: tc.toolId,
              call_id: tc.callId,
              result,
              ...(isError ? { is_error: true } : {}),
            });
            // §38/§47: tell the app what to do about it, not just the model.
            if (reconnect) {
              yield { type: 'notice', code: 'reconnect', provider: reconnect, message: `${reconnect} needs to be reconnected` };
            } else if (retryable) {
              yield { type: 'notice', code: 'retry', provider: tc.toolId.split('_')[0], message: 'upstream busy — retry shortly' };
            }
          }
          pendingResults = results;
        }

        toolSteps++;
        // Build proper OpenAI-format messages: assistant with tool_calls, then tool results
        const assistantMsg: ChatMessage = {
          role: 'model',
          content: '',
          tool_calls: toolCalls.map((tc) => ({
            id: tc.callId,
            type: 'function' as const,
            function: { name: tc.toolId, arguments: JSON.stringify(tc.args) },
          })),
        };

        const toolResultMsgs: ChatMessage[] = toolCalls.map((tc) => {
          const res = pendingResults?.find((r) => r.call_id === tc.callId);
          return {
            role: 'tool' as const,
            content: res?.is_error ? `Error: ${res.result}` : (res?.result ?? 'no result'),
            tool_call_id: tc.callId,
          };
        });

        currentMessages = [
          ...currentMessages,
          assistantMsg,
          ...toolResultMsgs,
          { role: 'user' as const, content: 'Tool results are above. Summarize them for the user concisely.' },
        ];
      }

      // Persist only real content — never an empty assistant row on a failed stream.
      if (full.trim()) await store.appendMessage(conversationId, userId, 'model', full, model, uiBlocks.length ? { ui: uiBlocks } : undefined);
    },
  };
}
