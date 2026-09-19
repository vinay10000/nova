// New browser/workspace tool self-checks: input validation, SSRF rejection,
// sandbox ownership registry. No live Browserless/E2B keys needed — validation and
// policy throw before any network call. Run: npx tsx src/browser/browserTools.test.ts
import assert from 'node:assert/strict';
import { toolRegistry } from '../tools/registry.js';
import { registerSandbox, unregisterSandbox, cleanupSandboxes } from '../sandbox/CodeSandboxProvider.js';

const prevOrigins = process.env.BROWSER_ALLOWED_ORIGINS;
process.env.BROWSER_ALLOWED_ORIGINS = 'https://example.com';

const ctx = { userId: 'u1', agentId: 'a1', permissions: ['browser.read', 'workspace.create', 'workspace.read'], db: {} as never };

// 1. browser_scrape exists, is read-only, rejects bad selectors before any network call.
{
  const t = toolRegistry.get('browser_scrape');
  assert.ok(t, 'browser_scrape registered');
  assert.equal(t.isWrite, false);
  await assert.rejects(() => t.execute({ url: 'https://example.com/', selectors: [] }, ctx), /1-10 selectors/);
  await assert.rejects(() => t.execute({ url: 'https://example.com/', selectors: ['body { color: red }'] }, ctx), /selector/);
  await assert.rejects(() => t.execute({ url: 'https://example.com/', selectors: ['x'.repeat(300)] }, ctx), /selector/);
}

// 2. SSRF guard fires before the upstream call (no key needed: policy throws first).
{
  const t = toolRegistry.get('browser_scrape')!;
  await assert.rejects(() => t.execute({ url: 'https://evil.com/', selectors: ['h1'] }, ctx), /browser_origin_not_allowed/);
  await assert.rejects(() => t.execute({ url: 'https://127.0.0.1/', selectors: ['h1'] }, ctx), /browser_origin_not_allowed/);
  const shot = toolRegistry.get('browser_screenshot')!;
  await assert.rejects(() => shot.execute({ url: 'http://example.com/' }, ctx), /browser_origin_not_allowed/);
  const pdf = toolRegistry.get('browser_pdf')!;
  await assert.rejects(() => pdf.execute({ url: 'notaurl' }, ctx), /browser_origin_not_allowed/);
}

// 3. browser_task: write-class, approval-gated, validates urls/instruction.
{
  const t = toolRegistry.get('browser_task');
  assert.ok(t, 'browser_task registered');
  assert.equal(t.approval, 'always');
  assert.equal(t.isWrite, true);
  await assert.rejects(() => t.execute({ urls: [], instruction: 'x' }, ctx), /1-5 urls/);
  await assert.rejects(() => t.execute({ urls: ['https://example.com/'], instruction: '' }, ctx), /instruction/);
}

// 4. workspace_list_files registered read-only; sandboxId validation throws first.
{
  const t = toolRegistry.get('workspace_list_files');
  assert.ok(t, 'workspace_list_files registered');
  assert.equal(t.isWrite, false);
  process.env.E2B_API_KEY = 'fake-key-for-validation-path';
  await assert.rejects(() => t.execute({ sandboxId: '' }, ctx), /sandboxId/);
  delete process.env.E2B_API_KEY;
}

// 5. Sandbox ownership registry: register/unregister/cleanup never throw without keys.
{
  registerSandbox('a9', 'sbx_abcdefgh');
  registerSandbox('a9', 'bad id!'); // rejected by id guard
  unregisterSandbox('a9', 'sbx_abcdefgh');
  assert.deepEqual(await cleanupSandboxes('a9'), [], 'unregistered sandbox not cleaned');
  registerSandbox('a9', 'sbx_12345678');
  const killed = await cleanupSandboxes('a9'); // kill fails quietly (fake id, no key handling)
  assert.deepEqual(killed, ['sbx_12345678']);
  assert.deepEqual(await cleanupSandboxes('a9'), [], 'second cleanup is a no-op');
}

if (prevOrigins === undefined) delete process.env.BROWSER_ALLOWED_ORIGINS;
else process.env.BROWSER_ALLOWED_ORIGINS = prevOrigins;

console.log('browserTools: all checks passed');
