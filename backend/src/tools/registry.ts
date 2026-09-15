import type { Tool, ToolContext } from './Tool.js';
import { getGitHubToken, markGitHubConnectionExpired } from '../integrations/github.js';
import { getGmailAccessToken, markGmailConnectionExpired, GMAIL_API } from '../integrations/gmail.js';
import { getCalendarAccessToken, markCalendarConnectionExpired, CALENDAR_API } from '../integrations/googleCalendar.js';
import {
  assertUsername,
  getContestHistory,
  getDailyChallenge,
  getProblem,
  getProfile,
  getRecentSubmissions,
  getSolved,
  searchProblems,
} from '../integrations/leetcode.js';

const GITHUB_API = 'https://api.github.com';

/**
 * §17: Shared GitHub fetch helper. Decrypts the user's OAuth token on each call.
 * §46: token is never stored in logs or step metadata.
 */
async function githubFetch(ctx: ToolContext, path: string, init?: RequestInit): Promise<unknown> {
  const token = await getGitHubToken(ctx.db, ctx.userId);
  if (!token) throw new Error('github_not_connected');
  const res = await fetch(`${GITHUB_API}${path}`, {
    ...init,
    headers: {
      Authorization: `Bearer ${token}`,
      Accept: 'application/vnd.github+json',
      'User-Agent': 'Nova-AI-Client',
      ...init?.headers,
    },
  });
  if (res.status === 401 || res.status === 403) {
    // The token GitHub handed us is no longer accepted (revoked, reset, or the
    // app was uninstalled). Flag the connection so the UI stops saying
    // "Connected" and offers a reconnect — the error text is what the model
    // relays to the user, so it must name the fix.
    const body = await res.text().catch(() => '');
    if (res.status === 401 || /bad credentials|token.*expired|revoked/i.test(body)) {
      await markGitHubConnectionExpired(ctx.db, ctx.userId);
      throw new Error('github_auth_invalid: the GitHub authorization is no longer valid. Ask the user to reconnect GitHub in Connections, then retry.');
    }
    throw new Error(`GitHub API 403: ${body.slice(0, 200)}`);
  }
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`GitHub API ${res.status}: ${body.slice(0, 200)}`);
  }
  return res.json();
}

// Level 1 native first: github, gmail, calendar (§27). Others via IntegrationAdapter or browser.

// §17 read/write are separate scopes and separate tools. Connecting GitHub grants read only.
const githubListIssues: Tool = {
  id: 'github_list_issues',
  description: 'List issues assigned to the authenticated user (§17)',
  inputSchema: { type: 'object', properties: { state: { type: 'string', enum: ['open', 'closed', 'all'] }, repo: { type: 'string' } } },
  scope: 'github.issues.read',
  isWrite: false,
  approval: 'never',
  execute: async (input, ctx) => {
    const { state = 'open', repo } = (input as Record<string, unknown>) ?? {};
    if (repo) {
      // List issues for a specific repo
      const params = new URLSearchParams({ state: String(state), per_page: '20', sort: 'updated' });
      return githubFetch(ctx, `/repos/${repo}/issues?${params}`);
    }
    // List issues assigned to the user across all repos
    const params = new URLSearchParams({ state: String(state), per_page: '20', filter: 'assigned' });
    return githubFetch(ctx, `/user/issues?${params}`);
  },
};

const githubCreateIssue: Tool = {
  id: 'github_create_issue',
  description: 'Create an issue in a repository (§17)',
  inputSchema: {
    type: 'object',
    properties: { repo: { type: 'string' }, title: { type: 'string' }, body: { type: 'string' }, labels: { type: 'array', items: { type: 'string' } } },
    required: ['repo', 'title'],
  },
  scope: 'github.issues.write',
  isWrite: true,
  approval: 'always', // §36/§47 every write passes a human checkpoint
  execute: async (input, ctx) => {
    const { repo, title, body, labels } = input as Record<string, unknown>;
    return githubFetch(ctx, `/repos/${repo}/issues`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ title, body: body ?? '', labels: labels ?? [] }),
    });
  },
};

const githubListPullRequests: Tool = {
  id: 'github_list_pull_requests',
  description: 'List pull requests in a repository (§17)',
  inputSchema: {
    type: 'object',
    properties: { repo: { type: 'string' }, state: { type: 'string', enum: ['open', 'closed', 'all'] } },
    required: ['repo'],
  },
  scope: 'github.prs.read',
  isWrite: false,
  approval: 'never',
  execute: async (input, ctx) => {
    const { repo, state = 'open' } = input as Record<string, unknown>;
    const params = new URLSearchParams({ state: String(state), per_page: '20', sort: 'updated' });
    return githubFetch(ctx, `/repos/${repo}/pulls?${params}`);
  },
};

const githubGetIssue: Tool = {
  id: 'github_get_issue',
  description: 'Get a specific issue with its details and comments (§17)',
  inputSchema: {
    type: 'object',
    properties: { repo: { type: 'string' }, issue_number: { type: 'number' } },
    required: ['repo', 'issue_number'],
  },
  scope: 'github.issues.read',
  isWrite: false,
  approval: 'never',
  execute: async (input, ctx) => {
    const { repo, issue_number } = input as Record<string, unknown>;
    const [issue, comments] = await Promise.all([
      githubFetch(ctx, `/repos/${repo}/issues/${issue_number}`),
      githubFetch(ctx, `/repos/${repo}/issues/${issue_number}/comments`),
    ]);
    return { ...(issue as Record<string, unknown>), comments };
  },
};

const githubCommentOnIssue: Tool = {
  id: 'github_comment_on_issue',
  description: 'Post a comment on an issue (§17)',
  inputSchema: {
    type: 'object',
    properties: { repo: { type: 'string' }, issue_number: { type: 'number' }, body: { type: 'string' } },
    required: ['repo', 'issue_number', 'body'],
  },
  scope: 'github.issues.write',
  isWrite: true,
  approval: 'always',
  execute: async (input, ctx) => {
    const { repo, issue_number, body } = input as Record<string, unknown>;
    return githubFetch(ctx, `/repos/${repo}/issues/${issue_number}/comments`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ body }),
    });
  },
};

const githubListRepositories: Tool = {
  id: 'github_list_repositories',
  description: 'List repositories the user has access to (§17)',
  inputSchema: { type: 'object', properties: { sort: { type: 'string', enum: ['updated', 'created', 'pushed', 'full_name'] }, per_page: { type: 'number' } } },
  scope: 'github.repos.read',
  isWrite: false,
  approval: 'never',
  execute: async (input, ctx) => {
    const { sort = 'updated', per_page = 20 } = input as Record<string, unknown>;
    const params = new URLSearchParams({ sort: String(sort), per_page: String(per_page) });
    return githubFetch(ctx, `/user/repos?${params}`);
  },
};

const githubGetNotifications: Tool = {
  id: 'github_get_notifications',
  description: 'Get unread notifications for the authenticated user (§17)',
  inputSchema: { type: 'object', properties: { all: { type: 'boolean' } } },
  scope: 'github.repos.read',
  isWrite: false,
  approval: 'never',
  execute: async (input, ctx) => {
    const { all = false } = input as Record<string, unknown>;
    const params = new URLSearchParams({ per_page: '30' });
    if (all) params.set('all', 'true');
    return githubFetch(ctx, `/notifications?${params}`);
  },
};

// §40: search is information retrieval, kept separate from §21 browser automation.
// Live via Exa Search API (server key, no per-user OAuth — information retrieval needs none).
// ponytail: fetch is stdlib; no SDK to own.
const webSearch: Tool = {
  id: 'web_search',
  description: 'Search the web for information (§40)',
  inputSchema: { type: 'object', properties: { query: { type: 'string' } }, required: ['query'] },
  scope: 'web.search',
  isWrite: false,
  approval: 'never',
  execute: async (input) => {
    const query = (input as { query?: unknown }).query;
    if (typeof query !== 'string' || !query.trim()) throw new Error('invalid_input: query is required');
    const key = process.env.EXA_API_KEY;
    if (!key) throw new Error('search_unconfigured');
    const r = await fetch('https://api.exa.ai/search', {
      method: 'POST',
      headers: { 'x-api-key': key, 'Content-Type': 'application/json' },
      body: JSON.stringify({ query: query.slice(0, 500), numResults: 5, contents: { text: { maxCharacters: 2000 } } }),
    });
    if (!r.ok) throw new Error('search_upstream_error');
    const body = (await r.json()) as { results?: { title?: string; url?: string; text?: string }[] };
    return {
      results: (body.results ?? []).map((x) => ({ title: x.title ?? '', url: x.url ?? '', text: (x.text ?? '').slice(0, 2000) })),
    };
  },
};

// §18 Google Calendar: read tools for chat, event creation approval-gated (§36).
async function calendarFetch(ctx: ToolContext, path: string, init?: RequestInit, retried = false): Promise<unknown> {
  const token = await getCalendarAccessToken(ctx.db, ctx.userId, { forceRefresh: retried });
  if (!token) {
    if (retried) await markCalendarConnectionExpired(ctx.db, ctx.userId);
    throw new Error(
      retried
        ? 'calendar_auth_invalid: the Calendar authorization is no longer valid. Ask the user to reconnect Calendar in Connections, then retry.'
        : 'calendar_not_connected',
    );
  }
  const res = await fetch(`${CALENDAR_API}${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${token}`, ...init?.headers },
  });
  if (res.status === 401 && !retried) return calendarFetch(ctx, path, init, true);
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`Calendar API ${res.status}: ${body.slice(0, 200)}`);
  }
  return res.json();
}

const calendarListEvents: Tool = {
  id: 'calendar_list_events',
  description: 'List upcoming Google Calendar events, optionally within a time range (§18)',
  inputSchema: {
    type: 'object',
    properties: {
      timeMin: { type: 'string', description: 'ISO start, e.g. 2026-09-15T00:00:00Z (default: now)' },
      timeMax: { type: 'string', description: 'ISO end' },
      maxResults: { type: 'number', description: '1-50, default 10' },
      query: { type: 'string', description: 'Free-text filter' },
    },
  },
  scope: 'calendar.read',
  isWrite: false,
  approval: 'never',
  execute: async (input, ctx) => {
    const { timeMin, timeMax, maxResults, query } = (input ?? {}) as Record<string, string | number | undefined>;
    const params = new URLSearchParams({
      singleEvents: 'true',
      orderBy: 'startTime',
      maxResults: String(Math.min(50, Math.max(1, Number(maxResults) || 10))),
      timeMin: typeof timeMin === 'string' && timeMin ? timeMin : new Date().toISOString(),
    });
    if (typeof timeMax === 'string' && timeMax) params.set('timeMax', timeMax);
    if (typeof query === 'string' && query) params.set('q', query);
    const body = (await calendarFetch(ctx, `/calendars/primary/events?${params}`)) as { items?: unknown[] };
    return { events: body.items ?? [] };
  },
};

const calendarCreateEvent: Tool = {
  id: 'calendar_create_event',
  description: 'Create a Google Calendar event (§18 write — always requires human approval)',
  inputSchema: {
    type: 'object',
    properties: {
      summary: { type: 'string' },
      start: { type: 'string', description: 'ISO date-time or date' },
      end: { type: 'string' },
      description: { type: 'string' },
      location: { type: 'string' },
    },
    required: ['summary', 'start', 'end'],
  },
  scope: 'calendar.write',
  isWrite: true,
  approval: 'always',
  execute: async (input, ctx) => {
    const { summary, start, end, description, location } = input as Record<string, string | undefined>;
    if (!summary || !start || !end) throw new Error('invalid_input: summary, start and end are required');
    return calendarFetch(ctx, '/calendars/primary/events', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        summary,
        ...(description ? { description } : {}),
        ...(location ? { location } : {}),
        start: { dateTime: start },
        end: { dateTime: end },
      }),
    });
  },
};

// §18/§27 LeetCode: server-keyed read tools (no OAuth — public profile data).
// The wrapper has a public rate limit, so tools stay few and results are cached
// in integrations/leetcode.ts. If the wrapper is down the tool reports
// 'leetcode_unavailable' and the model tells the user (§19).
const leetcodeScope = 'leetcode.read';
const usernameSchema = { type: 'object', properties: { username: { type: 'string', description: 'LeetCode username, e.g. alfaarghya' } }, required: ['username'] };

const leetcodeGetProfile: Tool = {
  id: 'leetcode_get_profile',
  description: 'Get a LeetCode user profile: real name, ranking, reputation, country, school, skills (§18)',
  inputSchema: usernameSchema,
  scope: leetcodeScope,
  isWrite: false,
  approval: 'never',
  execute: async (input) => getProfile(assertUsername((input as { username?: unknown }).username)),
};

const leetcodeGetSolved: Tool = {
  id: 'leetcode_get_solved',
  description: 'Get a LeetCode user solved-problem counts by difficulty, and totals (§18)',
  inputSchema: usernameSchema,
  scope: leetcodeScope,
  isWrite: false,
  approval: 'never',
  execute: async (input) => getSolved(assertUsername((input as { username?: unknown }).username)),
};

const leetcodeGetRecentSubmissions: Tool = {
  id: 'leetcode_get_recent_submissions',
  description: 'Get a LeetCode user most recent accepted/rejected submissions (§18)',
  inputSchema: {
    type: 'object',
    properties: { username: { type: 'string' }, limit: { type: 'number', description: '1-20, default 10' } },
    required: ['username'],
  },
  scope: leetcodeScope,
  isWrite: false,
  approval: 'never',
  execute: async (input) => {
    const { username, limit } = input as { username?: unknown; limit?: unknown };
    return getRecentSubmissions(assertUsername(username), typeof limit === 'number' ? limit : 10);
  },
};

const leetcodeGetContestHistory: Tool = {
  id: 'leetcode_get_contest_history',
  description: 'Get a LeetCode user contest ranking history and attendance (§18)',
  inputSchema: usernameSchema,
  scope: leetcodeScope,
  isWrite: false,
  approval: 'never',
  execute: async (input) => getContestHistory(assertUsername((input as { username?: unknown }).username)),
};

const leetcodeDailyChallenge: Tool = {
  id: 'leetcode_daily_challenge',
  description: 'Get the LeetCode problem of the day with its statement and difficulty (§18)',
  inputSchema: { type: 'object', properties: {} },
  scope: leetcodeScope,
  isWrite: false,
  approval: 'never',
  execute: async () => getDailyChallenge(),
};

const leetcodeSearchProblems: Tool = {
  id: 'leetcode_search_problems',
  description: 'Search LeetCode problems by topic tags and/or difficulty (§18)',
  inputSchema: {
    type: 'object',
    properties: {
      tags: { type: 'string', description: 'space or + separated tags, e.g. "array dynamic-programming"' },
      difficulty: { type: 'string', enum: ['EASY', 'MEDIUM', 'HARD'] },
      limit: { type: 'number', description: '1-50, default 20' },
      skip: { type: 'number' },
    },
  },
  scope: leetcodeScope,
  isWrite: false,
  approval: 'never',
  execute: async (input) => {
    const { tags, difficulty, limit, skip } = (input ?? {}) as { tags?: string; difficulty?: string; limit?: number; skip?: number };
    return searchProblems({ tags, difficulty, limit, skip });
  },
};

const leetcodeGetProblem: Tool = {
  id: 'leetcode_get_problem',
  description: 'Get one LeetCode problem by titleSlug (e.g. two-sum): statement, tags, difficulty (§18)',
  inputSchema: { type: 'object', properties: { titleSlug: { type: 'string' } }, required: ['titleSlug'] },
  scope: leetcodeScope,
  isWrite: false,
  approval: 'never',
  execute: async (input) => getProblem(String((input as { titleSlug?: unknown }).titleSlug ?? '')),
};

/**
 * §17 Gmail fetch helper. Access token auto-refreshes in place (§38).
 * A 401 forces one refresh + retry: the access token may have been revoked
 * server-side between refreshes, and losing the reply to that is avoidable.
 * §46: token is never stored in logs or step metadata.
 */
async function gmailFetch(ctx: ToolContext, path: string, init?: RequestInit, retried = false): Promise<unknown> {
  const token = await getGmailAccessToken(ctx.db, ctx.userId, { forceRefresh: retried });
  if (!token) {
    if (retried) await markGmailConnectionExpired(ctx.db, ctx.userId);
    throw new Error(
      retried
        ? 'gmail_auth_invalid: the Gmail authorization is no longer valid. Ask the user to reconnect Gmail in Connections, then retry.'
        : 'gmail_not_connected',
    );
  }
  const res = await fetch(`${GMAIL_API}${path}`, {
    ...init,
    headers: { Authorization: `Bearer ${token}`, ...init?.headers },
  });
  if (res.status === 401 && !retried) return gmailFetch(ctx, path, init, true);
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    if (res.status === 403 && /insufficient|scope/i.test(body)) {
      throw new Error('gmail_scope_missing: the Gmail connection does not grant this access. Ask the user to reconnect Gmail and approve all requested permissions.');
    }
    throw new Error(`Gmail API ${res.status}: ${body.slice(0, 200)}`);
  }
  return res.json();
}

/** Gmail payloads are base64url — convert to utf8 text. */
function b64urlToText(data?: string): string {
  if (!data) return '';
  const b64 = data.replace(/-/g, '+').replace(/_/g, '/');
  return Buffer.from(b64, 'base64').toString('utf8');
}

interface GmailPayload {
  mimeType?: string;
  body?: { data?: string };
  parts?: GmailPayload[];
  headers?: { name: string; value: string }[];
}

function headerOf(payload: GmailPayload | undefined, name: string): string {
  return payload?.headers?.find((h) => h.name.toLowerCase() === name.toLowerCase())?.value ?? '';
}

/** Prefer text/plain, fall back to text/html stripped of tags. Truncated by caller. */
function extractGmailBody(payload?: GmailPayload): string {
  if (!payload) return '';
  const walk = (p: GmailPayload, prefer: string): string | null => {
    if (p.mimeType === prefer && p.body?.data) return b64urlToText(p.body.data);
    for (const part of p.parts ?? []) {
      const hit = walk(part, prefer);
      if (hit) return hit;
    }
    return null;
  };
  const plain = walk(payload, 'text/plain');
  if (plain) return plain;
  const html = walk(payload, 'text/html');
  if (html) return html.replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
  if (payload.body?.data) return b64urlToText(payload.body.data);
  return '';
}

// Level 1 native: gmail read tools are chat-safe; send is write-gated (§17/§36).
const gmailListMessages: Tool = {
  id: 'gmail_list_messages',
  description: 'List recent Gmail messages, optionally filtered by Gmail search query (§17)',
  inputSchema: {
    type: 'object',
    properties: {
      query: { type: 'string', description: 'Gmail search query, e.g. "from:boss newer_than:7d"' },
      maxResults: { type: 'number', description: 'Max messages (1-20, default 10)' },
    },
  },
  scope: 'gmail.readonly',
  isWrite: false,
  approval: 'never',
  execute: async (input, ctx) => {
    const { query = '', maxResults = 10 } = (input as Record<string, unknown>) ?? {};
    const n = Math.min(20, Math.max(1, Number(maxResults) || 10));
    const params = new URLSearchParams({ maxResults: String(n), ...(query ? { q: String(query) } : {}) });
    const list = (await gmailFetch(ctx, `/users/me/messages?${params}`)) as {
      messages?: { id: string; threadId: string }[];
    };
    const items = list.messages ?? [];
    // Hydrate each message with Subject/From/Date metadata (bounded by n <= 20).
    const hydrated = await Promise.all(
      items.map(async (m) => {
        const full = (await gmailFetch(ctx, `/users/me/messages/${m.id}?format=metadata&metadataHeaders=Subject&metadataHeaders=From&metadataHeaders=Date`)) as {
          snippet?: string;
          payload?: GmailPayload;
        };
        return {
          id: m.id,
          threadId: m.threadId,
          subject: headerOf(full.payload, 'Subject'),
          from: headerOf(full.payload, 'From'),
          date: headerOf(full.payload, 'Date'),
          snippet: (full.snippet ?? '').slice(0, 300),
        };
      }),
    );
    return { messages: hydrated };
  },
};

const gmailGetMessage: Tool = {
  id: 'gmail_get_message',
  description: 'Get a full Gmail message by id, with decoded body text (§17)',
  inputSchema: {
    type: 'object',
    properties: { id: { type: 'string', description: 'Gmail message id from list_messages' } },
    required: ['id'],
  },
  scope: 'gmail.readonly',
  isWrite: false,
  approval: 'never',
  execute: async (input, ctx) => {
    const { id } = input as Record<string, unknown>;
    if (typeof id !== 'string' || !id.trim()) throw new Error('invalid_input: id is required');
    const full = (await gmailFetch(ctx, `/users/me/messages/${encodeURIComponent(id)}?format=full`)) as {
      snippet?: string;
      payload?: GmailPayload;
    };
    return {
      id,
      subject: headerOf(full.payload, 'Subject'),
      from: headerOf(full.payload, 'From'),
      to: headerOf(full.payload, 'To'),
      date: headerOf(full.payload, 'Date'),
      snippet: full.snippet ?? '',
      body: extractGmailBody(full.payload).slice(0, 8000),
    };
  },
};

const gmailSendMessage: Tool = {
  id: 'gmail_send_message',
  description: 'Send an email via Gmail (§17 write — always requires human approval)',
  inputSchema: {
    type: 'object',
    properties: {
      to: { type: 'string', description: 'Recipient email address' },
      subject: { type: 'string' },
      body: { type: 'string', description: 'Plain-text email body' },
      cc: { type: 'string' },
      bcc: { type: 'string' },
    },
    required: ['to', 'subject', 'body'],
  },
  scope: 'gmail.send',
  isWrite: true,
  approval: 'always', // §36/§47 every send passes a human checkpoint
  execute: async (input, ctx) => {
    const { to, subject, body, cc, bcc } = input as Record<string, unknown>;
    if (typeof to !== 'string' || !to.includes('@')) throw new Error('invalid_input: valid "to" email is required');
    if (typeof subject !== 'string' || !subject.trim()) throw new Error('invalid_input: subject is required');
    if (typeof body !== 'string' || !body.trim()) throw new Error('invalid_input: body is required');
    const lines = [
      `To: ${to}`,
      ...(typeof cc === 'string' && cc ? [`Cc: ${cc}`] : []),
      ...(typeof bcc === 'string' && bcc ? [`Bcc: ${bcc}`] : []),
      `Subject: ${String(subject).replace(/[\r\n]/g, ' ')}`,
      'Content-Type: text/plain; charset=utf-8',
      '',
      String(body),
    ];
    const raw = Buffer.from(lines.join('\r\n'), 'utf8')
      .toString('base64')
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
    return gmailFetch(ctx, '/users/me/messages/send', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ raw }),
    });
  },
};

export const toolRegistry = new Map<string, Tool>(
  [
    githubListIssues, githubCreateIssue, githubListPullRequests,
    githubGetIssue, githubCommentOnIssue, githubListRepositories,
    githubGetNotifications, webSearch,
    gmailListMessages, gmailGetMessage, gmailSendMessage,
    leetcodeGetProfile, leetcodeGetSolved, leetcodeGetRecentSubmissions,
    leetcodeGetContestHistory, leetcodeDailyChallenge, leetcodeSearchProblems,
    leetcodeGetProblem,
    calendarListEvents, calendarCreateEvent,
  ].map((t) => [t.id, t]),
);

/**
 * §40/§18: tools that run on the backend's own keys — no per-user OAuth exists
 * or is needed, so the authorization gate must not demand a Connection.
 */
export function isServerKeyedTool(toolId: string): boolean {
  return toolId.startsWith('web_') || toolId.startsWith('leetcode_');
}

/** §15: attach to the Gemini request as function declarations. */
export function toToolDefs(tools: Tool[]) {
  return tools.map((t) => ({ name: t.id, description: t.description, parameters: t.inputSchema }));
}
