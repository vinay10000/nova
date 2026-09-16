import type { PrismaClient } from '@prisma/client';
import type { ToolDef } from '../ai/AIProvider.js';
import type { Tool } from '../tools/Tool.js';
import { toolRegistry, toToolDefs, isServerKeyedTool } from '../tools/registry.js';
import { runToolWithSafety } from '../tools/Tool.js';

// §42 Chat + Agent Integration: @plugins in chat activate tool-backed conversations.
// Each plugin maps an @mention to tools, a system instruction, and a connection.

export interface ChatPlugin {
  /** The @mention trigger, e.g. "github" */
  id: string;
  /** Human-readable name */
  name: string;
  /** One-line description for the mention picker */
  blurb: string;
  /** Tool ids this plugin activates */
  toolIds: string[];
  /** System instruction appended to the conversation when this plugin is active */
  systemInstruction: string;
  /** Connection this plugin needs, or null when it runs on backend keys (§40). */
  requires: 'github' | 'gmail' | 'calendar' | 'drive' | 'vercel' | 'supabase' | null;
}

// §17: only read tools are auto-activated by @github in chat.
// Write tools still require agent-level approval (§36).
const GITHUB_READ_TOOLS = [
  'github_list_repositories',
  'github_list_issues',
  'github_get_issue',
  'github_list_pull_requests',
  'github_get_notifications',
];

// §17: only read tools are auto-activated by @gmail in chat.
// gmail.send_message stays agent-side behind approval (§36).
const GMAIL_READ_TOOLS = ['gmail_list_messages', 'gmail_get_message'];

// §18: LeetCode runs on backend keys — public data, no OAuth, always ready.
const LEETCODE_TOOLS = [
  'leetcode_get_profile',
  'leetcode_get_solved',
  'leetcode_get_recent_submissions',
  'leetcode_get_contest_history',
  'leetcode_daily_challenge',
  'leetcode_search_problems',
  'leetcode_get_problem',
];

// §18: chat reads the calendar only. calendar_create_event stays agent-side and
// approval-gated (§36) — chat never writes to a user's calendar unattended.
const CALENDAR_READ_TOOLS = ['calendar_list_events'];

export const chatPlugins: ChatPlugin[] = [
  {
    id: 'github',
    name: 'GitHub',
    blurb: 'Repos, issues, pull requests, notifications',
    toolIds: GITHUB_READ_TOOLS,
    requires: 'github',
    systemInstruction:
      'The user is asking about their GitHub account. Use the GitHub tools to answer. ' +
      'Be concise. If they ask about repos, list them. If they ask about issues or PRs, summarize what matters. ' +
      'Never fabricate data — always call the tools. ' +
      'If a tool reports the authorization is no longer valid or that GitHub is not connected, ' +
      'tell the user plainly to reconnect GitHub from the Connections screen. Never invent repository data.',
  },
  {
    id: 'gmail',
    name: 'Gmail',
    blurb: 'Search, read and summarize mail',
    toolIds: GMAIL_READ_TOOLS,
    requires: 'gmail',
    systemInstruction:
      'The user is asking about their Gmail inbox. Use the Gmail tools to answer. ' +
      'list_messages for search or recent mail, get_message for full bodies. ' +
      'Be concise. Never fabricate mail — always call the tools. Never send without explicit approval. ' +
      'If a tool reports the authorization is invalid or Gmail is not connected, tell the user plainly ' +
      'to reconnect Gmail from the Connections screen.',
  },
  {
    id: 'leetcode',
    name: 'LeetCode',
    blurb: 'Profiles, solved counts, contests, daily problem',
    toolIds: LEETCODE_TOOLS,
    requires: null,
    systemInstruction:
      'The user is asking about LeetCode. Use the LeetCode tools — they read public data, so no login is needed. ' +
      'Ask for the username when it is missing. Report solved counts by difficulty, ranking, and recent submissions. ' +
      'Never fabricate problem titles, counts or contest ranks: call the tools.',
  },
  {
    id: 'calendar',
    name: 'Calendar',
    blurb: 'What is coming up, by day and time',
    toolIds: CALENDAR_READ_TOOLS,
    requires: 'calendar',
    systemInstruction:
      'The user is asking about their Google Calendar. Use calendar_list_events with an explicit ISO time range ' +
      '(today, this week, or the range they asked for) and summarize in plain language with day and time. ' +
      'Do not create events in chat — say that creating an event needs an agent with approval. ' +
      'If the tool reports the connection is missing or invalid, tell the user to connect or reconnect Calendar.',
  },
  {
    id: 'drive',
    name: 'Drive',
    blurb: 'Files and folders in Google Drive',
    toolIds: ['drive_list'],
    requires: 'drive',
    systemInstruction:
      'The user is asking about their Google Drive. Use drive_list to answer. Be concise. Never fabricate files — always call the tools. ' +
      'If a tool reports Drive is not connected or authorization is invalid, tell the user plainly to connect Drive from the Connections screen.',
  },
  {
    id: 'docs',
    name: 'Docs',
    blurb: 'Read and create Google Docs',
    toolIds: ['drive_docs_get'],
    requires: 'drive',
    systemInstruction:
      'The user is asking about Google Docs. Use drive_docs_get with the document id. Creating docs needs an agent with approval — say so in chat. ' +
      'Never fabricate document content. On auth errors, tell the user to connect Drive from the Connections screen.',
  },
  {
    id: 'sheets',
    name: 'Sheets',
    blurb: 'Read and append Google Sheets',
    toolIds: ['drive_sheets_read'],
    requires: 'drive',
    systemInstruction:
      'The user is asking about Google Sheets. Use drive_sheets_read with spreadsheetId and range (e.g. Sheet1!A1:D20). ' +
      'Writing needs an agent with approval — say so in chat. Never fabricate cell values. On auth errors, tell the user to connect Drive.',
  },
  {
    id: 'vercel',
    name: 'Vercel',
    blurb: 'Projects and deployments',
    toolIds: ['vercel_list_projects', 'vercel_list_deployments'],
    requires: 'vercel',
    systemInstruction:
      'The user is asking about Vercel. Use vercel_list_projects and vercel_list_deployments. Deploys/rollbacks need an agent with approval — say so in chat. ' +
      'Never fabricate deployment states. On auth errors, tell the user to reconnect Vercel from the Connections screen.',
  },
  {
    id: 'supabase',
    name: 'Supabase',
    blurb: 'Tables and rows in your project',
    toolIds: ['supabase_list_tables', 'supabase_query_rows'],
    requires: 'supabase',
    systemInstruction:
      'The user is asking about Supabase. Use supabase_list_tables then supabase_query_rows with a safe table name. ' +
      'Inserts need an agent with approval — say so in chat. Never fabricate rows. On auth errors, tell the user to reconnect Supabase.',
  },
];


export interface PluginToolResult {
  result: string;
  isError: boolean;
  /** Set when the failure is "the user must (re)connect this provider". */
  reconnect?: string;
  /** Set when the failure is transient (upstream down / rate limited). */
  retryable?: boolean;
}

/**
 * Map a tool failure onto something the model can relay and the app can act on:
 * a reconnect prompt for dead credentials, a retry hint for upstream wobble.
 */
export function pluginErrorResult(message: string, provider: string): PluginToolResult {
  if (/auth_invalid|not_connected|no longer valid/i.test(message)) {
    return {
      result: JSON.stringify({
        error: 'reconnect_required',
        provider,
        message: `The ${provider} authorization is no longer valid. Tell the user to reconnect ${provider} from the Connections screen. Do not invent data.`,
      }),
      isError: true,
      reconnect: provider,
    };
  }
  if (/scope_missing/i.test(message)) {
    return {
      result: JSON.stringify({
        error: 'scope_missing',
        provider,
        message: `The ${provider} connection is missing a required permission. Tell the user to reconnect ${provider} and approve every requested permission.`,
      }),
      isError: true,
      reconnect: provider,
    };
  }
  if (/rate_limited|unavailable|429|502|503|504/i.test(message)) {
    return {
      result: JSON.stringify({
        error: 'upstream_unavailable',
        provider,
        message: `The ${provider} service is temporarily unavailable or rate limited. Tell the user to try again shortly.`,
      }),
      isError: true,
      retryable: true,
    };
  }
  return { result: JSON.stringify({ error: message }), isError: true };
}

/**
 * Execute a single plugin tool call through the safety pipeline.
 * §47: same gate as agent execution — permission, authorization, approval.
 */
export async function executePluginTool(
  db: PrismaClient,
  userId: string,
  toolId: string,
  args: unknown,
): Promise<PluginToolResult> {
  const tool = toolRegistry.get(toolId);
  if (!tool) return { result: `Unknown tool: ${toolId}`, isError: true };

  // Ids are provider_action (github_list_issues) — the prefix before "_" is the provider.
  const provider = toolId.split('_')[0]!;
  const serverKeyed = isServerKeyedTool(toolId);
  const conn = serverKeyed
    ? null
    : await db.connection.findFirst({ where: { userId, provider } });

  const outcome = await runToolWithSafety(
    tool,
    toolId,
    args,
    { userId, agentId: 'chat-plugin', permissions: [tool.scope], db },
    {
      hasPermission: true, // chat plugins are pre-approved for their own read tools
      hasAuth: serverKeyed || conn?.status === 'connected',
    },
  );

  switch (outcome.status) {
    case 'ok':
      return { result: JSON.stringify(outcome.result).slice(0, 8000), isError: false };
    case 'denied':
      if (outcome.code === 'auth_required') {
        // Distinguish "never connected" from "connected but the token died":
        // the user's next action differs, and naming the wrong one wastes their time.
        const expired = conn?.status === 'expired';
        return {
          result: JSON.stringify({
            denied: 'auth_required',
            provider: outcome.connect,
            expired,
            message: expired
              ? `${outcome.connect} authorization has expired. Tell the user to reconnect ${outcome.connect} from the Connections screen, then ask again.`
              : `${outcome.connect} is not connected yet. Tell the user to connect ${outcome.connect} from the Connections screen.`,
          }),
          isError: true,
          reconnect: outcome.connect,
        };
      }
      return { result: JSON.stringify({ denied: outcome.code, message: outcome.message, connect: outcome.connect }), isError: true };
    case 'error':
      return pluginErrorResult(outcome.message, provider);
    case 'approval_required':
      return { result: 'This action requires approval. Use the agent builder for write operations.', isError: false };
    case 'rejected':
      return { result: 'Action was rejected.', isError: true };
  }
}


// Max tool steps in a chat plugin loop — keeps responses fast (§46 execution limits).
export const MAX_PLUGIN_STEPS = 5;

/** Which of a user's providers are live (status = connected). */
export async function connectedProviders(db: PrismaClient, userId: string): Promise<Set<string>> {
  const rows = await db.connection.findMany({
    where: { userId, status: 'connected' },
    select: { provider: true },
  });
  return new Set(rows.map((r) => r.provider));
}

/** A plugin is usable when it needs no connection, or the user has one. */
export function isPluginUsable(plugin: ChatPlugin, connected: Set<string>): boolean {
  return plugin.requires === null || connected.has(plugin.requires);
}

/**
 * Detect a @plugin mention in a user message (mention may appear anywhere).
 * Returns the plugin and the message to send to the model (the mention is
 * stripped only when it leads the message), or null.
 *
 * `connected` gates the keyword fallback: routing a question to a provider the
 * user never connected only produces "not connected" noise — and it swaps the
 * model mid-chat for no reason.
 */
export function detectPlugin(
  message: string,
  connected?: Set<string>,
): { plugin: ChatPlugin; cleanedMessage: string } | null {
  for (const plugin of chatPlugins) {
    const lead = message.match(new RegExp(`^@${plugin.id}\\s+`, 'i'));
    if (lead) return { plugin, cleanedMessage: message.slice(lead[0].length).trim() };
    if (new RegExp(`@${plugin.id}\\b`, 'i').test(message)) {
      return { plugin, cleanedMessage: message };
    }
  }
  const usable = (id: string): ChatPlugin | null => {
    const plugin = chatPlugins.find((p) => p.id === id)!;
    return !connected || isPluginUsable(plugin, connected) ? plugin : null;
  };
  // Keyword fallback: plugin-shaped messages route even without an explicit @mention.
  if (/\b(github|repo(s)?|issue(s)?|pull request(s)?|pr(s)?)\b/i.test(message)) {
    const plugin = usable('github');
    if (plugin) return { plugin, cleanedMessage: message };
  }
  if (/\b(gmail|inbox|unread mail)\b/i.test(message)) {
    const plugin = usable('gmail');
    if (plugin) return { plugin, cleanedMessage: message };
  }
  if (/\b(leetcode|daily challenge|contest rank(ing)?)\b/i.test(message)) {
    const plugin = usable('leetcode');
    if (plugin) return { plugin, cleanedMessage: message };
  }
  if (/\b(calendar|agenda|my (schedule|meetings?))\b/i.test(message)) {
    const plugin = usable('calendar');
    if (plugin) return { plugin, cleanedMessage: message };
  }
  if (/\b(google docs?|docs? document)\b/i.test(message)) {
    const plugin = usable('docs');
    if (plugin) return { plugin, cleanedMessage: message };
  }
  if (/\b(google sheets?|spreadsheet)\b/i.test(message)) {
    const plugin = usable('sheets');
    if (plugin) return { plugin, cleanedMessage: message };
  }
  if (/\b(google drive|my drive|drive files?)\b/i.test(message)) {
    const plugin = usable('drive');
    if (plugin) return { plugin, cleanedMessage: message };
  }
  if (/\b(vercel|deployment(s)?|deploy preview)\b/i.test(message)) {
    const plugin = usable('vercel');
    if (plugin) return { plugin, cleanedMessage: message };
  }
  if (/\b(supabase|postgres rows?|db table)\b/i.test(message)) {
    const plugin = usable('supabase');
    if (plugin) return { plugin, cleanedMessage: message };
  }
  return null;
}

/** §42: the catalogue the client renders in the @ mention picker. */
export function pluginCatalog(connected: Set<string>) {
  return chatPlugins.map((p) => ({
    id: p.id,
    name: p.name,
    blurb: p.blurb,
    requires: p.requires,
    ready: isPluginUsable(p, connected),
    toolIds: p.toolIds,
  }));
}

/** Get the tool definitions for a plugin. */
export function pluginToolDefs(plugin: ChatPlugin): ToolDef[] {
  const tools = plugin.toolIds
    .map((id) => toolRegistry.get(id))
    .filter((t): t is Tool => !!t);
  return toToolDefs(tools);
}

