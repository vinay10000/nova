// browserTaskStore tests: history + session contexts via Prisma, structural fallback.
import assert from 'node:assert/strict';
import { BrowserTaskStore } from './browserTaskStore.js';

const rows: Record<string, unknown>[] = [];
function makeDb(hasModels: boolean) {
  const task = {
    create: async ({ data }: { data: Record<string, unknown> }) => {
      const row = { id: `t${rows.length + 1}`, createdAt: new Date(), ...data };
      rows.push(row);
      return row;
    },
    update: async ({ where, data }: { where: { id: string }; data: Record<string, unknown> }) => {
      const row = rows.find((r) => r.id === where.id) as Record<string, unknown>;
      Object.assign(row, data);
      return row;
    },
    findMany: async ({ where }: { where?: Record<string, unknown> }) =>
      rows.filter((r) => !where || Object.entries(where).every(([k, v]) => r[k] === v)),
    findFirst: async ({ where }: { where?: Record<string, unknown> }) =>
      (rows.find((r) => !where || Object.entries(where).every(([k, v]) => r[k] === v)) ?? null),
  };
  const session = {
    create: async ({ data }: { data: Record<string, unknown> }) => ({ id: 's1', createdAt: new Date(), ...data }),
    findFirst: async ({ where }: { where: Record<string, unknown> }) => {
      const hit = rows.find((r) => r.sessionUserId === where.userId && r.sessionProvider === where.provider);
      return hit ? { id: 'ctx1', contextId: hit.contextId } : null;
    },
  };
  return {
    browserTask: hasModels ? task : undefined,
    browserSession: hasModels ? session : undefined,
  } as never;
}

const ctx = { userId: 'u1', agentId: 'a1', permissions: [], db: makeDb(true) } as never;

const store = new BrowserTaskStore(ctx);
const rec = await store.recordStart({ userId: 'u1', agentId: 'a1', executionId: 'e1', provider: 'browserless', url: 'https://example.com/', instruction: 'read' });
assert.ok(rec?.id);
assert.equal(rec?.status, 'running');
await store.recordComplete(rec!.id as string, { status: 'completed', text: 'hello', sessionId: 'sb1' });
const list = await store.listForUser('u1');
assert.equal(list.length, 1);
assert.equal(list[0].status, 'completed');
assert.equal(list[0].text, 'hello');

const expired = await store.findLiveSession({ userId: 'u1', provider: 'browserbase', now: new Date('2020-01-01') });
assert.equal(expired, null, 'expired session must not be reused');

const open = await store.findLiveSession({ userId: 'u1', provider: 'browserbase', now: new Date(Date.now() + 3_600_000) });
assert.equal(open, null, 'no session row yet');

const withCtx = await store.saveSession({ userId: 'u1', provider: 'browserbase', contextId: 'ctx-abc', ttlMs: 3_600_000 });
assert.equal(withCtx.contextId, 'ctx-abc');

// Fallback: no prisma models -> store stays inert and reports unsupported honestly.
const store2 = new BrowserTaskStore({ userId: 'u2', agentId: '', permissions: [], db: makeDb(false) } as never);
const rec2 = await store2.recordStart({ userId: 'u2', provider: 'browserless', url: 'https://example.com/', instruction: 'x' });
assert.equal(rec2, null);
await assert.rejects(() => store2.recordStart({ userId: 'u2', provider: 'browserless', url: 'https://example.com/', instruction: 'x', required: true }), /browser_history_unavailable/);

console.log('browserTaskStore: all checks passed');
