import type { PrismaClient } from '@prisma/client';
import type { AIProvider } from '../ai/AIProvider.js';
import { MODELS } from '../ai/models.js';
import { runAgentLoop, stepMeta } from '../agent/runtime.js';
import { toolRegistry } from '../tools/registry.js';

// §32 execution, server-side only: Schedule->Queue->Worker->Gemini->Tools->Result.
// Phase 3 runs inline in the Run Now request (bounded by RUN_BUDGET_MS);
// background=true detaches the run (10-minute budget, DB-polled cancel).
// Phase 5 moves this behind PostgresQueue + a worker process.
import { cleanupSandboxes } from '../sandbox/CodeSandboxProvider.js';

type AgentRow = NonNullable<Awaited<ReturnType<PrismaClient['agent']['findFirst']>>>;

/** Core run body, shared by inline and background paths. Owns the Execution row lifecycle. */
async function runPreparedExecution(
  db: PrismaClient,
  ai: AIProvider,
  agent: AgentRow,
  executionId: string,
  opts: { userId: string; budgetMs?: number; background?: boolean },
): Promise<{ status: string; output?: string }> {
  const log = (s: Parameters<typeof stepMeta>[0]) =>
    db.executionStep.create({ data: { executionId, label: s.label.slice(0, 500), metadata: stepMeta(s) } });
  await db.execution.update({ where: { id: executionId }, data: { status: 'RUNNING' } });

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
      ...(opts.budgetMs ? { budgetMs: opts.budgetMs } : {}),
      // Background runs honor the /cancel route: poll the row between turns.
      ...(opts.background
        ? { isCancelled: async () => (await db.execution.findUnique({ where: { id: executionId }, select: { status: true } }))?.status === 'CANCELLED' }
        : {}),
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

    if (result.suspended) {
      await db.approval.create({
        data: { userId: opts.userId, executionId, toolId: result.suspended.toolId, payload: result.suspended.payload as object ?? {} },
      });
      await db.execution.update({ where: { id: executionId }, data: { status: 'WAITING_FOR_APPROVAL', output: result.output || null } });
      return { status: 'WAITING_FOR_APPROVAL', output: result.output };
    }
    await db.execution.update({
      where: { id: executionId },
      // A turn that yields neither text nor tool calls is a model/API anomaly,
      // not a successful run — fail loudly instead of storing an empty COMPLETED.
      data: result.output
        ? { status: 'COMPLETED', completedAt: new Date(), output: result.output }
        : { status: 'FAILED', completedAt: new Date(), error: 'empty_model_response' },
    });
    return result.output ? { status: 'COMPLETED', output: result.output } : { status: 'FAILED' };
  } catch (err) {
    const message = err instanceof Error ? err.message : 'run_failed';
    // A user cancel aborts the loop with 'cancelled' — persist CANCELLED, not FAILED.
    const cancelled = message === 'cancelled';
    await db.execution.update({
      where: { id: executionId },
      data: cancelled
        ? { status: 'CANCELLED', completedAt: new Date() }
        : { status: 'FAILED', completedAt: new Date(), error: message.slice(0, 1000) },
    });
    return { status: cancelled ? 'CANCELLED' : 'FAILED' };
  } finally {
    // Sandboxes the agent forgot to destroy die with the run — never leak paid E2B quota.
    const killed = await cleanupSandboxes(agent.id);
    if (killed.length) await log({ label: `Cleaned up ${killed.length} sandbox(es) left open by the run` });
  }
}

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
  const result = await runPreparedExecution(db, ai, agent, execution.id, { userId: opts.userId });
  return { executionId: execution.id, ...result };
}

/**
 * §33/§56 background=true: create the Execution, detach the run, return QUEUED
 * immediately. The run gets a 10-minute budget and DB-polled cancellation.
 * ponytail: on serverless (Vercel) the lambda is frozen once the response returns, so
 * background runs only truly outlive the request on a long-lived host (self-host /
 * Railway / a Phase 5 worker). The contract stays honest: QUEUED/RUNNING until the run
 * really finishes, and the client polls /v1/executions — never fake progress.
 */
export async function runAgentInBackground(
  db: PrismaClient,
  ai: AIProvider,
  opts: { userId: string; agentId: string; trigger: string },
): Promise<{ executionId: string; status: string }> {
  const agent = await db.agent.findFirst({ where: { id: opts.agentId, userId: opts.userId } });
  if (!agent) throw new Error('not_found');
  const execution = await db.execution.create({
    data: { agentId: agent.id, userId: opts.userId, trigger: opts.trigger, status: 'QUEUED' },
    select: { id: true },
  });
  void runPreparedExecution(db, ai, agent, execution.id, { userId: opts.userId, budgetMs: 600_000, background: true })
    .catch(() => {}); // runPreparedExecution persists its own failure state
  return { executionId: execution.id, status: 'QUEUED' };
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
