import type { AIProvider, ChatMessage, InlinePart, StreamChatOptions, StreamChunk } from './AIProvider.js';
import { MODELS, resolveModelEffort } from './models.js';

// Model policy lives in ./models.js (single source of truth, F1 fix).
export { MODELS } from './models.js';

// Function calling: the AI Studio OpenAI-compatible endpoint supports tools
// natively, so chat and tools share one router. Keys live in env, never in the repo.
const TOOLS_BASE_URL = process.env.TOOLS_BASE_URL ?? 'https://generativelanguage.googleapis.com/v1beta/openai';
const TOOLS_API_KEY = process.env.TOOLS_API_KEY ?? process.env.GEMINI_API_KEY ?? '';
const TOOLS_MODEL = process.env.TOOLS_MODEL ?? MODELS.tools;

// Chat/vision default to the same AI Studio endpoint.
const CHAT_BASE_URL = process.env.CHAT_BASE_URL ?? TOOLS_BASE_URL;
const CHAT_API_KEY = process.env.CHAT_API_KEY ?? TOOLS_API_KEY;

// Only these models are known to support function calling on the tools router.
// Anything else (e.g. a chat-router model id picked in the UI) is replaced by
// the tools model — otherwise the request 400s and the whole reply is lost.
const TOOL_CAPABLE_MODELS = new Set<string>([TOOLS_MODEL, MODELS.chat, MODELS.vision]);

export class OpenAIProvider implements AIProvider {
  private baseUrl: string;
  private apiKey: string;

  constructor() {
    // Non-streaming helpers (builder, titles) share the chat router.
    this.baseUrl = CHAT_BASE_URL;
    this.apiKey = CHAT_API_KEY;
    if (!this.apiKey) console.warn('[openai] no chat API key (CHAT_API_KEY/TOOLS_API_KEY) — chat will fail until set.');
  }

  async *streamChat(
    messages: ChatMessage[],
    opts?: StreamChatOptions,
  ): AsyncGenerator<StreamChunk> {
    opts?.signal?.throwIfAborted?.();

    // Route to the function-calling router when tools are present.
    // When it is not configured, still send the tools on the normal router — a
    // text-only answer beats a hard failure (§19: degrade honestly, never 500).
    const hasTools = !!opts?.tools?.length;
    const toolsRouterConfigured = hasTools && !!TOOLS_API_KEY;
    const baseUrl = toolsRouterConfigured ? TOOLS_BASE_URL : CHAT_BASE_URL;
    const apiKey = toolsRouterConfigured ? TOOLS_API_KEY : CHAT_API_KEY;
    const model = toolsRouterConfigured
      ? (opts?.model && TOOL_CAPABLE_MODELS.has(opts.model) ? opts.model : TOOLS_MODEL)
      : (opts?.model && TOOL_CAPABLE_MODELS.has(opts.model) ? opts.model : (opts?.model ?? MODELS.chat));

    if (hasTools && !TOOLS_API_KEY) {
      console.warn('[openai] TOOLS_API_KEY is not set — tools are sent without the tools router; function calling may be unavailable.');
    }

    const oaiMessages = toOpenAIMessages(messages, opts?.attachments, opts?.extractedText);
    const body: Record<string, unknown> = {
      model,
      messages: oaiMessages,
      stream: true,
    };

    const effort = resolveModelEffort(opts?.effort);
    if (effort) {
      body.reasoning_effort = effort;
    } else if (toolsRouterConfigured) {
      body.reasoning_effort = 'medium';
    }

    if (opts?.tools?.length) {
      body.tools = opts.tools.map((t) => ({
        type: 'function',
        function: { name: t.name, description: t.description, parameters: t.parameters },
      }));
    }

    const res = await fetch(`${baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${apiKey}`,
      },
      body: JSON.stringify(body),
      signal: opts?.signal,
    });

    if (!res.ok) {
      const text = await res.text().catch(() => '');
      throw new Error(`openai_upstream_${res.status}: ${text.slice(0, 300)}`);
    }

    const reader = res.body?.getReader();
    if (!reader) throw new Error('openai_no_response_body');

    const decoder = new TextDecoder();
    let buffer = '';
    let sawDone = false;

    // Buffer for tool call arguments that arrive across multiple chunks.
    const toolCalls = new Map<number, { id: string; name: string; args: string }>();

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        const lines = buffer.split('\n');
        buffer = lines.pop() ?? '';

        for (const line of lines) {
          const trimmed = line.trim();
          if (!trimmed || trimmed === 'data: [DONE]') {
            if (trimmed === 'data: [DONE]') sawDone = true;
            continue;
          }
          if (!trimmed.startsWith('data: ')) continue;

          let parsed: Record<string, unknown>;
          try {
            parsed = JSON.parse(trimmed.slice(6));
          } catch {
            continue;
          }

          const choices = parsed.choices as Array<{ delta?: { content?: string; reasoning_content?: string; tool_calls?: Array<{ index: number; id?: string; function?: { name?: string; arguments?: string } }> }; finish_reason?: string }> | undefined;
          if (!choices?.length) continue;

          for (const choice of choices) {
            // finish_reason usually arrives with an empty delta, so read it first.
            // 'tool_calls' is a normal, complete finish — not an error (§15).
            if (choice.finish_reason) {
              sawDone = true;
            }

            const delta = choice.delta;
            if (!delta) continue;

            // Reasoning tokens (thinking)
            if (delta.reasoning_content) {
              yield { type: 'reasoning', text: delta.reasoning_content };
            }

            // Text content
            if (delta.content) {
              yield { type: 'token', text: delta.content };
            }

            // Tool calls — arguments may arrive incrementally
            if (delta.tool_calls) {
              for (const tc of delta.tool_calls) {
                const existing = toolCalls.get(tc.index);
                if (existing) {
                  if (tc.id) existing.id = tc.id;
                  if (tc.function?.name) existing.name = tc.function.name;
                  if (tc.function?.arguments) existing.args += tc.function.arguments;
                } else {
                  toolCalls.set(tc.index, {
                    id: tc.id ?? `tc_${Date.now()}_${tc.index}`,
                    name: tc.function?.name ?? '',
                    args: tc.function?.arguments ?? '',
                  });
                }
              }
            }
          }
        }
      }
    } finally {
      reader.releaseLock();
    }

    // Flush any accumulated tool calls
    for (const [, tc] of toolCalls) {
      if (!tc.name) continue;
      let args: unknown = {};
      try { args = tc.args ? JSON.parse(tc.args) : {}; } catch { args = {}; }
      yield { type: 'tool_call', toolId: tc.name, callId: tc.id, args };
    }

    // A closed response body is a finished turn. Some routers omit finish_reason
    // on tool-call turns; treating that as a failure would delete a valid reply.
    if (!sawDone) console.warn('[openai] stream ended without finish_reason — treating as complete');
    yield { type: 'done' };
  }

  async generateAgentConfig(naturalLanguage: string, knownTools?: string[]): Promise<unknown> {
    const prompt =
      'Convert the request into an agent configuration. ' +
      `The tools array may ONLY contain these ids: ${JSON.stringify(knownTools ?? [])}. ` +
      'If goal, tools, schedule, or output is unclear, return {"questions":[...]} instead. ' +
      `Request: ${naturalLanguage}`;

    const res = await fetch(`${this.baseUrl}/chat/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${this.apiKey}` },
      body: JSON.stringify({
        model: MODELS.chat,
        messages: [{ role: 'user', content: prompt }],
        response_format: { type: 'json_object' },
      }),
    });

    if (!res.ok) throw new Error(`agent_config_upstream_${res.status}`);
    const data = await res.json() as { choices?: Array<{ message?: { content?: string } }> };
    const text = data.choices?.[0]?.message?.content ?? '';
    try { return JSON.parse(text); } catch { return { error: 'unparseable', raw: text }; }
  }

  async titleFor(firstUserMessage: string): Promise<string> {
    const res = await fetch(`${this.baseUrl}/chat/completions`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${this.apiKey}` },
      body: JSON.stringify({
        model: MODELS.cheap,
        messages: [{ role: 'user', content: `Title this chat in 6 words or fewer, no quotes, no trailing period: ${firstUserMessage}` }],
        max_tokens: 30,
      }),
    });

    if (!res.ok) return 'New chat';
    const data = await res.json() as { choices?: Array<{ message?: { content?: string } }> };
    return (data.choices?.[0]?.message?.content?.trim() || 'New chat').replace(/^["']|["']$/g, '').slice(0, 80);
  }
}

function toOpenAIMessages(
  messages: ChatMessage[],
  attachments?: InlinePart[],
  extractedText?: string,
): Array<Record<string, unknown>> {
  const result: Array<Record<string, unknown>> = [];

  for (let i = 0; i < messages.length; i++) {
    const m = messages[i];
    const isLast = i === messages.length - 1 && m.role === 'user';
    const hasExtras = isLast && ((attachments?.length ?? 0) > 0 || extractedText);

    // Assistant message with tool_calls
    if (m.role === 'model' && m.tool_calls?.length) {
      result.push({
        role: 'assistant',
        content: m.content ?? null,
        tool_calls: m.tool_calls,
      });
      continue;
    }

    // Tool result message
    if (m.role === 'tool' && m.tool_call_id) {
      result.push({
        role: 'tool',
        tool_call_id: m.tool_call_id,
        content: m.content ?? '',
      });
      continue;
    }

    // Map 'model' → 'assistant' for OpenAI format
    const role = m.role === 'model' ? 'assistant' : m.role;

    if (!hasExtras) {
      result.push({ role, content: m.content ?? '' });
      continue;
    }

    // Build multipart content for the last user message with attachments
    const parts: Array<Record<string, unknown>> = [];
    if (attachments?.length) {
      for (const a of attachments) {
        if (a.mime.startsWith('image/')) {
          parts.push({ type: 'image_url', image_url: { url: `data:${a.mime};base64,${a.data}` } });
        } else {
          parts.push({ type: 'text', text: `[Attached file (${a.mime})]` });
        }
      }
    }
    if (extractedText) {
      parts.push({ type: 'text', text: `[Attached file content]\n${extractedText}` });
    }
    parts.push({ type: 'text', text: m.content ?? '' });
    result.push({ role: 'user', content: parts });
  }

  return result;
}
