// §18/§27 LeetCode — no official public API exists, so this adapter speaks to a
// documented open-source wrapper (github.com/alfaarghya/alfa-leetcode-api) that
// serves public profile/problem data over REST. No auth, no scraping, no bypass
// of LeetCode's own controls. Server-keyed (§40): information retrieval needs no
// per-user OAuth. If LEETCODE_API_BASE is unreachable the tools report the
// capability as unavailable rather than fabricating data (§19).

const DEFAULT_BASE = 'https://alfa-leetcode-api.onrender.com';

export function leetcodeBase(): string {
  return (process.env.LEETCODE_API_BASE ?? DEFAULT_BASE).replace(/\/+$/, '');
}

// ponytail: in-process TTL cache only. Upstream rate-limits and the same public
// profile is asked for repeatedly in a chat; a Map with a TTL removes that load
// without a dependency. Upgrade path: swap for Redis if this ever runs multi-instance
// with meaningful traffic.
const CACHE_TTL_MS = 5 * 60 * 1000;
const CACHE_MAX = 200;
const cache = new Map<string, { at: number; data: unknown }>();

async function leetcodeGet<T>(path: string): Promise<T> {
  const url = `${leetcodeBase()}${path}`;
  const hit = cache.get(url);
  if (hit && Date.now() - hit.at < CACHE_TTL_MS) return hit.data as T;

  let res: Response;
  try {
    res = await fetch(url, {
      headers: { Accept: 'application/json' },
      signal: AbortSignal.timeout(15_000),
    });
  } catch {
    throw new Error('leetcode_unavailable');
  }
  if (res.status === 429) throw new Error('leetcode_rate_limited');
  if (!res.ok) throw new Error(`leetcode_upstream_${res.status}`);

  const data = (await res.json()) as T;
  if (cache.size >= CACHE_MAX) {
    const oldest = cache.keys().next().value;
    if (oldest) cache.delete(oldest);
  }
  cache.set(url, { at: Date.now(), data });
  return data;
}

/** LeetCode nicknames are [A-Za-z0-9_-]; reject anything else before it hits the URL. */
export function assertUsername(username: unknown): string {
  if (typeof username !== 'string' || !/^[A-Za-z0-9_-]{1,40}$/.test(username.trim())) {
    throw new Error('invalid_input: username is required (letters, digits, _ or -)');
  }
  return username.trim();
}

export interface LeetCodeProfile {
  username?: string;
  name?: string;
  ranking?: number;
  reputation?: number;
  country?: string;
  company?: string | null;
  school?: string | null;
  skillTags?: string[];
  about?: string;
  gitHub?: string;
  website?: string[];
}

export async function getProfile(username: string): Promise<LeetCodeProfile> {
  return leetcodeGet<LeetCodeProfile>(`/${encodeURIComponent(username)}`);
}

export async function getSolved(username: string): Promise<unknown> {
  return leetcodeGet(`/${encodeURIComponent(username)}/solved`);
}

export async function getContestHistory(username: string): Promise<unknown> {
  return leetcodeGet(`/${encodeURIComponent(username)}/contest`);
}

export async function getSubmissionCalendar(username: string): Promise<unknown> {
  return leetcodeGet(`/${encodeURIComponent(username)}/calendar`);
}

export async function getRecentSubmissions(username: string, limit = 10): Promise<unknown> {
  const take = Math.min(20, Math.max(1, limit));
  return leetcodeGet(`/${encodeURIComponent(username)}/submission?limit=${take}`);
}

export async function getDailyChallenge(): Promise<unknown> {
  return leetcodeGet('/daily');
}

export async function getProblem(titleSlug: string): Promise<unknown> {
  if (typeof titleSlug !== 'string' || !/^[a-z0-9-]{2,120}$/i.test(titleSlug)) {
    throw new Error('invalid_input: titleSlug is required (e.g. two-sum)');
  }
  return leetcodeGet(`/select?titleSlug=${encodeURIComponent(titleSlug)}`);
}

export async function searchProblems(opts: { tags?: string; difficulty?: string; limit?: number; skip?: number }): Promise<unknown> {
  const params = new URLSearchParams();
  const limit = Math.min(50, Math.max(1, opts.limit ?? 20));
  params.set('limit', String(limit));
  if (opts.skip) params.set('skip', String(Math.max(0, opts.skip)));
  if (opts.tags && /^[A-Za-z0-9+ -]{1,120}$/.test(opts.tags)) params.set('tags', opts.tags.replace(/ /g, '+'));
  const difficulty = (opts.difficulty ?? '').toUpperCase();
  if (['EASY', 'MEDIUM', 'HARD'].includes(difficulty)) params.set('difficulty', difficulty);
  return leetcodeGet(`/problems?${params}`);
}