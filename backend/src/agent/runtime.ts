import type { PrismaClient } from '@prisma/client';
import type { AIProvider } from '../ai/AIProvider.js';
import type { FunctionResultInput } from '../ai/GeminiProvider.js';
import { toolRegistry, toToolDefs, isServerKeyedTool } from '../tools/registry.js';
import { runToolWithSafety, type ToolOutcome } from '../tools/Tool.js';

// §52 agent runtime: the explicit tool loop (no auto-function-calling — §47 gates
// sit between the model's request and execution). Bounded by step cap + wall clock
// so a runaway agent cannot bill forever (§46 execution limits). Fits Vercel 60s.
export const MAX_TOOL_STEPS = 10;
export const RUN_BUDGET_MS = 55_000;

export interface AgentRef {
  id: string;
  userId: string;
  goal: string;
  instructions: string;
  tools: unknown;
  permissions: unknown;
}

export interface RuntimeStep {
  label: string;
  toolId?: string;
  outcome?: string;
}

export interface RuntimeResult {
  output: string;
  interactionId?: string;
  suspended?: { toolId: string; payload: unknown };
  steps: RuntimeStep[];
}

/**
 * §3.3 gate: resolves permission + authorization from the DB itself, then calls
 * the single choke point. The caller never supplies its own verdict.
 */
export async function gateToolCall(
  db: PrismaClient,
  agent: AgentRef,
  toolId: string,
  args: unknown,
): Promise<ToolOutcome> {
  const tool = toolRegistry.get(toolId);
  const toolIds = Array.isArray(agent.tools) ? (agent.tools as unknown[]) : [];
  const scopes = Array.isArray(agent.permissions) ? (agent.permissions as string[]) : [];
  // Server-keyed tools (information retrieval, §40; LeetCode, §18) run on the
  // backend's own key — no per-user OAuth exists or is needed. Everything else
  // needs a live Connection; 'expired' rows are deliberately NOT accepted here
  // so a revoked token denies up front with a reconnect hint instead of failing
  // mid-run.
  const serverKeyed = isServerKeyedTool(toolId);
  const provider = toolId.split('_')[0]!;
  const conn = serverKeyed || !tool
    ? null
    : await db.connection.findFirst({ where: { userId: agent.userId, provider, status: 'connected' } });
  return runToolWithSafety(tool, toolId, args, { userId: agent.userId, agentId: agent.id, permissions: scopes, db }, {
    hasPermission: toolIds.includes(toolId),
    hasAuth: serverKeyed || !!conn,
  });
}

/** §35: step metadata allowlist — labels and ids only, never args/results/secrets. */
export function stepMeta(step: RuntimeStep): Record<string, string> {
  return { label: step.label.slice(0, 500), ...(step.toolId ? { toolId: step.toolId } : {}), ...(step.outcome ? { outcome: step.outcome } : {}) };
}

function outcomeToResult(o: ToolOutcome): { text: string; isError: boolean } {
  switch (o.status) {
    case 'ok': return { text: JSON.stringify(o.result).slice(0, 8000), isError: false };
    case 'denied':
      return { text: JSON.stringify({ denied: o.code, message: o.message, ...(o.connect ? { connect: o.connect } : {}) }), isError: true };
    case 'rejected': return { text: JSON.stringify({ denied: 'approval_rejected', toolId: o.toolId }), isError: true };
    case 'error': return { text: JSON.stringify({ error: o.message }), isError: true };
    case 'approval_required': return { text: '', isError: false }; // suspends; never fed back
  }
}

/** Render function results as plain text for a fresh (unchained) follow-up turn. */
function resultsToText(results: FunctionResultInput[] | undefined): string {
  if (!results?.length) return '';
  return '\n' + results.map((r) => `Tool ${r.name} returned:\n${r.result.slice(0, 8000)}`).join('\n') + '\nContinue working toward the goal.';
}

export async function runAgentLoop(opts: {
  ai: AIProvider;
  db: PrismaClient;
  agent: AgentRef;
  signal?: AbortSignal;
  /** Model override — the service retries here on quota exhaustion. */
  model?: string;
  onStep?: (step: RuntimeStep) => Promise<void> | void;
}): Promise<RuntimeResult> {
  const { ai, db, agent } = opts;
  const steps: RuntimeStep[] = [];
  const step = async (s: RuntimeStep) => { steps.push(s); await opts.onStep?.(s); };

  const tools = (Array.isArray(agent.tools) ? agent.tools : [])
    .map((id) => toolRegistry.get(String(id)))
    .filter((t): t is NonNullable<typeof t> => !!t);
  const defs = toToolDefs(tools);

  const ac = new AbortController();
  const timer = setTimeout(() => ac.abort(), RUN_BUDGET_MS);
  const aborted = () => ac.signal.aborted || opts.signal?.aborted;
  opts.signal?.addEventListener('abort', () => ac.abort());
  await step({ label: 'Agent started' });

  try {
    let previousInteractionId: string | undefined;
    let results: FunctionResultInput[] | undefined;
    let output = '';
    // Exa web search is a first-class agent tool: the model must reach for it
    // instead of guessing whenever facts may be current or external.
    const basePrompt = `Goal: ${agent.goal}\nInstructions: ${agent.instructions}\nFor current or external facts, use the web_search tool (Exa) rather than guessing.`;
    // Accumulates tool results as plain text when chaining is rejected — the
    // next turn re-sends goal + results in one fresh user message.
    let freshText = '';

    for (let turn = 0; turn < MAX_TOOL_STEPS + 1; turn++) {
      if (aborted()) throw new Error('cancelled');
      const calls: { callId: string; toolId: string; args: unknown }[] = [];
      let text = '';
      const chained = !!results?.length && !freshText;

      try {
        const stream = ai.streamChat(
          !chained ? [{ role: 'user', content: `${basePrompt}${freshText}` }] : [],
          {
            tools: defs,
            ...(opts.model ? { model: opts.model } : {}),
            ...(chained && previousInteractionId ? { previous_interaction_id: previousInteractionId } : {}),
            ...(chained ? { functionResults: results } : {}),
            signal: ac.signal,
          } as Parameters<AIProvider['streamChat']>[1],
        );
        for await (const chunk of stream) {
          if (aborted()) break;
          if (chunk.type === 'token') text += chunk.text;
          else if (chunk.type === 'tool_call' && chunk.toolId) {
            calls.push({ callId: chunk.callId ?? `${turn}:${calls.length}`, toolId: chunk.toolId, args: chunk.args });
          } else if (chunk.type === 'done') {
            previousInteractionId = chunk.interactionId ?? previousInteractionId;
          }
        }
      } catch (err) {
        // The API rejects a function_result turn unless the chained interaction
        // ended ON the matching function call (e.g. the model chatted after the
        // call and completed). Fall back once per turn: same results as plain
        // text in a fresh turn — chaining is an optimization, not a requirement.
        if (chained && err instanceof Error && /immediately after|invalid_request/i.test(err.message)) {
          await step({ label: 'Chained follow-up rejected — continuing with results as text' });
          freshText += resultsToText(results);
          results = [];
          turn--; // retry this turn fresh; doesn't consume the step budget
          continue;
        }
        throw err;
      }
      freshText = '';
      output += text;
      results = [];

      if (!calls.length) return { output: output.trim(), interactionId: previousInteractionId, steps };
      if (turn === MAX_TOOL_STEPS) {
        await step({ label: 'Step budget reached — stopping' });
        return { output: (output || 'Stopped: step budget reached.').trim(), interactionId: previousInteractionId, steps };
      }

      for (const c of calls) {
        if (aborted()) break;
        await step({ label: `Calling ${c.toolId}`, toolId: c.toolId });
        const outcome = await gateToolCall(db, agent, c.toolId, c.args);
        if (outcome.status === 'approval_required') {
          await step({ label: `Waiting for approval: ${c.toolId}`, toolId: c.toolId, outcome: 'approval_required' });
          return { output: output.trim(), interactionId: previousInteractionId, steps, suspended: { toolId: c.toolId, payload: c.args } };
        }
        const status = outcome.status === 'ok' ? 'ok' : outcome.status;
        await step({ label: `${c.toolId}: ${status}`, toolId: c.toolId, outcome: status });
        const fr = outcomeToResult(outcome);
        results.push({ type: 'function_result', name: c.toolId, call_id: c.callId, result: fr.text, ...(fr.isError ? { is_error: true } : {}) });
      }
    }
    return { output: output.trim(), interactionId: previousInteractionId, steps };
  } finally {
    clearTimeout(timer);
  }
}
