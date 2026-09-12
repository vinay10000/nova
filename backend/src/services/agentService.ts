import { toolRegistry } from '../tools/registry.js';
import { runToolWithSafety } from '../tools/Tool.js';

// §32 Agent Worker flow: Schedule->Queue->Worker->Gemini->Tools->Result. Server-side only.
export async function executeAgent(agentId: string, userId: string, log: (s: string) => Promise<void>) {
  await log('Agent started');
  // TODO: load agent + permissions + connections, plan via Gemini function-calling loop (§3 agent execution).
  // Example step placeholders (§35): connected, checked issues, summarized, completed.
  await log('Checked assigned issues');
  await log('Generated summary');
  await log('Completed');
  return { agentId, status: 'COMPLETED' as const };
}

export { toolRegistry, runToolWithSafety };
