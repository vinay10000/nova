import assert from 'node:assert/strict';
import { uiBlocksFromToolResult, validateUiBlocks } from './UiBlocks.js';

const table = uiBlocksFromToolResult('github_list_issues', JSON.stringify({ issues: [
  { title: 'Fix login', state: 'open', html_url: 'https://github.com/example/1' },
] }));
assert.equal(table[0]?.type, 'table');
assert.deepEqual((table[0] as { rows: string[][] }).rows[0], ['Fix login', 'open']);
assert.ok((table[0] as { actions: { type: string }[] }).actions.some((a) => a.type === 'copy'), 'tool cards get a copy action');

const metrics = uiBlocksFromToolResult('leetcode_get_solved', JSON.stringify({ total: 42, easy: 20, hard: 3 }));
assert.equal(metrics[0]?.type, 'metrics');
assert.equal((metrics[0] as { metrics: { value: string }[] }).metrics[0]?.value, '42');
const list = uiBlocksFromToolResult('browser_open', JSON.stringify({ text: 'Live page content' }));
assert.equal(list[0]?.type, 'summary');

assert.deepEqual(uiBlocksFromToolResult('github_create_issue', JSON.stringify({ id: 1 })), []);
assert.throws(() => validateUiBlocks([{ type: 'sparkle', id: 'bad' }]), /discriminator/);
assert.throws(() => validateUiBlocks([{ type: 'summary', id: 'x', body: 'x'.repeat(2_001) }]), /too_big/);

// Repeated tool calls in one turn must not collide on id (client de-dupes by id).
{
  const a = uiBlocksFromToolResult('github_list_issues', JSON.stringify({ issues: [{ title: 'A', state: 'open' }] }));
  const b = uiBlocksFromToolResult('github_list_issues', JSON.stringify({ issues: [{ title: 'B', state: 'open' }] }));
  assert.notEqual(a[0]?.id, b[0]?.id, 'same-tool blocks get distinct ids');
}

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

  // Server-assigned id wins even if the model supplies one (collision → dropped card).
  const claimed = blocksFromPresentUiInput({ blocks: [{ type: 'summary', id: 'shared', body: 'one' }, { type: 'summary', id: 'shared', body: 'two' }] });
  assert.ok(claimed && claimed[0].id !== claimed[1].id, 'model ids cannot collide');

  // Empty metric values are rejected by the schema.
  assert.equal(
    blocksFromPresentUiInput({ blocks: [{ type: 'metrics', metrics: [{ label: 'x', value: '  ' }] }] }),
    null,
    'blank metric value rejected',
  );

  // Ragged table rows are padded/truncated to the header width — never rendered ragged.
  const ragged = blocksFromPresentUiInput({ blocks: [{ type: 'table', columns: ['a', 'b', 'c'], rows: [['1'], ['1', '2', '3', '4']] }] });
  assert.ok(ragged?.length === 1);
  const t = ragged![0] as { rows: string[][]; columns: string[] };
  assert.deepEqual(t.rows[0], ['1', '—', '—'], 'short row padded');
  assert.equal(t.rows[1].length, t.columns.length, 'long row truncated to header');

  // New block types parse and carry the default copy action.
  const mixed = blocksFromPresentUiInput({ blocks: [
    { type: 'progress', items: [{ label: 'Coverage', value: 82 }] },
    { type: 'timeline', steps: [{ label: 'Build', status: 'done' }, { label: 'Deploy', status: 'active' }] },
    { type: 'comparison', leftLabel: 'A', rightLabel: 'B', rows: [{ label: 'Speed', left: 'Fast', right: 'Slow' }], winner: 'left' },
  ] });
  assert.equal(mixed?.length, 3);
  assert.equal(mixed?.[0]?.type, 'progress');
  assert.equal(mixed?.[1]?.type, 'timeline');
  assert.equal(mixed?.[2]?.type, 'comparison');
  for (const b of mixed ?? []) {
    assert.ok((b as { actions: { type: string }[] }).actions.some((a) => a.type === 'copy'), `${b.type} gets copy action`);
  }

  const more = blocksFromPresentUiInput({ blocks: [
    { type: 'code', code: 'const x = 1;', language: 'ts' },
    { type: 'chart', points: [{ label: 'Mon', value: 3 }, { label: 'Tue', value: -1 }], unit: 'ms' },
    { type: 'links', links: [{ title: 'Docs', url: 'https://example.com/docs' }] },
  ] });
  assert.equal(more?.length, 3);
  assert.equal(more?.[0]?.type, 'code');
  assert.equal(more?.[1]?.type, 'chart');
  assert.equal(more?.[2]?.type, 'links');

  // Non-http URLs are rejected — the client opens links with an implicit intent.
  assert.equal(
    blocksFromPresentUiInput({ blocks: [{ type: 'links', links: [{ title: 'x', url: 'javascript:alert(1)' }] }] }),
    null,
    'non-http link rejected',
  );

  // Progress out of range rejected; timeline bad status rejected.
  assert.equal(blocksFromPresentUiInput({ blocks: [{ type: 'progress', items: [{ label: 'x', value: 120 }] }] }), null);
  assert.equal(blocksFromPresentUiInput({ blocks: [{ type: 'timeline', steps: [{ label: 'x', status: 'mystery' }] }] }), null);
}

// browser_scrape presentation: selector map → list blocks.
{
  const blocks = uiBlocksFromToolResult('browser_scrape', JSON.stringify({ url: 'https://example.com', data: { h1: ['Title'], '.price': ['$10', '$20'] } }));
  assert.equal(blocks.length, 2);
  assert.equal(blocks[0]?.type, 'list');
  assert.deepEqual(uiBlocksFromToolResult('browser_scrape', JSON.stringify({ data: {} })), []);
}
console.log('generative UI checks passed');
