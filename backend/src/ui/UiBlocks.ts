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
const metricSchema = z.object({
  label: z.string().min(1).max(80),
  value: z.string().trim().min(1).max(120),
  change: z.string().max(80).optional(),
});
const progressItemSchema = z.object({
  label: z.string().min(1).max(80),
  /** Percent 0–100. The renderer treats >1 as percent and ≤1 as fraction only when every value in the block is ≤1. */
  value: z.number().finite().min(0).max(100),
  detail: z.string().max(80).optional(),
});
const stepSchema = z.object({
  label: z.string().min(1).max(120),
  status: z.enum(['done', 'active', 'todo', 'error']),
  detail: z.string().max(300).optional(),
  value: z.string().max(80).optional(),
});
const compareRowSchema = z.object({
  label: z.string().min(1).max(80),
  left: z.string().max(160).default(''),
  right: z.string().max(160).default(''),
});
const chartPointSchema = z.object({
  label: z.string().min(1).max(24),
  value: z.number().finite(),
});
const linkSchema = z.object({
  title: z.string().min(1).max(120),
  url: z.string().max(500).regex(/^https?:\/\//i, 'link_must_be_http'),
  secondary: z.string().max(200).optional(),
});

const blockBase = z.object({
  id: z.string().min(1).max(80),
  title: z.string().max(120).optional(),
  actions: z.array(actionSchema).max(6).optional(),
});

export const uiBlockSchema = z.discriminatedUnion('type', [
  blockBase.extend({
    type: z.literal('summary'),
    body: z.string().min(1).max(2_000),
    metadata: z.array(itemSchema).max(8).optional(),
  }),
  blockBase.extend({ type: z.literal('metrics'), metrics: z.array(metricSchema).min(1).max(12) }),
  blockBase.extend({ type: z.literal('list'), items: z.array(itemSchema).min(1).max(30) }),
  blockBase.extend({
    type: z.literal('table'),
    columns: z.array(z.string().min(1).max(80)).min(1).max(8),
    rows: z.array(z.array(z.string().max(500)).max(8)).max(40),
  }),
  blockBase.extend({ type: z.literal('progress'), items: z.array(progressItemSchema).min(1).max(8) }),
  blockBase.extend({ type: z.literal('timeline'), steps: z.array(stepSchema).min(1).max(12) }),
  blockBase.extend({
    type: z.literal('comparison'),
    leftLabel: z.string().min(1).max(40),
    rightLabel: z.string().min(1).max(40),
    rows: z.array(compareRowSchema).min(1).max(10),
    winner: z.enum(['left', 'right']).optional(),
  }),
  blockBase.extend({
    type: z.literal('code'),
    code: z.string().min(1).max(4_000),
    language: z.string().max(24).optional(),
  }),
  blockBase.extend({
    type: z.literal('chart'),
    points: z.array(chartPointSchema).min(1).max(12),
    unit: z.string().max(16).optional(),
  }),
  blockBase.extend({ type: z.literal('links'), links: z.array(linkSchema).min(1).max(12) }),
]);

export const uiBlocksSchema = z.array(uiBlockSchema).max(8).superRefine((blocks, ctx) => {
  if (JSON.stringify(blocks).length > 32_000) ctx.addIssue({ code: 'custom', message: 'ui_payload_too_large' });
});
export type UiBlock = z.infer<typeof uiBlockSchema>;

/**
 * Table rows from a model may run short or long against the header. Pad with
 * the em dash and truncate past the header so every cell stays on its grid —
 * never reject (the turn would stall on a retry) and never render ragged.
 */
function normalizeTable(block: Extract<UiBlock, { type: 'table' }>): Extract<UiBlock, { type: 'table' }> {
  const width = block.columns.length;
  return {
    ...block,
    rows: block.rows.map((row) => {
      const cells = row.slice(0, width);
      while (cells.length < width) cells.push('—');
      return cells;
    }),
  };
}

/**
 * Every card carries a copy action so the header is never a dead end; models
 * that asked for one keep theirs, models that did not get the default.
 */
function ensureCopyAction<T extends UiBlock>(block: T): T {
  if (block.actions?.some((a) => a.type === 'copy')) return block;
  return { ...block, actions: [{ type: 'copy', label: 'Copy' }, ...(block.actions ?? [])] };
}

function finalizeBlocks(blocks: UiBlock[]): UiBlock[] {
  return blocks.map((b) => ensureCopyAction(b.type === 'table' ? normalizeTable(b) : b));
}

const PRESENTABLE_READ_TOOLS = new Set([
  'github_list_issues', 'github_list_repositories', 'github_list_pull_requests', 'github_get_notifications',
  'gmail_list_messages', 'calendar_list_events', 'drive_list', 'leetcode_get_solved',
  'browser_open', 'browser_scrape',
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
  // Two calls to the same tool in one turn must not collide on id — the client
  // de-duplicates by id and would silently drop the second block.
  const nonce = `${Date.now() % 1_000_000}-${Math.floor(Math.random() * 1_000)}`;
  if (Array.isArray(payload)) {
    const values = payload.slice(0, 30);
    const items = values.map((value) => ({ label: text(value, jsonPreview(value, 200)) }));
    return items.length ? finalizeBlocks(uiBlocksSchema.parse([{ type: 'list', id: `${toolId}-list-${nonce}`, title: 'Live data', items }])) : [];
  }
  const record = payload as Record<string, unknown>;
  if (toolId === 'browser_open' && typeof record.text === 'string' && record.text.trim()) {
    return finalizeBlocks(uiBlocksSchema.parse([{ type: 'summary', id: `${toolId}-summary-${nonce}`, title: 'Web research', body: record.text.slice(0, 2_000) }]));
  }
  // browser_scrape result { url, data: { selector: [texts] } } → one list per selector.
  if (toolId === 'browser_scrape' && record.data && typeof record.data === 'object' && !Array.isArray(record.data)) {
    const blocks: UiBlock[] = [];
    for (const [selector, values] of Object.entries(record.data as Record<string, unknown>)) {
      if (!Array.isArray(values)) continue;
      const items = values.map((v) => ({ label: text(v) })).filter((i) => i.label).slice(0, 30);
      if (items.length) blocks.push({ type: 'list', id: `${toolId}-${selector.slice(0, 40)}-${nonce}`, title: selector, items });
      if (blocks.length >= 4) break;
    }
    return blocks.length ? finalizeBlocks(uiBlocksSchema.parse(blocks)) : [];
  }
  const arrayEntry = Object.entries(record).find(([, value]) => Array.isArray(value));
  if (arrayEntry) {
    const rawValues = (arrayEntry[1] as unknown[]).slice(0, 40);
    if (rawValues.length && rawValues.every((value) => typeof value !== 'object' || value === null)) {
      return finalizeBlocks(uiBlocksSchema.parse([{ type: 'list', id: `${toolId}-list-${nonce}`, title: arrayEntry[0], items: rawValues.map((value) => ({ label: text(value) })) }]));
    }
    const values = rawValues.filter((v) => v && typeof v === 'object') as Record<string, unknown>[];
    if (!values.length) return [];
    const columns = Object.keys(values[0]).filter((key) => !['url', 'html_url', 'avatar_url'].includes(key)).slice(0, 8);
    const rows = values.map((value) => columns.map((column) => text(value[column], '—')));
    return finalizeBlocks(uiBlocksSchema.parse([{ type: 'table', id: `${toolId}-data-${nonce}`, title: arrayEntry[0], columns, rows }]));
  }
  const numeric = Object.entries(record).filter(([, value]) => typeof value === 'number').slice(0, 12);
  if (numeric.length) {
    return finalizeBlocks(uiBlocksSchema.parse([{ type: 'metrics', id: `${toolId}-metrics-${nonce}`, metrics: numeric.map(([label, value]) => ({ label, value: text(value) })) }]));
  }
  const summary = Object.entries(record).slice(0, 8).map(([label, value]) => ({ label, value: text(value, jsonPreview(value)) }));
  return summary.length ? finalizeBlocks(uiBlocksSchema.parse([{ type: 'summary', id: `${toolId}-summary-${nonce}`, title: toolId.replaceAll('_', ' '), body: 'Live data from Nova', metadata: summary }])) : [];
}

export function validateUiBlocks(value: unknown): UiBlock[] { return finalizeBlocks(uiBlocksSchema.parse(value)); }

// ---- Chat-side presentation tool (§45 generative UI) -------------------------------
// The model cannot stream arbitrary components; it calls present_ui with TYPED params,
// the server builds blocks through the same zod schema tool results use, and chatService
// emits them as a real `ui` SSE chunk. Raw JSON pasted into prose stays invisible to the
// renderer by design — this tool is the ONLY way UI reaches the screen, so a model that
// wants cards must call it instead of printing JSON text.

/** Gemini function parameters mirror the block schema (flat, per block type). */
export const presentUiParamsSchema = {
  type: 'object',
  properties: {
    blocks: {
      type: 'array',
      description: '1-3 UI blocks to render under the reply — one card per coherent unit of structured data',
      items: {
        type: 'object',
        properties: {
          type: {
            type: 'string',
            enum: ['summary', 'metrics', 'list', 'table', 'progress', 'timeline', 'comparison', 'code', 'chart', 'links'],
            description:
              'summary: prose + key/value facts · metrics: big numbers with signed deltas · list: scannable items · ' +
              'table: rows × columns · progress: percent bars 0-100 · timeline: status steps · ' +
              'comparison: two labeled columns · code: source listing · chart: bar chart points · links: openable URLs',
          },
          title: { type: 'string', description: 'Short noun phrase, sentence case, about 6 words (e.g. "Sprint overview")' },
          body: { type: 'string', description: 'summary block: concise factual prose under 600 characters; never apologies or refusals' },
          metadata: { type: 'array', items: { type: 'object', properties: { label: { type: 'string' }, value: { type: 'string' }, secondary: { type: 'string' } }, required: ['label'] }, description: 'summary: key/value fact rows (max 8)' },
          metrics: { type: 'array', items: { type: 'object', properties: { label: { type: 'string', description: 'Short metric name' }, value: { type: 'string', description: 'Non-empty display value with unit' }, change: { type: 'string', description: 'Signed delta such as +12% or -3' } }, required: ['label', 'value'] } },
          items: { type: 'array', items: { type: 'object', properties: { label: { type: 'string', description: 'Primary line' }, secondary: { type: 'string', description: 'Supporting line' }, value: { type: 'string', description: 'Right-aligned datum' } }, required: ['label'] }, description: 'list block rows' },
          columns: { type: 'array', items: { type: 'string' }, description: 'Table column headers, left to right' },
          rows: { type: 'array', items: { type: 'array', items: { type: 'string' } }, description: 'One array per row, same length as columns (short rows are padded)' },
          progress: { type: 'array', items: { type: 'object', properties: { label: { type: 'string' }, value: { type: 'number', description: 'Percent 0-100' }, detail: { type: 'string' } }, required: ['label', 'value'] }, description: 'progress block bars (max 8)' },
          steps: { type: 'array', items: { type: 'object', properties: { label: { type: 'string' }, status: { type: 'string', enum: ['done', 'active', 'todo', 'error'] }, detail: { type: 'string' }, value: { type: 'string', description: 'Right meta such as a timestamp' } }, required: ['label', 'status'] }, description: 'timeline block steps (max 12)' },
          leftLabel: { type: 'string', description: 'comparison: left column heading' },
          rightLabel: { type: 'string', description: 'comparison: right column heading' },
          winner: { type: 'string', enum: ['left', 'right'], description: 'comparison: optional preferred side' },
          code: { type: 'string', description: 'code block: source text up to 4000 chars' },
          language: { type: 'string', description: 'code block: language tag e.g. ts, py' },
          points: { type: 'array', items: { type: 'object', properties: { label: { type: 'string' }, value: { type: 'number' } }, required: ['label', 'value'] }, description: 'chart block: 1-12 bars' },
          unit: { type: 'string', description: 'chart block: unit shown in the header e.g. ms' },
          links: { type: 'array', items: { type: 'object', properties: { title: { type: 'string' }, url: { type: 'string', description: 'https:// URL' }, secondary: { type: 'string', description: 'Domain or byline' } }, required: ['title', 'url'] }, description: 'links block rows (max 12)' },
        },
        required: ['type'],
      },
    },
  },
  required: ['blocks'],
} as const;

/**
 * Validate + normalize model-supplied present_ui params. Returns null on invalid input
 * (caller turns that into an error result the model can correct from — never render
 * unvalidated payloads).
 */
export function blocksFromPresentUiInput(input: unknown): UiBlock[] | null {
  const raw = (input as { blocks?: unknown })?.blocks;
  if (!Array.isArray(raw) || !raw.length) return null;
  const withIds = raw.slice(0, 3).map((b, i) => ({
    ...(b as Record<string, unknown>),
    id: `present-${i}-${Date.now() % 100_000}`, // server id wins — a model-supplied id could collide and drop a card
  }));
  const parsed = uiBlocksSchema.safeParse(withIds);
  return parsed.success ? finalizeBlocks(parsed.data) : null;
}
