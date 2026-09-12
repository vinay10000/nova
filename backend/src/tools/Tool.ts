// §15 generic tool abstraction + §47 safety pipeline.
export interface ToolContext { userId: string; agentId: string; permissions: string[]; }
export interface Tool {
  id: string; // e.g. github.list_issues
  description: string;
  inputSchema: unknown;
  requiresApproval: boolean;
  execute(input: unknown, ctx: ToolContext): Promise<unknown>;
}

// Pipeline: validate -> agent permission -> user auth -> approval? -> execute -> return to Gemini (§15,§47)
export async function runToolWithSafety(tool: Tool, input: unknown, ctx: ToolContext, checks: {
  hasPermission: boolean; hasAuth: boolean; approve: () => Promise<boolean>;
}): Promise<unknown> {
  if (!checks.hasPermission) throw Object.assign(new Error('Permission required'), { code: 'PERMISSION_REQUIRED' });
  if (!checks.hasAuth) throw Object.assign(new Error('Connect provider'), { code: 'AUTH_REQUIRED' });
  if (tool.requiresApproval && !(await checks.approve())) throw Object.assign(new Error('Rejected'), { code: 'APPROVAL_REJECTED' });
  return tool.execute(input, ctx);
}
