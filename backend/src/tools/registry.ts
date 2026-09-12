import type { Tool } from './Tool.js';

// Level 1 native first: github, gmail, calendar (§27). Others via IntegrationAdapter or browser.
const githubListIssues: Tool = {
  id: 'github.list_issues', description: 'List assigned GitHub issues (§17)',
  inputSchema: { type: 'object', properties: { state: { type: 'string' } } },
  requiresApproval: false,
  async execute(input) { return { issues: [], note: 'TODO: call GitHub API with user OAuth token' }; },
};
const webSearch: Tool = {
  id: 'web.search', description: 'Web search, separate from browser automation (§40)',
  inputSchema: { type: 'object', properties: { query: { type: 'string' } } },
  requiresApproval: false,
  async execute(input) { return { results: [], note: 'TODO: plug search provider' }; },
};

export const toolRegistry = new Map<string, Tool>([
  [githubListIssues.id, githubListIssues],
  [webSearch.id, webSearch],
]);
