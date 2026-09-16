import { GoogleGenAI } from '@google/genai';
import type { AIProvider, ChatMessage, InlinePart, StreamChunk, ToolDef } from './AIProvider.js';

import { MODELS } from './models.js';

export { MODELS };

// Model policy: ONLY two models. gemini-3.1-flash-lite default; gemini-3.5-flash-lite
// for tool calling and vision (see OpenAIProvider MODELS).


// §2-§3 Gemini via backend only. Function calling is the primary tool bridge (§15).
export class GeminiProvider implements AIProvider {
  private client: GoogleGenAI;

  constructor(apiKey = process.env.GEMINI_API_KEY ?? '') {
    if (!apiKey) console.warn('[gemini] GEMINI_API_KEY missing — chat fails until set server-side.');
    this.client = new GoogleGenAI({ apiKey });
  }

  /**
   * Verified Interactions API shape: `create()` takes `input` always; turn 1 passes the
   * text, follow-ups pass `function_result` steps plus `previous_interaction_id`.
   * Streaming emits `step.delta` text events, `step.start` function_call (arguments
   * EMPTY — real args stream later as `arguments_delta` chunks, flushed at `step.stop`),
   * and turns needing action close with NO `interaction.completed`, so the turn end
   * is detected by stream close with the id from `interaction.created`.
   */
  async *streamChat(
    messages: ChatMessage[],
    opts?: {
      model?: string;
      tools?: ToolDef[];
      previousInteractionId?: string;
      functionResults?: FunctionResultInput[];
      signal?: AbortSignal;
      /** §9 inline image/document parts on the last user turn. */
      attachments?: InlinePart[];
      /** §9 server-side extracted document text. */
      extractedText?: string;
    },
  ): AsyncGenerator<StreamChunk> {
    opts?.signal?.throwIfAborted?.();
    const system = messages.filter((m) => m.role === 'system').map((m) => m.content).join('\n');
    const turns = messages.filter((m) => m.role !== 'system');

    // Guard against stale model ids saved by older clients (e.g. router-era
    // 'qwen/...' ids) — they 404 the whole turn. Only known models pass.
    const allowed = new Set<string>(Object.values(MODELS));
    const model = opts?.model && allowed.has(opts.model) ? opts.model : MODELS.chat;
    const stream = (await this.client.interactions.create({
      model,
      input: opts?.functionResults?.length
        ? (opts.functionResults as unknown as Array<Record<string, unknown>>)
        : (buildInput(turns, opts?.attachments, opts?.extractedText) as string),
      ...(opts?.previousInteractionId ? { previous_interaction_id: opts.previousInteractionId } : {}),
      ...(system ? { system_instruction: system } : {}),
      ...(opts?.tools?.length ? { tools: opts.tools.map(toToolDef) } : {}),
      store: true, // required for previous_interaction_id chaining
      stream: true,
    } as Parameters<typeof this.client.interactions.create>[0])) as AsyncIterable<{
      event_type: string;
      index?: number;
      delta?: { type: string; text?: string; arguments?: string };
      step?: { type: string; name?: string; id?: string; arguments?: unknown };
      interaction?: { id?: string };
      interaction_id?: string;
    }>;

    // function_call args stream as arguments_delta chunks AFTER step.start carries
    // empty arguments — buffer per step index, emit the call at step.stop.
    let pending: { index?: number; id: string; name: string; argsText: string } | null = null;
    let interactionId: string | undefined;
    let sawDone = false;
    const flush = function* (): Generator<StreamChunk> {
      if (pending) {
        let args: unknown = {};
        try { args = pending.argsText ? JSON.parse(pending.argsText) : {}; } catch { args = {}; }
        const call = { type: 'tool_call' as const, toolId: pending.name, callId: pending.id, args };
        pending = null;
        yield call;
      }
    };

    for await (const event of stream) {
      if (opts?.signal?.aborted) break;
      // Stream-level failures (e.g. quota) arrive as EVENTS on stream:true, not
      // HTTP errors — surfacing them as throws is what lets callers fall back.
      // Seen in the wild: silent empty streams exactly when quota was exhausted.
      const evt = event as { event_type: string; error?: unknown; message?: unknown };
      if (evt.event_type === 'error' || evt.event_type === 'interaction.failed' || evt.error) {
        throw new Error(`gemini_stream_error: ${JSON.stringify(evt.error ?? evt.message ?? evt).slice(0, 300)}`);
      }
      interactionId = event.interaction?.id ?? event.interaction_id ?? interactionId;
      if (event.event_type === 'step.delta' && (event.delta?.type === 'text' || event.delta?.type === 'text_delta') && event.delta.text) {
        yield { type: 'token', text: event.delta.text };
      }
      // step.start carries a function_call step with EMPTY arguments; the real
      // args arrive as arguments_delta chunks on the same index.
      if (event.event_type === 'step.start' && event.step?.type === 'function_call' && event.step.name) {
        yield* flush();
        // Verified: FunctionCallStep exposes `id` (used as call_id on the matching function_result).
        pending = { index: event.index, id: event.step.id ?? `${Date.now()}`, name: event.step.name, argsText: '' };
      }
      if (event.event_type === 'step.delta' && event.delta?.type === 'arguments_delta' && event.delta.arguments) {
        if (pending && (event.index === undefined || event.index === pending.index)) pending.argsText += event.delta.arguments;
      }
      if (event.event_type === 'step.stop') {
        yield* flush();
      }
      if (event.event_type === 'interaction.completed') {
        sawDone = true;
        yield* flush();
        yield { type: 'done', interactionId: event.interaction?.id ?? interactionId };
      }
    }
    // Turns needing action (requires_action) close the stream with no completed
    // event — the turn is still done; the runtime continues via functionResults.
    yield* flush();
    if (!sawDone) {
      // A stream with zero usable content and zero interaction id is an anomaly
      // (e.g. an unrecognized error shape) — fail loudly, never silently empty.
      if (!interactionId) throw new Error('empty_stream_response');
      yield { type: 'done', interactionId };
    }
  }

  async generateAgentConfig(naturalLanguage: string, knownTools?: string[]): Promise<unknown> {
    // §12-§14: goal/tools/permissions/schedule, plus questions[] when input is incomplete.
    // One fallback to the cheap model on quota exhaustion (per-model free-tier limits).
    // The allowed tool ids are named so the model cannot invent tool names (§15).
    const prompt =
      'Convert the request into an agent configuration matching shared/agent-config.schema.json. ' +
      `The tools array may ONLY contain these ids: ${JSON.stringify(knownTools ?? [])}. ` +
      'If goal, tools, schedule, or output is unclear, return {"questions":[...]} instead. ' +
      `Request: ${naturalLanguage}`;
    for (const model of [MODELS.chat, MODELS.cheap]) {
      try {
        const res = await this.client.interactions.create({
          model,
          input: prompt,
          response_format: { type: 'text', mime_type: 'application/json' },
        });
        return parseJson(outputText(res), { error: 'unparseable' });
      } catch (err) {
        if (model === MODELS.cheap || !(err instanceof Error && /429|quota|rate/i.test(err.message))) throw err;
      }
    }
    throw new Error('unreachable');
  }

  async titleFor(firstUserMessage: string): Promise<string> {
    // §8 auto-title. Cheap model, short cap.
    const res = await this.client.interactions.create({
      model: MODELS.cheap,
      input: `Title this chat in 6 words or fewer, no quotes, no trailing period: ${firstUserMessage}`,
    });
    return (outputText(res).trim() || 'New chat').replace(/^["']|["']$/g, '').slice(0, 80);
  }
}

export interface FunctionResultInput {
  type: 'function_result';
  name: string;
  call_id: string;
  result: string;
  is_error?: boolean;
}

/**
 * Input builder: plain transcript normally; §9 structured content array when the
 * last user turn carries inline attachments or extracted document text.
 * Content blocks verified against SDK typings: image/document accept base64 `data`
 * + `mime_type`; document mime limited to application/pdf and text/csv.
 */
function transcript(turns: ChatMessage[]): string {
  if (turns.length === 1) return turns[0]!.content ?? '';
  return turns.map((m) => `${m.role === 'model' ? 'Assistant' : 'User'}: ${m.content ?? ''}`).join('\n\n');
}

function buildInput(
  turns: ChatMessage[],
  attachments: InlinePart[] = [],
  extractedText?: string,
): string | Array<Record<string, unknown>> {
  if (!attachments.length && !extractedText) return transcript(turns);
  const last = turns[turns.length - 1]!;
  const prior = turns.slice(0, -1);
  const blocks: Array<Record<string, unknown>> = [];
  if (prior.length) blocks.push({ type: 'text', text: transcript(prior) + '\n\n' });
  if (extractedText) blocks.push({ type: 'text', text: `[Attached file content]\n${extractedText}\n\n` });
  for (const a of attachments) {
    blocks.push(a.mime.startsWith('image/')
      ? { type: 'image', mime_type: a.mime, data: a.data }
      : { type: 'document', mime_type: a.mime, data: a.data });
  }
  blocks.push({ type: 'text', text: last.content ?? '' });
  return blocks;
}

function toToolDef(t: ToolDef) {
  // Interactions API tool shape: fields at the top level, NOT nested under
  // `function` (that is OpenAI format and 400s with "Unknown parameter 'function'").
  return { type: 'function' as const, name: t.name, description: t.description, parameters: t.parameters };
}

function outputText(res: unknown): string {
  return (res as { output_text?: string }).output_text ?? '';
}

function parseJson(text: string, fallback: unknown): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return { ...(fallback as object), raw: text };
  }
}
