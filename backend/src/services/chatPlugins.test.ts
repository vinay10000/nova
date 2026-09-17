// Runnable check for §42 chat plugins: detection, connection gating, and the
// failure→reconnect mapping that made "@github" fail for connected users.
//   npx tsx src/services/chatPlugins.test.ts
import assert from 'node:assert/strict';
import {
  chatPlugins,
  detectPlugin,
  isPluginUsable,
  pluginCatalog,
  pluginErrorResult,
  pluginToolDefs,
} from './chatPlugins.js';
import { toolRegistry, isServerKeyedTool } from '../tools/registry.js';

// 1. Every plugin tool id must exist in the registry — a typo here is a silently
//    dead @mention, which is exactly how the plugins looked broken.
for (const plugin of chatPlugins) {
  for (const id of plugin.toolIds) {
    assert.ok(toolRegistry.has(id), `plugin ${plugin.id} names unknown tool ${id}`);
  }
  assert.ok(pluginToolDefs(plugin).length === plugin.toolIds.length, `${plugin.id} lost tool defs`);
}

// 2. An explicit @mention always routes, and only the leading mention is stripped.
{
  const r = detectPlugin('@github list my repositories');
  assert.equal(r?.plugin.id, 'github');
  assert.equal(r?.cleanedMessage, 'list my repositories');
  const mid = detectPlugin('why is my @gmail so noisy');
  assert.equal(mid?.plugin.id, 'gmail');
  assert.equal(mid?.cleanedMessage, 'why is my @gmail so noisy');
}

// 3. Keyword routing is gated on a live connection: no connection, no silent swap.
{
  const none = new Set<string>();
  assert.equal(detectPlugin('show me my github issues', none), null);
  assert.equal(detectPlugin('show me my github issues', new Set(['github']))?.plugin.id, 'github');
  // LeetCode and web search run on backend keys, so they are always usable.
  assert.equal(detectPlugin('what is the leetcode daily challenge', none)?.plugin.id, 'leetcode');
  assert.equal(detectPlugin('@browser research this page', none)?.plugin.id, 'browser');
  assert.equal(detectPlugin('open browser and research the latest trend', none)?.plugin.id, 'browser');
}

// 4. Readiness drives the picker, and LeetCode never needs a connection.
{
  const catalog = pluginCatalog(new Set(['github']));
  const byId = new Map(catalog.map((p) => [p.id, p]));
  assert.equal(byId.get('github')?.ready, true);
  assert.equal(byId.get('gmail')?.ready, false);
  assert.equal(byId.get('leetcode')?.ready, true);
  assert.equal(byId.get('calendar')?.ready, false);
  assert.equal(isPluginUsable(chatPlugins.find((p) => p.id === 'leetcode')!, new Set()), true);
}

// 5. A revoked token must map to a reconnect instruction, not a generic error —
//    this is the bug that made "connected" accounts look disconnected.
{
  const revoked = pluginErrorResult('github_auth_invalid: the GitHub authorization is no longer valid.', 'github');
  assert.equal(revoked.reconnect, 'github');
  assert.match(revoked.result, /reconnect_required/);
  const scope = pluginErrorResult('gmail_scope_missing: ...', 'gmail');
  assert.equal(scope.reconnect, 'gmail');
  const flaky = pluginErrorResult('leetcode_rate_limited', 'leetcode');
  assert.equal(flaky.retryable, true);
  assert.equal(flaky.reconnect, undefined);
}

// 6. Server-keyed tools must never demand a Connection row.
assert.equal(isServerKeyedTool('leetcode_get_solved'), true);
assert.equal(isServerKeyedTool('web_search'), true);
assert.equal(isServerKeyedTool('browser_open'), true);
assert.equal(isServerKeyedTool('github_list_issues'), false);
assert.equal(isServerKeyedTool('calendar_list_events'), false);

console.log('chatPlugins: all checks passed');
