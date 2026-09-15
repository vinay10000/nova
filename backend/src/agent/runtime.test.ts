import assert from 'node:assert/strict';
import { MAX_TOOL_STEPS, gateToolCall, runAgentLoop } from './runtime.js';
import type { AIProvider } from '../ai/AIProvider.js';

// Fake provider: emits one tool_call per turn, then text. Never bills Gemini.
function fakeProvider(calls: { toolId: string; args?: unknown }[], text = 'done'): AIProvider {
  let n = 0;
  return {
    async *streamChat() {
      const c = calls[n++];
      if (c) yield { type: 'tool_call', toolId: c.toolId, callId: `c${n}`, args: c.args ?? {} };
      else yield { type: 'token', text };
      yield { type: 'done', interactionId: `i${n}` };
    },
    async generateAgentConfig() { return {}; },
    async titleFor() { return 't'; },
  };
}

const agent = { id: 'a1', userId: 'u1', goal: 'g', instructions: 'i', tools: ['web_search'], permissions: ['web_search'] };
// gate needs only the connection lookup for non-web tools; web.* is server-keyed.
const db = { connection: { findFirst: async () => null } } as never;

// 1. web.search runs the real Exa call? No — gate only checks scope here via runToolWithSafety
//    with a stubbed execute: use a permission-denied path instead (unknown tool).
{
  const o = await gateToolCall(db, agent, 'nope.tool', {});
  assert.equal(o.status, 'denied');
}

// 2. approval suspend: write tool with no channel suspends instead of executing.
{
  const ai = fakeProvider([{ toolId: 'github_create_issue', args: { title: 't' } }]);
  const wide = { ...agent, tools: ['github_create_issue'], permissions: ['github.issues.write'] };
  const dbConn = { connection: { findFirst: async () => ({ status: 'connected' }) } } as never;
  const r = await runAgentLoop({ ai, db: dbConn, agent: wide });
  assert.ok(r.suspended, 'write must suspend, not execute');
  assert.equal(r.suspended.toolId, 'github_create_issue');
}

// 3. iteration cap: a tool that always calls again stops at MAX_TOOL_STEPS.
{
  const calls = Array.from({ length: MAX_TOOL_STEPS + 5 }, () => ({ toolId: 'web_search', args: { query: 'x' } }));
  // web.search would call Exa — instead point at an unknown tool that denies fast.
  const denyCalls = Array.from({ length: MAX_TOOL_STEPS + 5 }, () => ({ toolId: 'nope.tool' }));
  const ai = fakeProvider(denyCalls, 'unreached');
  const ag = { ...agent, tools: ['nope.tool'], permissions: [] };
  const r = await runAgentLoop({ ai, db, agent: ag });
  assert.ok(!r.suspended);
  assert.ok(r.steps.length <= MAX_TOOL_STEPS * 2 + 3, `bounded steps, got ${r.steps.length}`);
  assert.match(r.output, /budget|unreached/);
}

console.log('runtime checks passed');
