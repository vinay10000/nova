import assert from 'node:assert/strict';
import type { GoogleGenAI } from '@google/genai';
import type { StreamChunk } from './AIProvider.js';
import { GeminiProvider, buildInteractionRequest } from './GeminiProvider.js';
import { MODELS } from './models.js';

const messages = [{ role: 'user' as const, content: 'hello' }];

for (const effort of ['minimal', 'low', 'medium', 'high'] as const) {
  const request = buildInteractionRequest(messages, { model: MODELS.chat, effort }, MODELS.chat);
  assert.equal(request.model, MODELS.chat);
  assert.deepEqual(request.generation_config, { thinking_level: effort, thinking_summaries: 'auto' });
}

assert.deepEqual(buildInteractionRequest(messages, { model: MODELS.chat }, MODELS.chat).generation_config, { thinking_summaries: 'auto' });
assert.deepEqual(buildInteractionRequest(messages, { effort: 'invalid' } as never, MODELS.chat).generation_config, { thinking_summaries: 'auto' });

const followUp = buildInteractionRequest(
  messages,
  { model: MODELS.vision, effort: 'high', previousInteractionId: 'prev-1' },
  MODELS.vision,
);
assert.equal(followUp.model, MODELS.vision);
assert.equal(followUp.previous_interaction_id, 'prev-1');
assert.deepEqual(followUp.generation_config, { thinking_level: 'high', thinking_summaries: 'auto' });

async function collect(provider: GeminiProvider, model?: string, effort?: 'minimal' | 'low' | 'medium' | 'high') {
  const chunks: StreamChunk[] = [];
  for await (const chunk of provider.streamChat(messages, { model, effort })) chunks.push(chunk);
  return chunks;
}

const calls: Array<Record<string, unknown>> = [];
const transientClient = {
  interactions: {
    create: async (request: Record<string, unknown>) => {
      calls.push(request);
      if (calls.length === 1) throw new Error('503 upstream busy');
      return (async function* () {
        yield { event_type: 'interaction.completed', interaction: { id: 'i-2' } };
      })();
    },
  },
} as unknown as GoogleGenAI;

const fallbackProvider = new GeminiProvider('test-key', transientClient);
await collect(fallbackProvider, MODELS.chat, 'high');
assert.equal(calls.length, 2);
assert.equal(calls[0]?.model, MODELS.chat);
assert.equal(calls[1]?.model, MODELS.vision);
assert.deepEqual(calls[0]?.generation_config, { thinking_level: 'high', thinking_summaries: 'auto' });
assert.deepEqual(calls[1]?.generation_config, { thinking_level: 'high', thinking_summaries: 'auto' });

const rejectedCalls: Array<Record<string, unknown>> = [];
const rejectedClient = {
  interactions: {
    create: async (request: Record<string, unknown>) => {
      rejectedCalls.push(request);
      throw new Error('400 invalid request');
    },
  },
} as unknown as GoogleGenAI;

await assert.rejects(() => collect(new GeminiProvider('test-key', rejectedClient), MODELS.chat, 'low'));
assert.equal(rejectedCalls.length, 1);

const reasoningClient = {
  interactions: {
    create: async () => (async function* () {
      yield { event_type: 'step.start', index: 0, step: { type: 'thought', summary: [{ type: 'text', text: 'Weighing the options' }] } };
      yield { event_type: 'step.delta', index: 0, delta: { type: 'thought_summary', content: { text: 'Weighing the options' } } };
      yield { event_type: 'step.delta', index: 0, delta: { type: 'thought_summary', content: { text: ' carefully' } } };
      yield { event_type: 'step.delta', index: 1, delta: { type: 'text', text: 'Done' } };
      yield { event_type: 'interaction.completed', interaction: { id: 'i-3' } };
    })(),
  },
} as unknown as GoogleGenAI;

const reasoningChunks = await collect(new GeminiProvider('test-key', reasoningClient), MODELS.chat, 'medium');
assert.equal(
  reasoningChunks.filter((chunk) => chunk.type === 'reasoning').map((chunk) => chunk.text).join(''),
  'Weighing the options carefully',
);
assert.equal(reasoningChunks.filter((chunk) => chunk.type === 'token').map((chunk) => chunk.text).join(''), 'Done');

console.log('gemini provider checks passed');
