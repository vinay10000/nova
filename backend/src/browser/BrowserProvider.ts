// F3 browser abstraction. Browserless (free tier) is the default cloud browser;
// Playwright stays as the local/self-host fallback. Every run passes through
// browserPolicy (origin allowlist + SSRF guards) before any fetch.
import { assertAllowedUrl, BrowserTaskSpec } from './browserPolicy.js';

export interface BrowserTask { url: string; instruction: string; }
export interface BrowserProvider { id: string; run(task: BrowserTask): Promise<{ text: string }>; }

export interface ScrapeElement { selector: string }
export interface ScrapeResult { url: string; data: Record<string, string[]> }

/** Shared Browserless REST call with hard timeout. Endpoints return text or JSON. */
async function browserlessCall(endpoint: 'content' | 'scrape' | 'screenshot' | 'pdf', body: Record<string, unknown>, timeoutMs = 30_000): Promise<Response> {
  const key = process.env.BROWSERLESS_API_KEY;
  if (!key) throw new Error('browser_unconfigured: set BROWSERLESS_API_KEY server-side');
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(`https://chrome.browserless.io/${endpoint}?token=${encodeURIComponent(key)}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: controller.signal,
    });
    if (!res.ok) throw new Error(`browser_upstream_error: ${res.status}`);
    return res;
  } finally {
    clearTimeout(timer);
  }
}

/** CSS selector sanity bound — keeps prompt-injected selectors from running arbitrary page JS. */
function assertSelector(s: unknown): string {
  if (typeof s !== 'string' || !s.trim() || s.length > 200 || /[{};]|javascript:/i.test(s)) {
    throw new Error('invalid_input: selector must be a short CSS selector');
  }
  return s.trim();
}

/** Browserless /scrape: structured extraction of element text by CSS selectors. */
export async function scrape(url: string, selectors: string[]): Promise<ScrapeResult> {
  const safeUrl = await assertAllowedUrl(url);
  if (!selectors.length || selectors.length > 10) throw new Error('invalid_input: 1-10 selectors required');
  const elements: ScrapeElement[] = selectors.map((s) => ({ selector: assertSelector(s) }));
  const res = await browserlessCall('scrape', { url: safeUrl, elements });
  const json = (await res.json()) as { data?: { selector: string; results?: { text?: string }[] }[] };
  const data: Record<string, string[]> = {};
  for (const block of json.data ?? []) {
    data[block.selector] = (block.results ?? []).map((r) => (r.text ?? '').trim()).filter(Boolean).slice(0, 50);
  }
  return { url: safeUrl, data };
}

/** Browserless /screenshot: base64 PNG of a rendered page (allowlist-guarded). */
export async function screenshot(url: string): Promise<{ base64: string; bytes: number }> {
  const safeUrl = await assertAllowedUrl(url);
  const res = await browserlessCall('screenshot', { url: safeUrl, options: { type: 'png', fullPage: false } });
  const buf = Buffer.from(await res.arrayBuffer());
  if (buf.length > 5 * 1024 * 1024) throw new Error('browser_upstream_error: screenshot too large');
  return { base64: buf.toString('base64'), bytes: buf.length };
}

/** Browserless /pdf: base64 PDF of a rendered page (allowlist-guarded). */
export async function pagePdf(url: string): Promise<{ base64: string; bytes: number }> {
  const safeUrl = await assertAllowedUrl(url);
  const res = await browserlessCall('pdf', { url: safeUrl });
  const buf = Buffer.from(await res.arrayBuffer());
  if (buf.length > 10 * 1024 * 1024) throw new Error('browser_upstream_error: pdf too large');
  return { base64: buf.toString('base64'), bytes: buf.length };
}

/**
 * High-level agent browser task (F6): fetch pages via Browserless, optionally run a
 * sandboxed transform script over the extracted text via E2B, and always clean up the
 * sandbox — orphaned sandboxes burn E2B quota. The script reads concatenated page text
 * from pages.txt in the sandbox home dir and prints the result to stdout. Executes
 * model-written code, so the owning tool must be approval-gated like workspace_run_command.
 */
export async function runBrowserTask(opts: { urls: string[]; instruction: string; script?: string }): Promise<{ pages: { url: string; text: string }[]; scriptOutput?: string }> {
  if (!Array.isArray(opts.urls) || !opts.urls.length || opts.urls.length > 5) throw new Error('invalid_input: 1-5 urls required');
  const browser = defaultBrowser();
  const pages: { url: string; text: string }[] = [];
  for (const u of opts.urls) {
    const { text } = await browser.run({ url: String(u), instruction: opts.instruction });
    pages.push({ url: String(u), text });
  }
  if (!opts.script) return { pages };
  if (typeof opts.script !== 'string' || !opts.script.trim() || opts.script.length > 4000) {
    throw new Error('invalid_input: script must be 1-4000 chars');
  }
  const { defaultSandbox } = await import('../sandbox/CodeSandboxProvider.js');
  const sbx = defaultSandbox();
  const { sandboxId } = await sbx.create();
  try {
    await sbx.writeFile(sandboxId, 'pages.txt', pages.map((p) => `## ${p.url}\n${p.text}`).join('\n\n'));
    await sbx.writeFile(sandboxId, 'task.py', opts.script);
    const out = await sbx.run(sandboxId, 'python3 /home/user/task.py', 60_000);
    return { pages, scriptOutput: (out.stdout || out.stderr || '(no output)').slice(0, 4000) };
  } finally {
    await sbx.destroy(sandboxId).catch(() => {});
  }
}

function stripHtml(html: string): string {
  return html.replace(/<script[\s\S]*?<\/script>/gi, ' ').replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim().slice(0, 8000);
}

export class BrowserlessProvider implements BrowserProvider {
  id = 'browserless';
  async run(t: BrowserTask): Promise<{ text: string }> {
    const key = process.env.BROWSERLESS_API_KEY;
    if (!key) throw new Error('browser_unconfigured: set BROWSERLESS_API_KEY server-side');
    const spec = BrowserTaskSpec.parse({ url: t.url, instruction: t.instruction });
    const url = await assertAllowedUrl(spec.url);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 30_000);
    try {
      const res = await fetch(`https://chrome.browserless.io/content?token=${encodeURIComponent(key)}`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ url }),
        signal: controller.signal,
      });
      if (!res.ok) throw new Error(`browser_upstream_error: ${res.status}`);
      const html = await res.text();
      return { text: stripHtml(html) || '(empty page)' };
    } finally {
      clearTimeout(timer);
    }
  }
}

export class BrowserbaseProvider implements BrowserProvider {
  id = 'browserbase';
  async run(t: BrowserTask): Promise<{ text: string }> {
    // Open-mode fallback when no Browserless key is set: direct fetch with the
    // same policy guards (no JS rendering). Honest degradation, never fake data.
    const spec = BrowserTaskSpec.parse({ url: t.url, instruction: t.instruction });
    const url = await assertAllowedUrl(spec.url);
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), 20_000);
    try {
      const res = await fetch(url, { headers: { 'User-Agent': 'Nova-AI-Client' }, signal: controller.signal });
      if (!res.ok) throw new Error(`browser_upstream_error: ${res.status}`);
      const ct = res.headers.get('content-type') ?? '';
      const body = await res.text();
      return { text: ct.includes('html') ? stripHtml(body) || '(empty page)' : body.slice(0, 8000) };
    } finally {
      clearTimeout(timer);
    }
  }
}

export class PlaywrightProvider implements BrowserProvider {
  id = 'playwright';
  async run(t: BrowserTask): Promise<{ text: string }> {
    // Self-host path: same policy, direct fetch until a Playwright service lands.
    return new BrowserbaseProvider().run(t);
  }
}

/** Default: Browserless when configured, honest direct-fetch fallback otherwise. */
export function defaultBrowser(): BrowserProvider {
  return process.env.BROWSERLESS_API_KEY ? new BrowserlessProvider() : new BrowserbaseProvider();
}
