import assert from 'node:assert/strict';
import { uiBlocksFromToolResult, validateUiBlocks } from './UiBlocks.js';

const table = uiBlocksFromToolResult('github_list_issues', JSON.stringify({ issues: [
  { title: 'Fix login', state: 'open', html_url: 'https://github.com/example/1' },
] }));
assert.equal(table[0]?.type, 'table');
assert.deepEqual((table[0] as { rows: string[][] }).rows[0], ['Fix login', 'open']);

const metrics = uiBlocksFromToolResult('leetcode_get_solved', JSON.stringify({ total: 42, easy: 20, hard: 3 }));
assert.equal(metrics[0]?.type, 'metrics');
assert.equal((metrics[0] as { metrics: { value: string }[] }).metrics[0]?.value, '42');
const list = uiBlocksFromToolResult('browser_open', JSON.stringify({ text: 'Live page content' }));
assert.equal(list[0]?.type, 'summary');

assert.deepEqual(uiBlocksFromToolResult('github_create_issue', JSON.stringify({ id: 1 })), []);
assert.throws(() => validateUiBlocks([{ type: 'chart', id: 'bad' }]), /discriminator/);
assert.throws(() => validateUiBlocks([{ type: 'summary', id: 'x', body: 'x'.repeat(2_001) }]), /too_big/);

// present_ui: model params → validated blocks; garbage rejected, never rendered.
{
  const { blocksFromPresentUiInput } = await import('./UiBlocks.js');
  const ok = blocksFromPresentUiInput({ blocks: [
    { type: 'metrics', title: 'Stats', metrics: [{ label: 'solved', value: '42' }] },
    { type: 'list', items: [{ label: 'a' }, { label: 'b' }] },
  ] });
  assert.equal(ok?.length, 2);
  assert.equal(ok?.[0]?.type, 'metrics');
  assert.ok(ok?.[0]?.id, 'server assigns ids');
  assert.equal(blocksFromPresentUiInput({ blocks: [{ type: 'chart' }] }), null, 'unknown type rejected');
  assert.equal(blocksFromPresentUiInput({ blocks: [] }), null);
  assert.equal(blocksFromPresentUiInput({}), null);
  assert.equal(blocksFromPresentUiInput(null), null);
  const overCap = blocksFromPresentUiInput({ blocks: Array.from({ length: 5 }, () => ({ type: 'summary', body: 'x' })) });
  assert.ok(overCap && overCap.length <= 3, 'capped at 3 blocks');
}

// browser_scrape presentation: selector map → list blocks.
{
  const blocks = uiBlocksFromToolResult('browser_scrape', JSON.stringify({ url: 'https://example.com', data: { h1: ['Title'], '.price': ['$10', '$20'] } }));
  assert.equal(blocks.length, 2);
  assert.equal(blocks[0]?.type, 'list');
  assert.deepEqual(uiBlocksFromToolResult('browser_scrape', JSON.stringify({ data: {} })), []);
}
console.log('generative UI checks passed');
