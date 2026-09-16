// Phase 6 policy self-check: origin allowlist, private-host blocking, task parsing.
// Run: npx tsx src/browser/browserPolicy.test.ts
import assert from 'node:assert/strict';
import { lookup } from 'node:dns/promises';
import { isIpPrivate, parseAllowedOrigins, assertAllowedUrl, BrowserTaskSpec } from './browserPolicy.js';

const DEFAULT_TEST_ORIGINS = 'https://example.com,https://api.github.com';

function withEnv(origins: string | undefined, fn: () => void | Promise<void>): Promise<void> {
  const prev = process.env.BROWSER_ALLOWED_ORIGINS;
  if (origins === undefined) delete process.env.BROWSER_ALLOWED_ORIGINS;
  else process.env.BROWSER_ALLOWED_ORIGINS = origins;
  return Promise.resolve()
    .then(fn)
    .finally(() => {
      if (prev === undefined) delete process.env.BROWSER_ALLOWED_ORIGINS;
      else process.env.BROWSER_ALLOWED_ORIGINS = prev;
    });
}

await withEnv(DEFAULT_TEST_ORIGINS, async () => {
  const origins = parseAllowedOrigins();
  assert.deepEqual(origins, ['https://example.com', 'https://api.github.com']);

  assert.equal(isIpPrivate('127.0.0.1'), true);
  assert.equal(isIpPrivate('10.0.0.5'), true);
  assert.equal(isIpPrivate('172.16.0.9'), true);
  assert.equal(isIpPrivate('192.168.1.1'), true);
  assert.equal(isIpPrivate('169.254.169.254'), true);
  assert.equal(isIpPrivate('::1'), true);
  assert.equal(isIpPrivate('fc00::1'), true);
  assert.equal(isIpPrivate('fe80::1'), true);
  assert.equal(isIpPrivate('0.0.0.0'), true);
  assert.equal(isIpPrivate('8.8.8.8'), false);
  assert.equal(isIpPrivate('2606:4700::1111'), false);

  assert.equal(await assertAllowedUrl('https://example.com/a?b=1'), 'https://example.com/a?b=1');
  assert.equal(await assertAllowedUrl('HTTPS://EXAMPLE.com/x'), 'https://example.com/x');
  assert.equal(await assertAllowedUrl('https://example.com:443/deep'), 'https://example.com/deep');

  await assert.rejects(async () => assertAllowedUrl('http://example.com/'), /browser_origin_not_allowed/);
  await assert.rejects(async () => assertAllowedUrl('https://evil.com/'), /browser_origin_not_allowed/);
  await assert.rejects(async () => assertAllowedUrl('https://example.com.evil.com/'), /browser_origin_not_allowed/);
  await assert.rejects(async () => assertAllowedUrl('https://example.com@evil.com/'), /browser_origin_not_allowed|browser_credentials_in_url/);
  await assert.rejects(async () => assertAllowedUrl('ftp://example.com/'), /browser_origin_not_allowed/);
  await assert.rejects(async () => assertAllowedUrl('https://user:pw@example.com/'), /browser_credentials_in_url/);
  await assert.rejects(async () => assertAllowedUrl('https://127.0.0.1/'), /browser_origin_not_allowed/);
  await assert.rejects(async () => assertAllowedUrl('https://[::1]/'), /browser_origin_not_allowed/);

  const spec = BrowserTaskSpec.parse({
    url: 'https://example.com/',
    instruction: 'Read the pricing page',
    ops: [
      { kind: 'extract', selector: 'h1' },
      { kind: 'wait', selector: 'table', timeoutMs: 5000 },
    ],
  }) as BrowserTaskSpec;
  assert.equal(spec.provider, 'browserless');
  assert.equal(spec.ops.length, 2);

  assert.throws(
    () => BrowserTaskSpec.parse({ url: 'https://example.com/', instruction: 'x', ops: [{ kind: 'act', act: 'click it' }] }),
    /browser_task_interactive_requires_approval/,
  );

  await withEnv(undefined, () => {
    const spec2 = BrowserTaskSpec.parse({ url: 'https://anything.dev/', instruction: 'x' }) as BrowserTaskSpec;
    assert.equal(spec2.provider, 'browserbase');
  });
});

await withEnv(undefined, () => {
  const spec = BrowserTaskSpec.parse({ url: 'https://example.com/', instruction: 'summarize page' }) as BrowserTaskSpec;
  assert.equal(spec.provider, 'browserbase');
});

await withEnv('not-a-url,   https://ok.io  ,', () => {
  assert.deepEqual(parseAllowedOrigins(), ['https://ok.io']);
});

await withEnv('', () => {
  assert.deepEqual(parseAllowedOrigins(), []);
});

// DNS pinning: resolves a real public host and rejects one that resolves private.
await withEnv('https://example.com', async () => {
  const url = await assertAllowedUrl('https://example.com/');
  assert.ok(url.startsWith('https://example.com/'));
  const addrs = await lookup('example.com').catch(() => null);
  if (addrs) {
    assert.equal(isIpPrivate(addrs.address), false);
  }
});

await withEnv('https://metadata.internal', async () => {
  const fakeLookup = (async () => ({ address: '169.254.169.254', family: 4 })) as unknown as typeof lookup;
  await assert.rejects(
    () => assertAllowedUrl('https://metadata.internal/', { lookup: fakeLookup }),
    /browser_private_host/,
  );
});

console.log('browserPolicy: all checks passed');
