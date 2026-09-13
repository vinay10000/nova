import { GoogleGenAI } from '@google/genai';
import type { AIProvider, ChatMessage, InlinePart, StreamChunk, ToolDef } from './AIProvider.js';

// Verified model IDs (ai.google.dev/gemini-api/docs/models). Chat-capable models only —
// the model selector serves this list, so audio/image variants must not leak in.
// TTS output = Fish Audio S2.1 via OpenRouter (/v1/tts); STT = Android SpeechRecognizer.
export const MODELS = {
  chat: 'gemini-3.8-flash',
  cheap: 'gemini-3.5-flash-lite',
  reasoning: 'gemini-3.1-pro-preview',
} as const;

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
   * Streaming emits `step.delta` events carrying `TextDelta { type:'text', text }`.
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

    const stream = (await this.client.interactions.create({
      model: opts?.model ?? MODELS.chat,
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
      delta?: { type: string; text: string };
      step?: { type: string; name?: string; id?: string; arguments?: unknown };
      interaction?: { id?: string };
    }>;

    for await (const event of stream) {
      if (opts?.signal?.aborted) break;
      if (event.event_type === 'step.delta' && event.delta?.type === 'text') {
        yield { type: 'token', text: event.delta.text };
      }
      // step.start carries a complete function_call step (name/arguments/call_id).
      if (event.event_type === 'step.start' && event.step?.type === 'function_call' && event.step.name) {
        // Verified: FunctionCallStep exposes `id` (used as call_id on the matching function_result).
        yield { type: 'tool_call', toolId: event.step.name, callId: event.step.id, args: event.step.arguments };
      }
      if (event.event_type === 'interaction.completed') {
        yield { type: 'done', interactionId: event.interaction?.id };
      }
    }
  }

  async generateAgentConfig(naturalLanguage: string): Promise<unknown> {
    // §12-§14: goal/tools/permissions/schedule, plus questions[] when input is incomplete.
    const res = await this.client.interactions.create({
      model: MODELS.reasoning,
      input:
        'Convert the request into an agent configuration matching shared/agent-config.schema.json. ' +
        'If goal, tools, schedule, or output is unclear, return {"questions":[...]} instead. ' +
        `Request: ${naturalLanguage}`,
      response_format: { type: 'text', mime_type: 'application/json' },
    });
    return parseJson(outputText(res), { error: 'unparseable' });
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
  if (turns.length === 1) return turns[0]!.content;
  return turns.map((m) => `${m.role === 'model' ? 'Assistant' : 'User'}: ${m.content}`).join('\n\n');
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
  blocks.push({ type: 'text', text: last.content });
  return blocks;
}

function toToolDef(t: ToolDef) {
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
