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
console.log('generative UI checks passed');
