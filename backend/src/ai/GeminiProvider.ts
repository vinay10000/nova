import { GoogleGenAI } from '@google/genai';
import type { AIProvider, ChatMessage, StreamChunk, ToolDef } from './AIProvider.js';

// Verified model IDs (ai.google.dev/gemini-api/docs/models).
export const MODELS = {
  chat: 'gemini-3.8-flash',
  cheap: 'gemini-3.5-flash-lite',
  reasoning: 'gemini-3.1-pro-preview',
  image: 'gemini-3.1-flash-image',
  tts: 'gemini-3.1-flash-tts-preview',
  transcribe: 'gemini-3.5-transcribe',
  live: 'gemini-3.1-flash-live-preview',
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
    opts?: { model?: string; tools?: ToolDef[]; previousInteractionId?: string; functionResults?: FunctionResultInput[]; signal?: AbortSignal },
  ): AsyncGenerator<StreamChunk> {
    opts?.signal?.throwIfAborted?.();
    const system = messages.filter((m) => m.role === 'system').map((m) => m.content).join('\n');
    const turns = messages.filter((m) => m.role !== 'system');

    const stream = await this.client.interactions.create({
      model: opts?.model ?? MODELS.chat,
      input: opts?.functionResults?.length
        ? opts.functionResults
        : transcript(turns),
      ...(opts?.previousInteractionId ? { previous_interaction_id: opts.previousInteractionId } : {}),
      ...(system ? { system_instruction: system } : {}),
      ...(opts?.tools?.length ? { tools: opts.tools.map(toToolDef) } : {}),
      store: true, // required for previous_interaction_id chaining
      stream: true,
    });

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

/** Turn 1 input: a plain string transcript. Simplest form the API accepts. */
function transcript(turns: ChatMessage[]): string {
  if (turns.length === 1) return turns[0]!.content;
  return turns.map((m) => `${m.role === 'model' ? 'Assistant' : 'User'}: ${m.content}`).join('\n\n');
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
