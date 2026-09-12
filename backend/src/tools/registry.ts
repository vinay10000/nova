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
  execute: async () => ({ issues: [], note: 'TODO: call GitHub API with the user OAuth token' }),
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
  execute: async () => ({ created: false, note: 'TODO: requires approval-gated GitHub write token' }),
};

// §40: search is information retrieval, kept separate from §21 browser automation.
// Prefer the provider's built-in google_search tool over a hand-rolled scraper.
const webSearch: Tool = {
  id: 'web.search',
  description: 'Search the web for information (§40)',
  inputSchema: { type: 'object', properties: { query: { type: 'string' } }, required: ['query'] },
  scope: 'web.search',
  isWrite: false,
  approval: 'never',
  execute: async () => ({ results: [], note: 'TODO: wire search provider or Gemini google_search' }),
};

export const toolRegistry = new Map<string, Tool>(
  [githubListIssues, githubCreateIssue, webSearch].map((t) => [t.id, t]),
);

/** §15: attach to the Gemini request as function declarations. */
export function toToolDefs(tools: Tool[]) {
  return tools.map((t) => ({ name: t.id, description: t.description, parameters: t.inputSchema }));
}
