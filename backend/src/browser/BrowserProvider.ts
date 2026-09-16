// F3 browser abstraction. Browserless (free tier) is the default cloud browser;
// Playwright stays as the local/self-host fallback. Every run passes through
// browserPolicy (origin allowlist + SSRF guards) before any fetch.
import { assertAllowedUrl, BrowserTaskSpec } from './browserPolicy.js';

export interface BrowserTask { url: string; instruction: string; }
export interface BrowserProvider { id: string; run(task: BrowserTask): Promise<{ text: string }>; }

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
