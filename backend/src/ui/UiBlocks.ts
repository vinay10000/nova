import { z } from 'zod';

const actionSchema = z.object({
  type: z.enum(['copy', 'expand', 'filter', 'open']),
  label: z.string().min(1).max(60),
  value: z.string().max(2_000).optional(),
});
const itemSchema = z.object({
  label: z.string().min(1).max(200),
  secondary: z.string().max(500).optional(),
  value: z.string().max(2_000).optional(),
});
const blockBase = z.object({
  id: z.string().min(1).max(80),
  title: z.string().max(120).optional(),
  actions: z.array(actionSchema).max(6).optional(),
});

export const uiBlockSchema = z.discriminatedUnion('type', [
  blockBase.extend({ type: z.literal('summary'), body: z.string().min(1).max(2_000), metadata: z.array(itemSchema).max(8).optional() }),
  blockBase.extend({ type: z.literal('metrics'), metrics: z.array(z.object({ label: z.string().min(1).max(80), value: z.string().max(120), change: z.string().max(80).optional() })).min(1).max(12) }),
  blockBase.extend({ type: z.literal('list'), items: z.array(itemSchema).min(1).max(30) }),
  blockBase.extend({ type: z.literal('table'), columns: z.array(z.string().min(1).max(80)).min(1).max(8), rows: z.array(z.array(z.string().max(500)).max(8)).max(40) }),
]);
export const uiBlocksSchema = z.array(uiBlockSchema).max(8).superRefine((blocks, ctx) => {
  if (JSON.stringify(blocks).length > 32_000) ctx.addIssue({ code: 'custom', message: 'ui_payload_too_large' });
});
export type UiBlock = z.infer<typeof uiBlockSchema>;

const PRESENTABLE_READ_TOOLS = new Set([
  'github_list_issues', 'github_list_repositories', 'github_list_pull_requests', 'github_get_notifications',
  'gmail_list_messages', 'calendar_list_events', 'drive_list', 'leetcode_get_solved',
  'browser_open',
]);
const text = (value: unknown, fallback = ''): string =>
  typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean' ? String(value) : fallback;
const jsonPreview = (value: unknown, max = 500): string => {
  try { return JSON.stringify(value)?.slice(0, max) ?? ''; } catch { return ''; }
};

function safeJson(result: string): unknown { try { return JSON.parse(result); } catch { return null; } }

/** Creates presentation data only from a bounded, allowlisted read-tool result. */
export function uiBlocksFromToolResult(toolId: string, result: string): UiBlock[] {
  if (!PRESENTABLE_READ_TOOLS.has(toolId)) return [];
  const payload = safeJson(result);
  if (!payload || typeof payload !== 'object') return [];
  if (Array.isArray(payload)) {
    const values = payload.slice(0, 30);
    const items = values.map((value) => ({ label: text(value, jsonPreview(value, 200)) }));
    return items.length ? uiBlocksSchema.parse([{ type: 'list', id: `${toolId}-list`, title: 'Live data', items }]) : [];
  }
  const record = payload as Record<string, unknown>;
  if (toolId === 'browser_open' && typeof record.text === 'string' && record.text.trim()) {
    return uiBlocksSchema.parse([{ type: 'summary', id: `${toolId}-summary`, title: 'Web research', body: record.text.slice(0, 2_000) }]);
  }
  const arrayEntry = Object.entries(record).find(([, value]) => Array.isArray(value));
  if (arrayEntry) {
    const rawValues = (arrayEntry[1] as unknown[]).slice(0, 40);
    if (rawValues.length && rawValues.every((value) => typeof value !== 'object' || value === null)) {
      return uiBlocksSchema.parse([{ type: 'list', id: `${toolId}-list`, title: arrayEntry[0], items: rawValues.map((value) => ({ label: text(value) })) }]);
    }
    const values = rawValues.filter((v) => v && typeof v === 'object') as Record<string, unknown>[];
    if (!values.length) return [];
    const columns = Object.keys(values[0]).filter((key) => !['url', 'html_url', 'avatar_url'].includes(key)).slice(0, 8);
    const rows = values.map((value) => columns.map((column) => text(value[column], '—')));
    return uiBlocksSchema.parse([{ type: 'table', id: `${toolId}-data`, title: arrayEntry[0], columns, rows }]);
  }
  const numeric = Object.entries(record).filter(([, value]) => typeof value === 'number').slice(0, 12);
  if (numeric.length) {
    return uiBlocksSchema.parse([{ type: 'metrics', id: `${toolId}-metrics`, metrics: numeric.map(([label, value]) => ({ label, value: text(value) })) }]);
  }
  const summary = Object.entries(record).slice(0, 8).map(([label, value]) => ({ label, value: text(value, jsonPreview(value)) }));
  return summary.length ? uiBlocksSchema.parse([{ type: 'summary', id: `${toolId}-summary`, title: toolId.replaceAll('_', ' '), body: 'Live data from Nova', metadata: summary }]) : [];
}

export function validateUiBlocks(value: unknown): UiBlock[] { return uiBlocksSchema.parse(value); }
