import type { Tool } from './Tool.js';

// Level 1 native first: github, gmail, calendar (§27). Others via IntegrationAdapter or browser.

// §17 read/write are separate scopes and separate tools. Connecting GitHub grants read only.
const githubListIssues: Tool = {
  id: 'github.list_issues',
  description: 'List issues assigned to the authenticated user (§17)',
  inputSchema: { type: 'object', properties: { state: { type: 'string', enum: ['open', 'closed', 'all'] } } },
  scope: 'github.issues.read',
  isWrite: false,
  approval: 'write',
  // §61: no fake empty list. OAuth token use lands in Phase 4; until then the
  // gate denies with auth_required (no Connection row exists) before this runs.
  execute: async () => { throw new Error('github_unconfigured'); },
};

const githubCreateIssue: Tool = {
  id: 'github.create_issue',
  description: 'Create an issue in a repository (§17)',
  inputSchema: {
    type: 'object',
    properties: { repo: { type: 'string' }, title: { type: 'string' }, body: { type: 'string' } },
    required: ['repo', 'title'],
  },
  scope: 'github.issues.write',
  isWrite: true,
  approval: 'always', // §36/§47 every write passes a human checkpoint
  execute: async () => { throw new Error('github_unconfigured'); },
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
  [githubListIssues, githubCreateIssue, webSearch].map((t) => [t.id, t]),
);

/** §15: attach to the Gemini request as function declarations. */
export function toToolDefs(tools: Tool[]) {
  return tools.map((t) => ({ name: t.id, description: t.description, parameters: t.inputSchema }));
}
