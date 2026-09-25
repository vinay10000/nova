import assert from 'node:assert/strict';
import { OpenAIProvider } from './OpenAIProvider.js';
import { MODELS } from './models.js';

const originalFetch = globalThis.fetch;
const bodies: Array<Record<string, unknown>> = [];
const encoder = new TextEncoder();

globalThis.fetch = (async (_input: string | URL | Request, init?: RequestInit) => {
  bodies.push(JSON.parse(String(init?.body)) as Record<string, unknown>);
  return new Response(
    new ReadableStream<Uint8Array>({
      start(controller) {
        controller.enqueue(encoder.encode('data: [DONE]\n\n'));
        controller.close();
      },
    }),
    { status: 200, headers: { 'content-type': 'text/event-stream' } },
  );
}) as typeof fetch;

async function send(effort?: 'minimal' | 'low' | 'medium' | 'high', tools = false) {
  const provider = new OpenAIProvider();
  const opts = tools
    ? { model: MODELS.tools, effort, tools: [{ name: 'noop', description: 'noop', parameters: { type: 'object' } }] }
    : { model: MODELS.chat, effort };
  for await (const _chunk of provider.streamChat([{ role: 'user', content: 'hello' }], opts)) {
    void _chunk;
  }
}

try {
  for (const effort of ['minimal', 'low', 'medium', 'high'] as const) {
    bodies.length = 0;
    await send(effort);
    assert.equal(bodies[0]?.model, MODELS.chat);
    assert.equal(bodies[0]?.reasoning_effort, effort);
  }

  bodies.length = 0;
  await send('low', true);
  assert.equal(bodies[0]?.reasoning_effort, 'low');
  assert.ok(Array.isArray(bodies[0]?.tools));

  bodies.length = 0;
  await send();
  assert.equal('reasoning_effort' in (bodies[0] ?? {}), false);
} finally {
  globalThis.fetch = originalFetch;
}

console.log('openai provider checks passed');
