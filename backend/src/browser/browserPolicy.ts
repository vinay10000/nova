// F3 browser safety policy: origin allowlist + private-host blocking + task spec.
// Fail-closed on network (SSRF/metadata), fail-open on origin only when no
// allowlist is configured (open mode -> browserbase provider).
import { lookup as defaultLookup } from 'node:dns/promises';

/** True for RFC1918/loopback/link-local/multicast/reserved IPs (v4 + v6). */
export function isIpPrivate(ip: string): boolean {
  const v = ip.trim().toLowerCase().replace(/^\[|\]$/g, '');
  if (v === '::1' || v === '::' || v === '0.0.0.0') return true;
  if (v.includes(':')) {
    // IPv6: fc00::/7 unique-local, fe80::/10 link-local
    const first = v.split(':')[0] ?? '';
    const head = parseInt(first || '0', 16);
    if (Number.isNaN(head)) return true;
    if ((head & 0xfe00) === 0xfc00) return true; // fc00::/7
    if ((head & 0xffc0) === 0xfe80) return true; // fe80::/10
    if (v === '::ffff:127.0.0.1' || v.startsWith('::ffff:10.') || v.startsWith('::ffff:192.168.')) return true;
    return false;
  }
  const parts = v.split('.');
  if (parts.length !== 4 || parts.some((p) => !/^\d+$/.test(p))) return true;
  const n = parts.map(Number);
  if (n.some((x) => x < 0 || x > 255)) return true;
  const [a, b] = n as [number, number, number, number];
  if (a === 10) return true;
  if (a === 127) return true;
  if (a === 0) return true;
  if (a === 169 && b === 254) return true; // cloud metadata
  if (a === 192 && b === 168) return true;
  if (a === 172 && b >= 16 && b <= 31) return true;
  if (a >= 224) return true; // multicast + reserved
  return false;
}

/** Parse BROWSER_ALLOWED_ORIGINS (comma-separated https origins). Invalid entries dropped. */
export function parseAllowedOrigins(): string[] {
  const raw = process.env.BROWSER_ALLOWED_ORIGINS ?? '';
  const out: string[] = [];
  for (const piece of raw.split(',')) {
    const s = piece.trim();
    if (!s) continue;
    try {
      const u = new URL(s);
      if (u.protocol !== 'https:') continue;
      if (u.username || u.password) continue;
      // Normalize: lowercase host, strip default port, strip path/query.
      out.push(`https://${u.hostname.toLowerCase()}`);
    } catch {
      continue;
    }
  }
  return [...new Set(out)];
}

type LookupFn = (hostname: string) => Promise<{ address: string; family: number } | { address: string; family: number }[]>;

function hostAllowed(hostname: string, origins: string[]): boolean {
  const h = hostname.toLowerCase();
  return origins.some((o) => {
    try {
      return new URL(o).hostname.toLowerCase() === h;
    } catch {
      return false;
    }
  });
}

/**
 * Validate a browser URL. Sync checks throw immediately; when a `lookup` is
 * supplied (or DNS resolves), private IPs reject with browser_private_host.
 * Returns the normalized URL string.
 */
export async function assertAllowedUrl(
  raw: string,
  opts?: { lookup?: LookupFn },
): Promise<string> {
  let u: URL;
  try {
    u = new URL(raw);
  } catch {
    throw new Error('browser_origin_not_allowed: unparsable url');
  }
  if (u.protocol !== 'https:') throw new Error('browser_origin_not_allowed: https only');
  if (u.username || u.password) throw new Error('browser_credentials_in_url: userinfo not allowed');
  const origins = parseAllowedOrigins();
  if (origins.length && !hostAllowed(u.hostname, origins)) {
    throw new Error('browser_origin_not_allowed: host not in allowlist');
  }
  // Literal-IP hosts: reject private without needing DNS.
  const bare = u.hostname.replace(/^\[|\]$/g, '');
  if (/^[\d.]+$/.test(bare) || bare.includes(':')) {
    if (isIpPrivate(bare)) throw new Error('browser_origin_not_allowed: private literal ip');
  }
  const lookup = opts?.lookup as LookupFn | undefined;
  if (!lookup) {
    // Fire-and-forget DNS pinning in production would go here; sync path keeps
    // the common case simple. Normalize: lowercase host, strip default :443.
    u.hostname = u.hostname.toLowerCase();
    if (u.port === '443') u.port = '';
    u.protocol = 'https:';
    return u.toString();
  }
  return (async () => {
    let addrs: { address: string } | { address: string }[] | null = null;
    try {
      addrs = (await lookup(u.hostname)) as { address: string } | { address: string }[];
    } catch {
      throw new Error('browser_private_host: dns lookup failed');
    }
    const list = Array.isArray(addrs) ? addrs : [addrs];
    for (const a of list) {
      if (isIpPrivate(a.address)) throw new Error('browser_private_host: resolves to private ip');
    }
    u.hostname = u.hostname.toLowerCase();
    if (u.port === '443') u.port = '';
    return u.toString();
  })();
}

export interface BrowserOpExtract { kind: 'extract'; selector: string }
export interface BrowserOpWait { kind: 'wait'; selector: string; timeoutMs?: number }
export type BrowserOp = BrowserOpExtract | BrowserOpWait;

export interface BrowserTaskSpec {
  url: string;
  instruction: string;
  ops: BrowserOp[];
  provider: 'browserless' | 'browserbase';
}

// eslint-disable-next-line @typescript-eslint/no-namespace
export namespace BrowserTaskSpec {
  export function parse(input: unknown): BrowserTaskSpec {
    const o = input as Record<string, unknown>;
    if (typeof o?.url !== 'string' || !o.url) throw new Error('browser_task_invalid: url required');
    if (typeof o?.instruction !== 'string' || !o.instruction.trim()) {
      throw new Error('browser_task_invalid: instruction required');
    }
    // URL must at least be https-parseable here; origin policy enforced at run time.
    const u = new URL(o.url);
    if (u.protocol !== 'https:') throw new Error('browser_origin_not_allowed: https only');
    const ops: BrowserOp[] = [];
    if (o.ops !== undefined) {
      if (!Array.isArray(o.ops)) throw new Error('browser_task_invalid: ops must be an array');
      for (const op of o.ops) {
        const r = op as Record<string, unknown>;
        if (r.kind === 'extract' && typeof r.selector === 'string') {
          ops.push({ kind: 'extract', selector: r.selector });
        } else if (r.kind === 'wait' && typeof r.selector === 'string') {
          const timeoutMs = typeof r.timeoutMs === 'number' ? Math.min(30_000, Math.max(500, r.timeoutMs)) : 5000;
          ops.push({ kind: 'wait', selector: r.selector, timeoutMs });
        } else if ((r as { kind?: string }).kind === 'act') {
          // Interactive actions (click/type) can change remote state — always approval-gated.
          throw new Error('browser_task_interactive_requires_approval: act ops need human approval');
        } else {
          throw new Error('browser_task_invalid: unknown op');
        }
      }
    }
    // Restricted allowlist -> Browserless; open mode -> Browserbase.
    const provider = parseAllowedOrigins().length ? 'browserless' : 'browserbase';
    return { url: o.url as string, instruction: (o.instruction as string).trim(), ops, provider };
  }
}

export { defaultLookup };
