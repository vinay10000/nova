import type { Tool, ToolContext } from './Tool.js';
import { getGitHubToken } from '../integrations/github.js';

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
  if (!res.ok) {
    const body = await res.text().catch(() => '');
    throw new Error(`GitHub API ${res.status}: ${body.slice(0, 200)}`);
  }
  return res.json();
}

// Level 1 native first: github, gmail, calendar (§27). Others via IntegrationAdapter or browser.

// §17 read/write are separate scopes and separate tools. Connecting GitHub grants read only.
const githubListIssues: Tool = {
  id: 'github.list_issues',
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
  id: 'github.create_issue',
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
  id: 'github.list_pull_requests',
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
  id: 'github.get_issue',
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
  id: 'github.comment_on_issue',
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
  id: 'github.list_repositories',
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
  id: 'github.get_notifications',
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
  id: 'web.search',
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

export const toolRegistry = new Map<string, Tool>(
  [
    githubListIssues, githubCreateIssue, githubListPullRequests,
    githubGetIssue, githubCommentOnIssue, githubListRepositories,
    githubGetNotifications, webSearch,
  ].map((t) => [t.id, t]),
);

/** §15: attach to the Gemini request as function declarations. */
export function toToolDefs(tools: Tool[]) {
  return tools.map((t) => ({ name: t.id, description: t.description, parameters: t.inputSchema }));
}
