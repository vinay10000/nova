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

// Model returns role 'model'; the Interactions API input expects 'user'/'model'.
type Step = { type: string; name?: string; arguments?: unknown; call_id?: string; content?: unknown };

/** Map Gemini steps to our normalized stream chunks. */
function* stepToChunks(step: Step): Generator<StreamChunk> {
  if (step.type === 'text' && typeof step.content === 'string') {
    yield { type: 'token', text: step.content };
  }
  if (step.type === 'function_call' && step.name) {
    yield { type: 'tool_call', toolId: step.name, callId: step.call_id, args: step.arguments };
  }
}

// §2-§3 Gemini via backend only. Function calling is the primary tool bridge (§15).
export class GeminiProvider implements AIProvider {
  private client: GoogleGenAI;

  constructor(apiKey = process.env.GEMINI_API_KEY ?? '') {
    if (!apiKey) console.warn('[gemini] GEMINI_API_KEY missing — chat fails until set server-side.');
    this.client = new GoogleGenAI({ apiKey });
  }

  async *streamChat(
    messages: ChatMessage[],
    opts?: { model?: string; tools?: ToolDef[]; previousInteractionId?: string; signal?: AbortSignal },
  ): AsyncGenerator<StreamChunk> {
    const stream = await this.client.interactions.create({
      model: opts?.model ?? MODELS.chat,
      // Verified: turn 1 sends the new user text; follow-ups pass previous_interaction_id
      // plus a function_result input instead of replaying history.
      ...(opts?.previousInteractionId
        ? { previous_interaction_id: opts.previousInteractionId }
        : { input: toInput(messages) }),
      ...(opts?.tools?.length ? { tools: opts.tools.map(toToolDef) } : {}),
      stream: true,
    });

    let interactionId: string | undefined;
    for await (const event of stream as AsyncIterable<{ event_type?: string; interaction_id?: string; step?: Step }>) {
      interactionId = event.interaction_id ?? interactionId;
      if (event.step) yield* stepToChunks(event.step);
    }
    yield { type: 'done', interactionId };
  }

  async generateAgentConfig(naturalLanguage: string): Promise<unknown> {
    // §12-§14: goal/tools/permissions/schedule, plus questions[] when input is incomplete.
    const res = await this.client.interactions.create({
      model: MODELS.reasoning,
      input: [
        {
          type: 'text',
          text:
            'Convert the request into an agent configuration matching shared/agent-config.schema.json. ' +
            'If goal, tools, schedule, or output is unclear, return {"questions":[...]} instead. ' +
            `Request: ${naturalLanguage}`,
        },
      ],
      response_format: { type: 'json_object' },
    });
    const text = (res as { output_text?: string }).output_text ?? '';
    try {
      return JSON.parse(text);
    } catch {
      return { questions: ['Could not parse a configuration. Rephrase the goal?'], raw: text };
    }
  }

  async titleFor(firstUserMessage: string): Promise<string> {
    // §8 auto-title. Cheap model, short cap.
    const res = await this.client.interactions.create({
      model: MODELS.cheap,
      input: [{ type: 'text', text: `Title this chat in 6 words or fewer, no quotes: ${firstUserMessage}` }],
    });
    return ((res as { output_text?: string }).output_text ?? 'New chat').trim().slice(0, 80);
  }
}

function toInput(messages: ChatMessage[]) {
  return messages.map((m) => ({
    type: 'text' as const,
    text: m.role === 'system' ? `[system] ${m.content}` : m.content,
    ...(m.role === 'model' ? { role: 'model' } : {}),
  }));
}

function toToolDef(t: ToolDef) {
  return { type: 'function' as const, name: t.name, description: t.description, parameters: t.parameters };
}
