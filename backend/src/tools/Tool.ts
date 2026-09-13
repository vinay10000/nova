// §15 generic tool abstraction + §47 safety pipeline.
import type { PrismaClient } from '@prisma/client';

export interface ToolContext {
  userId: string;
  agentId: string;
  permissions: string[];
  /** §46: DB access for fetching encrypted tokens — never logged, never in ExecutionStep metadata. */
  db: PrismaClient;
}

export interface Tool {
  id: string; // e.g. github.list_issues
  description: string;
  inputSchema: unknown; // JSON Schema handed to Gemini as the function `parameters`
  /** §37 least privilege: the granular scope this tool needs, e.g. 'github.issues.read'. */
  scope: string;
  /** §17 write/send tools must not be auto-granted by connecting the provider. */
  isWrite: boolean;
  /** §36 approval checkpoints, configurable per tool/action. */
  approval: 'never' | 'always' | 'write';
  execute(input: unknown, ctx: ToolContext): Promise<unknown>;
}

/**
 * §47 outcome. A denial is a RESULT the model can read and explain, not a thrown
 * error — that is what lets the UI offer "[Connect GitHub]" / "[Approve]".
 */
export type ToolOutcome =
  | { status: 'ok'; result: unknown }
  | { status: 'denied'; code: 'tool_unavailable' | 'permission_required' | 'auth_required'; message: string; connect?: string }
  | { status: 'approval_required'; toolId: string; payload: unknown }
  | { status: 'rejected'; toolId: string }
  | { status: 'error'; message: string };

export interface SafetyChecks {
  hasPermission: boolean;
  hasAuth: boolean;
  /** Present only when the tool requires approval; may suspend until a human decides. */
  approve?: () => Promise<boolean>;
  /** §46 audit trail; must never receive credentials. */
  audit?: (outcome: ToolOutcome) => Promise<void>;
}

const WRITE_NEEDS_APPROVAL: Tool['approval'][] = ['always', 'write'];

/**
 * The single choke point every tool call passes through (§15, §47).
 * Order is deliberate and FAILS CLOSED: availability, then agent permission, then user
 * authorization, then approval — all before any side effect.
 */
export async function runToolWithSafety(
  tool: Tool | undefined,
  toolId: string,
  input: unknown,
  ctx: ToolContext,
  checks: SafetyChecks,
): Promise<ToolOutcome> {
  const finish = async (outcome: ToolOutcome): Promise<ToolOutcome> => {
    await checks.audit?.(outcome);
    return outcome;
  };

  // 1. Is the tool available?
  if (!tool) {
    return finish({ status: 'denied', code: 'tool_unavailable', message: `Unknown tool: ${toolId}` });
  }

  // 2. Does the agent have permission? Checked against the tool's granular scope (§37),
  //    never against a broad provider grant.
  if (!checks.hasPermission || !ctx.permissions.includes(tool.scope)) {
    return finish({ status: 'denied', code: 'permission_required', message: `Permission required: ${tool.scope}` });
  }

  // 3. Does the user have authorization (a live, in-scope connection)?
  if (!checks.hasAuth) {
    return finish({ status: 'denied', code: 'auth_required', message: 'This service is not connected.', connect: tool.scope.split('.')[0] });
  }

  // 4. Does the action require approval? A write tool marked 'write' only gates when it IS a write.
  const needsApproval = tool.approval === 'always' || (tool.approval === 'write' && tool.isWrite);
  if (needsApproval) {
    if (!checks.approve) {
      // No approval channel wired up yet — deny rather than execute ungoverned.
      return finish({ status: 'approval_required', toolId: tool.id, payload: input });
    }
    if (!(await checks.approve())) {
      return finish({ status: 'rejected', toolId: tool.id });
    }
  }

  // 5. Execute.
  try {
    return await finish({ status: 'ok', result: await tool.execute(input, ctx) });
  } catch (err) {
    return await finish({ status: 'error', message: err instanceof Error ? err.message : 'tool_failed' });
  }
}
