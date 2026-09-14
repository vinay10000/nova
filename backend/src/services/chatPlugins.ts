import type { PrismaClient } from '@prisma/client';
import type { ToolDef } from '../ai/AIProvider.js';
import type { Tool, ToolContext } from '../tools/Tool.js';
import { toolRegistry, toToolDefs } from '../tools/registry.js';
import { runToolWithSafety } from '../tools/Tool.js';

// §42 Chat + Agent Integration: @plugins in chat activate tool-backed conversations.
// Each plugin maps an @mention to tools, a system instruction, and required scopes.

export interface ChatPlugin {
  /** The @mention trigger, e.g. "github" */
  id: string;
  /** Human-readable name */
  name: string;
  /** Tool ids this plugin activates */
  toolIds: string[];
  /** System instruction appended to the conversation when this plugin is active */
  systemInstruction: string;
}

// §17: only read tools are auto-activated by @github in chat.
// Write tools still require agent-level approval (§36).
const GITHUB_READ_TOOLS = [
  'github.list_repositories',
  'github.list_issues',
  'github.get_issue',
  'github.list_pull_requests',
  'github.get_notifications',
];

// §17: only read tools are auto-activated by @gmail in chat.
// gmail.send_message stays agent-side behind approval (§36).
const GMAIL_READ_TOOLS = ['gmail.list_messages', 'gmail.get_message'];

export const chatPlugins: ChatPlugin[] = [
  {
    id: 'github',
    name: 'GitHub',
    toolIds: GITHUB_READ_TOOLS,
    systemInstruction:
      'The user is asking about their GitHub account. Use the GitHub tools to answer their question. ' +
      'Be concise. If the user asks about repos, list them. If they ask about issues or PRs, summarize what matters. ' +
      'Never fabricate data — always call the tools to get real information.',
  },
  {
    id: 'gmail',
    name: 'Gmail',
    toolIds: GMAIL_READ_TOOLS,
    systemInstruction:
      'The user is asking about their Gmail inbox. Use the Gmail tools to answer. ' +
      'list_messages for search/recent mail, get_message for full bodies. ' +
      'Be concise. Never fabricate mail — always call the tools. Never send without explicit approval.',
  },
];

// Max tool steps in a chat plugin loop — keeps responses fast (§46 execution limits).
export const MAX_PLUGIN_STEPS = 5;

/**
 * Detect a @plugin mention in a user message.
 * Returns the plugin and the cleaned message (mention stripped), or null.
 */
export function detectPlugin(message: string): { plugin: ChatPlugin; cleanedMessage: string } | null {
  for (const plugin of chatPlugins) {
    const pattern = new RegExp(`^@${plugin.id}\\s+`, 'i');
    const match = message.match(pattern);
    if (match) {
      return { plugin, cleanedMessage: message.slice(match[0].length).trim() };
    }
  }
  return null;
}

/**
 * Get the tool definitions for a plugin.
 */
export function pluginToolDefs(plugin: ChatPlugin): ToolDef[] {
  const tools = plugin.toolIds
    .map((id) => toolRegistry.get(id))
    .filter((t): t is Tool => !!t);
  return toToolDefs(tools);
}

/**
 * Execute a single plugin tool call via the safety pipeline.
 * §47: same gate as agent execution — permission, auth, approval.
 */
export async function executePluginTool(
  db: PrismaClient,
  userId: string,
  toolId: string,
  args: unknown,
): Promise<{ result: string; isError: boolean }> {
  const tool = toolRegistry.get(toolId);
  const plugin = chatPlugins.find((p) => p.toolIds.includes(toolId));
  if (!tool || !plugin) return { result: `Unknown tool: ${toolId}`, isError: true };

  // Look up the user's connection for this provider
  const provider = toolId.split('.')[0]!;
  const conn = await db.connection.findFirst({
    where: { userId, provider, status: 'connected' },
  });

  const outcome = await runToolWithSafety(
    tool,
    toolId,
    args,
    { userId, agentId: 'chat-plugin', permissions: plugin.toolIds.map((id) => toolRegistry.get(id)?.scope ?? ''), db },
    {
      hasPermission: true, // chat plugins are pre-approved for their own tools
      hasAuth: !!conn,
    },
  );

  switch (outcome.status) {
    case 'ok':
      return { result: JSON.stringify(outcome.result).slice(0, 8000), isError: false };
    case 'denied':
      return { result: JSON.stringify({ denied: outcome.code, message: outcome.message, connect: outcome.connect }), isError: true };
    case 'error':
      return { result: JSON.stringify({ error: outcome.message }), isError: true };
    case 'approval_required':
      return { result: 'This action requires approval. Please use the agent builder for write operations.', isError: false };
    case 'rejected':
      return { result: 'Action was rejected.', isError: true };
  }
}
