import type { PrismaClient } from '@prisma/client';
import type { AIProvider } from '../ai/AIProvider.js';
import { MODELS } from '../ai/GeminiProvider.js';
import { runAgentLoop, stepMeta } from '../agent/runtime.js';
import { toolRegistry } from '../tools/registry.js';

// §32 execution, server-side only: Schedule->Queue->Worker->Gemini->Tools->Result.
// Phase 3 runs inline in the Run Now request (bounded by RUN_BUDGET_MS);
// Phase 5 moves this behind PostgresQueue + a worker process.
export async function executeAgent(
  db: PrismaClient,
  ai: AIProvider,
  opts: { userId: string; agentId: string; trigger: string },
): Promise<{ executionId: string; status: string; output?: string }> {
  const agent = await db.agent.findFirst({ where: { id: opts.agentId, userId: opts.userId } });
  if (!agent) throw new Error('not_found');

  const execution = await db.execution.create({
    data: { agentId: agent.id, userId: opts.userId, trigger: opts.trigger, status: 'QUEUED' },
    select: { id: true },
  });
  const log = (s: Parameters<typeof stepMeta>[0]) =>
    db.executionStep.create({ data: { executionId: execution.id, label: s.label.slice(0, 500), metadata: stepMeta(s) } });
  await db.execution.update({ where: { id: execution.id }, data: { status: 'RUNNING' } });

  try {
    const base = {
      ai,
      db,
      agent: {
        id: agent.id,
        userId: agent.userId,
        goal: agent.goal,
        instructions: agent.instructions,
        tools: agent.tools,
        permissions: agent.permissions,
      },
      onStep: async (s: Parameters<typeof stepMeta>[0]) => { await log(s); },
    };
    let result;
    try {
      result = await runAgentLoop(base);
    } catch (err) {
      // Free-tier quota is per-model: one retry on the cheap model before failing.
      if (!(err instanceof Error && /429|quota|rate/i.test(err.message))) throw err;
      await log({ label: 'Primary model is rate-limited — retrying on fallback model' });
      result = await runAgentLoop({ ...base, model: MODELS.cheap });
    }

    if (result.suspended) {      await db.approval.create({
        data: { userId: opts.userId, executionId: execution.id, toolId: result.suspended.toolId, payload: result.suspended.payload as object ?? {} },
      });
      await db.execution.update({ where: { id: execution.id }, data: { status: 'WAITING_FOR_APPROVAL', output: result.output || null } });
      return { executionId: execution.id, status: 'WAITING_FOR_APPROVAL', output: result.output };
    }
    await db.execution.update({
      where: { id: execution.id },
      // A turn that yields neither text nor tool calls is a model/API anomaly,
      // not a successful run — fail loudly instead of storing an empty COMPLETED.
      data: result.output
        ? { status: 'COMPLETED', completedAt: new Date(), output: result.output }
        : { status: 'FAILED', completedAt: new Date(), error: 'empty_model_response' },
    });
    if (!result.output) return { executionId: execution.id, status: 'FAILED' };
    return { executionId: execution.id, status: 'COMPLETED', output: result.output };
  } catch (err) {
    const message = err instanceof Error ? err.message : 'run_failed';
    await db.execution.update({ where: { id: execution.id }, data: { status: 'FAILED', completedAt: new Date(), error: message.slice(0, 1000) } });
    return { executionId: execution.id, status: 'FAILED' };
  }
}

/** §13 review-before-activate: derive least-privilege scopes from the chosen tools. Unknown tool id = 400. */
export function scopesForTools(toolIds: unknown): string[] | null {
  if (!Array.isArray(toolIds)) return null;
  const scopes: string[] = [];
  for (const id of toolIds) {
    const tool = toolRegistry.get(String(id));
    if (!tool) return null;
    scopes.push(tool.scope);
  }
  return [...new Set(scopes)];
}

export { toolRegistry };
export { runToolWithSafety } from '../tools/Tool.js';
