import assert from 'node:assert/strict';
import type { AIProvider, StreamChunk } from '../ai/AIProvider.js';
import { MODELS } from '../ai/models.js';
import { createChatService, type ChatStore } from './chatService.js';

function recordingProvider(calls: Array<Record<string, unknown>>): AIProvider {
  return {
    async *streamChat(_messages, opts) {
      calls.push({ ...(opts ?? {}) });
      yield { type: 'reasoning', text: 'Considering the options' } satisfies StreamChunk;
      yield { type: 'token', text: 'ok' } satisfies StreamChunk;
      yield { type: 'done', interactionId: 'i-1' } satisfies StreamChunk;
    },
    async generateAgentConfig() {
      return {};
    },
    async titleFor() {
      return 'title';
    },
  };
}

function recordingStore(): ChatStore {
  return {
    async loadMessages() {
      return [];
    },
    async appendMessage() {
      return 'm1';
    },
    async titleConversation() {},
  };
}

async function drain(service: ReturnType<typeof createChatService>, options: Parameters<typeof service.stream>[0]) {
  const chunks: StreamChunk[] = [];
  for await (const chunk of service.stream(options)) chunks.push(chunk);
  return chunks;
}

const calls: Array<Record<string, unknown>> = [];
const service = createChatService(recordingProvider(calls), recordingStore());
const firstStream = await drain(service, {
  conversationId: 'c1',
  userId: 'u1',
  message: 'hello',
  model: MODELS.vision,
  effort: 'medium',
});
assert.equal(calls[0]?.model, MODELS.vision);
assert.equal(calls[0]?.effort, 'medium');
assert.equal(firstStream.filter((chunk) => chunk.type === 'reasoning').map((chunk) => chunk.text).join(''), 'Considering the options');

calls.length = 0;
const attachments = {
  async loadAttachments() {
    return [
      {
        id: 'f1',
        mime: 'image/png',
        status: 'ready',
        inlineData: { mime: 'image/png', data: 'AA==' },
      },
    ];
  },
  async linkAttachments() {},
};
const visionService = createChatService(recordingProvider(calls), recordingStore(), attachments);
await drain(visionService, {
  conversationId: 'c2',
  userId: 'u1',
  message: 'look',
  attachmentIds: ['f1'],
  effort: 'high',
});
assert.equal(calls[0]?.model, MODELS.vision);
assert.equal(calls[0]?.effort, 'high');

console.log('chat service effort checks passed');
