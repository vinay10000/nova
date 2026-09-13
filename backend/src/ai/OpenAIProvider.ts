import type { AIProvider, ChatMessage, InlinePart, StreamChunk, ToolDef } from './AIProvider.js';

export const MODELS = {
  chat: 'tencent-hy3-free',
  cheap: 'tencent-hy3-free',
  reasoning: 'tencent-hy3-free',
} as const;

const BASE_URL = 'https://router.bynara.id/v1';
const API_KEY = process.env.AI_API_KEY ?? '';

export class OpenAIProvider implements AIProvider {
  private baseUrl: string;
  private apiKey: string;

  constructor() {
    this.baseUrl = BASE_URL;
    this.apiKey = API_KEY;
    if (!this.apiKey) console.warn('[openai] AI_API_KEY missing — chat will fail until set.');
  }

  async *streamChat(
    messages: ChatMessage[],
    opts?: {
      model?: string;
      tools?: ToolDef[];
      signal?: AbortSignal;
      attachments?: InlinePart[];
      extractedText?: string;
    },
  ): AsyncGenerator<StreamChunk> {
    opts?.signal?.throwIfAborted?.();

    const oaiMessages = toOpenAIMessages(messages, opts?.attachments, opts?.extractedText);
    const body: Record<string, unknown> = {
      model: opts?.model ?? MODELS.chat,
      messages: oaiMessages,
      stream: true,
    };

    if (opts?.tools?.length) {
      body.tools = opts.tools.map((t) => ({
        type: 'function',
        function: { name: t.name, description: t.description, parameters: t.parameters },
      }));
    }

    const res = await fetch(`${this.baseUrl}/chat/completions`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.apiKey}`,
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

          const choices = parsed.choices as Array<{ delta?: { content?: string; tool_calls?: Array<{ index: number; id?: string; function?: { name?: string; arguments?: string } }> }; finish_reason?: string }> | undefined;
          if (!choices?.length) continue;

          for (const choice of choices) {
            const delta = choice.delta;
            if (!delta) continue;

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

            // Stream finished
            if (choice.finish_reason === 'stop') {
              sawDone = true;
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

    if (!sawDone) throw new Error('openai_stream_incomplete');
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
): Array<{ role: string; content: string | Array<Record<string, unknown>> }> {
  const result: Array<{ role: string; content: string | Array<Record<string, unknown>> }> = [];

  for (let i = 0; i < messages.length; i++) {
    const m = messages[i];
    const isLast = i === messages.length - 1 && m.role === 'user';
    const hasExtras = isLast && ((attachments?.length ?? 0) > 0 || extractedText);

    if (!hasExtras) {
      result.push({ role: m.role === 'model' ? 'assistant' : m.role, content: m.content });
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
    parts.push({ type: 'text', text: m.content });
    result.push({ role: 'user', content: parts });
  }

  return result;
}
