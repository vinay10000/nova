import type { AIProvider, ChatMessage, InlinePart, StreamChunk } from '../ai/AIProvider.js';
import type { PrismaClient } from '@prisma/client';
import { detectPlugin, pluginToolDefs, executePluginTool, MAX_PLUGIN_STEPS, connectedProviders, isPluginUsable, type ChatPlugin } from './chatPlugins.js';
import { MODELS, type ModelEffort } from '../ai/models.js';
import { uiBlocksFromToolResult, blocksFromPresentUiInput, presentUiParamsSchema, type UiBlock } from '../ui/UiBlocks.js';
import { envelopesFromSurfaceInput } from '../ui/A2ui.js';

/** §45 generative UI: the model calls this instead of printing UI JSON in prose. */
const PRESENT_UI_DEF = {
  name: 'present_ui',
  description:
    'Render rich UI cards (summary, metrics, list, table, progress, timeline, comparison, code, chart, links) under your reply. ' +
    'Use it ONLY for structured content: lists, comparisons, tabular data, numeric metrics, status steps, percent goals, ' +
    'source listings, code, bar-chart series, openable links, or a short factual summary with key/value facts. ' +
    'Pick the type that matches the data — never force a table into a list. ' +
    'Do NOT put refusals, apologies, limitations or conversational filler in a card — those stay in plain prose. ' +
    'Titles are short noun phrases (about 6 words, sentence case). ' +
    'NEVER print UI JSON in your text answer — call this tool instead.',
  parameters: presentUiParamsSchema,
};

/**
 * §45b generative UI: a live surface, not a set of cards.
 *
 * present_ui is for data the user asked to see laid out. present_surface is for
 * a moment the app itself has to own — a trip being assembled, a day filling up,
 * a checklist the user can tick, a purchase waiting on a yes. Its cards are
 * stateful: they can stream, they can be edited, and their buttons report back.
 *
 * Prefer present_surface when the answer has a shape (a trip, an agenda, a
 * receipt). Prefer present_ui when the answer is a table of numbers. Never call
 * both for the same content.
 */
const PRESENT_SURFACE_DEF = {
  name: 'present_surface',
  description:
    'Render a live, interactive surface under your reply. Use it when the answer has a SHAPE — a trip, a place to stay, ' +
    'a day of plans, a tickable checklist, weather, or a purchase awaiting confirmation — rather than a table of numbers. ' +
    'Each card is a native component: it can fill in progressively, the user can tick a checklist, and its buttons ' +
    'send an action back to you. ' +
    'Kinds: trip (destination + dates + flight) · stay (name, address, check-in/out, photo) · weather (now, high/low, hours) · ' +
    'agenda (a day of timed entries) · checklist (tickable items, set done:true/false for what is already handled) · ' +
    'approval (a purchase waiting on the user) · actions (suggested next steps as buttons). ' +
    'Put prose, caveats and refusals in your text answer, never in a card. ' +
    'For actions, each prompt needs a short label and an action name you will listen for. ' +
    'For approval, set confirmName/declineName to the action names you will handle. ' +
    'NEVER print surface JSON in your text answer — call this tool instead.',
  parameters: {
    type: 'object',
    properties: {
      cards: {
        type: 'array',
        description: '1-8 cards, in the order they should read top to bottom',
        items: {
          type: 'object',
          properties: {
            kind: {
              type: 'string',
              enum: ['trip', 'stay', 'weather', 'agenda', 'checklist', 'approval', 'actions'],
            },
            eyebrow: { type: 'string', description: 'Small label above the card, e.g. "Your trip"' },
            destination: { type: 'string', description: 'trip: where to' },
            dates: { type: 'string', description: 'trip: date range, e.g. "Jun 3 – Jun 9"' },
            leg: { type: 'string', description: 'trip: the flight or leg, e.g. "Flight MU7 · departs 11:20"' },
            legStatus: { type: 'string', description: 'trip: short status such as "On time"' },
            note: { type: 'string', description: 'trip or checklist: one quiet line of context' },
            name: { type: 'string', description: 'stay: the property or hotel name' },
            address: { type: 'string', description: 'stay: neighbourhood and floor' },
            checkIn: { type: 'string', description: 'stay: check-in date and time' },
            checkOut: { type: 'string', description: 'stay: check-out date and time' },
            imageUrl: { type: 'string', description: 'stay: https photo of the place' },
            place: { type: 'string', description: 'weather: the city' },
            now: { type: 'string', description: 'weather: current temperature with degree, e.g. "22°"' },
            condition: { type: 'string', description: 'weather: e.g. "Partly cloudy"' },
            high: { type: 'string', description: 'weather: high, e.g. "24°"' },
            low: { type: 'string', description: 'weather: low, e.g. "17°"' },
            precip: { type: 'string', description: 'weather: chance of rain, e.g. "30%"' },
            wind: { type: 'string', description: 'weather: e.g. "Light breeze"' },
            hours: {
              type: 'array',
              description: 'weather: the next few hours (max 8)',
              items: { type: 'object', properties: { label: { type: 'string' }, temp: { type: 'string' } }, required: ['label', 'temp'] },
            },
            heading: { type: 'string', description: 'agenda: the day, e.g. "Thursday, Jun 4"' },
            events: {
              type: 'array',
              description: 'agenda: timed entries (max 12)',
              items: { type: 'object', properties: { time: { type: 'string' }, title: { type: 'string' }, place: { type: 'string' } }, required: ['time', 'title'] },
            },
            items: { type: 'array', items: { type: 'string' }, description: 'checklist: the rows to tick (max 20)' },
            done: {
              type: 'array',
              items: { type: 'boolean' },
              description:
                'checklist: same length as items. Supply this ONLY when the user has already handled some rows — it makes the ' +
                'list two-way, so their ticks come back to you. Omit it and the list is read-only.',
            },
            provider: { type: 'string', description: 'approval: who is charging' },
            summary: { type: 'string', description: 'approval: what is being bought, e.g. "Airport pickup · Haneda → Kanda House"' },
            amount: { type: 'string', description: 'approval: the amount, e.g. "¥6,800"' },
            instrument: { type: 'string', description: 'approval: the card, e.g. "Visa ···· 4342"' },
            state: {
              type: 'string',
              enum: ['pending', 'verifying', 'approved', 'declined'],
              description: 'approval: the flow state. Start at pending.',
            },
            confirmName: { type: 'string', description: 'approval: action name you handle when the user confirms' },
            declineName: { type: 'string', description: 'approval: action name you handle when the user declines' },
            prompts: {
              type: 'array',
              description: 'actions: suggested next steps (max 4)',
              items: {
                type: 'object',
                properties: {
                  label: { type: 'string', description: 'What the button says' },
                  name: { type: 'string', description: 'Action name you will handle when tapped' },
                  context: { type: 'object', additionalProperties: true, description: 'Extra values to send with the action' },
                },
                required: ['label', 'name'],
              },
            },
          },
          required: ['kind'],
        },
      },
    },
    required: ['cards'],
  } as const,
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
  effort?: ModelEffort;
  signal?: AbortSignal;
  /** §10 attachment ids uploaded via /v1/files, attached to this user message. */
  attachmentIds?: string[];
}

// §48 Chat Service -> Gemini Gateway. Persists Conversation/Message (§8), streams via SSE.
// §42: supports @plugin mentions that activate tool-backed conversations.
export function createChatService(ai: AIProvider, store: ChatStore, attachments?: AttachmentSource, db?: PrismaClient) {
  return {
    async *stream(opts: StreamOptions): AsyncGenerator<StreamChunk> {
      const { conversationId, userId, message, model, effort, signal } = opts;
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

      // If a plugin is active, add system instruction and tools. The two UI tools ride on
      // every chat (plugin or not) so "show me a dashboard" never degrades to prose JSON.
      const pluginTools = activePlugin ? pluginToolDefs(activePlugin) : undefined;
      const chatToolDefs = [...(pluginTools ?? []), PRESENT_UI_DEF, PRESENT_SURFACE_DEF];
      const UI_HINT =
        'The app renders tool results, present_ui cards and present_surface components as real UI automatically. ' +
        'Answer in plain prose. NEVER print raw JSON or a code block describing a UI — call present_ui or present_surface instead. ' +
        'present_ui is for data: metrics for numbers, list for items, table for rows, progress for percent goals ' +
        '(0-100), timeline for status steps, comparison for two-sided choices, code for source, chart for series, ' +
        'links for openable URLs, summary for a short factual overview with key/value metadata (under 600 characters). ' +
        'present_surface is for an answer with a SHAPE: a trip, a place to stay, a day of plans, a tickable checklist, ' +
        'weather, a purchase awaiting confirmation, or suggested next steps. Its buttons come back to you as actions, ' +
        'so name them ("add_packing_list", "confirm_ride") and react to them. ' +
        'Refusals, apologies and limitations stay in prose.';
      const systemMessage = activePlugin ? `${activePlugin.systemInstruction} ${UI_HINT}` : UI_HINT;

      let full = '';
      let uiBlocks: UiBlock[] = [];
      // §45b: one server-owned id per turn. The model never sees or sets it.
      const surfaceId = `s_${Date.now().toString(36)}_${Math.floor(Math.random() * 1e6).toString(36)}`;
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
          ...(effort ? { effort } : {}),
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

          if (chunk.type === 'reasoning') {
            yield chunk;
          } else if (chunk.type === 'token') {
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
        // present_ui and present_surface are local (no DB needed) — validate + emit immediately.
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
            if (tc.toolId === 'present_surface') {
              // One surface per turn. The id is server-owned, so two surfaces in
              // one turn could not be told apart by the client.
              const frames = envelopesFromSurfaceInput(tc.args, surfaceId);
              if (frames.length) {
                for (const frame of frames) yield { type: 'a2ui', frame };
                const componentCount = ((frames[1]?.updateComponents as { components?: unknown[] } | undefined)?.components?.length) ?? 0;
                results.push({
                  type: 'function_result',
                  name: tc.toolId,
                  call_id: tc.callId,
                  result: JSON.stringify({ rendered: componentCount, surfaceId }),
                });
              } else {
                results.push({ type: 'function_result', name: tc.toolId, call_id: tc.callId, result: 'invalid cards — check the schema and retry with valid kinds', is_error: true });
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
