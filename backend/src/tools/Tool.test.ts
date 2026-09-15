// Runnable check for the §47 safety pipeline. Plain node:assert — no framework.
//   npx tsx --test src/tools/Tool.test.ts   OR   npx tsx src/tools/Tool.test.ts
import assert from 'node:assert/strict';
import { runToolWithSafety, type Tool, type ToolContext } from './Tool.js';

// Mock db — test tools don't actually use it (the test calls are gated before execute).
const mockDb = {} as ToolContext['db'];
const ctx: ToolContext = { userId: 'u1', agentId: 'a1', permissions: ['github.issues.read'], db: mockDb };

const readTool: Tool = {
  id: 'github_list_issues',
  description: 'List issues',
  inputSchema: {},
  scope: 'github.issues.read',
  isWrite: false,
  approval: 'write',
  execute: async () => ({ issues: 5 }),
};

const writeTool: Tool = { ...readTool, id: 'github_create_issue', scope: 'github.issues.write', isWrite: true };
const alwaysTool: Tool = { ...readTool, id: 'x.post', scope: 'github.issues.read', isWrite: true, approval: 'always' };

const ok = { hasPermission: true, hasAuth: true };

// 1. unknown tool denies
assert.equal((await runToolWithSafety(undefined, 'nope', {}, ctx, ok)).status, 'denied');

// 2. missing agent permission denies — even though the caller says hasPermission
{
  const r = await runToolWithSafety(readTool, readTool.id, {}, ctx, { hasPermission: true, hasAuth: true });
  assert.equal(r.status, 'ok'); // scope is held, so this one is allowed
  const r2 = await runToolWithSafety(writeTool, writeTool.id, {}, ctx, { hasPermission: true, hasAuth: true });
  assert.equal(r2.status, 'denied');
  assert.equal(r2.code, 'permission_required');
}

// 3. missing authorization denies and names the provider to connect
{
  const r = await runToolWithSafety(readTool, readTool.id, {}, ctx, { hasPermission: true, hasAuth: false });
  assert.equal(r.status, 'denied');
  assert.equal(r.code, 'auth_required');
  assert.equal(r.connect, 'github');
}

// 4. write tool with no approval channel SUSPENDS, never executes
{
  const wide: ToolContext = { ...ctx, permissions: ['github.issues.write'] };
  let executed = false;
  const spy: Tool = { ...writeTool, execute: async () => { executed = true; return 'wrote'; } };
  const r = await runToolWithSafety(spy, spy.id, { title: 't' }, wide, { hasPermission: true, hasAuth: true });
  assert.equal(r.status, 'approval_required');
  assert.equal(executed, false, 'write must not execute without approval');
}

// 5. rejected approval does not execute
{
  const wide: ToolContext = { ...ctx, permissions: ['github.issues.write'] };
  let executed = false;
  const spy: Tool = { ...writeTool, execute: async () => { executed = true; return 'wrote'; } };
  const r = await runToolWithSafety(spy, spy.id, {}, wide, { hasPermission: true, hasAuth: true, approve: async () => false });
  assert.equal(r.status, 'rejected');
  assert.equal(executed, false);
}

// 6. approved write executes once
{
  const wide: ToolContext = { ...ctx, permissions: ['github.issues.write'] };
  let calls = 0;
  const spy: Tool = { ...writeTool, execute: async () => { calls++; return 'wrote'; } };
  const r = await runToolWithSafety(spy, spy.id, {}, wide, { hasPermission: true, hasAuth: true, approve: async () => true });
  assert.deepEqual(r, { status: 'ok', result: 'wrote' });
  assert.equal(calls, 1);
}

// 7. `approval: 'always'` gates even a read
{
  const r = await runToolWithSafety(alwaysTool, alwaysTool.id, {}, ctx, { hasPermission: true, hasAuth: true });
  assert.equal(r.status, 'approval_required');
}

// 8. execute() throwing becomes an error outcome, not an exception
{
  const bad: Tool = { ...readTool, execute: async () => { throw new Error('boom'); } };
  const r = await runToolWithSafety(bad, bad.id, {}, ctx, ok);
  assert.equal(r.status, 'error');
}

// 9. audit fires on every path
{
  const seen: string[] = [];
  const audit = async (o: { status: string }) => { seen.push(o.status); };
  await runToolWithSafety(readTool, readTool.id, {}, ctx, { ...ok, audit });
  await runToolWithSafety(writeTool, writeTool.id, {}, ctx, { ...ok, audit });
  assert.deepEqual(seen, ['ok', 'denied']);
}

console.log('Tool.ts safety gates: all checks passed');
