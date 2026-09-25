// Single model registry (F1 fix). Previously MODELS lived in OpenAIProvider.ts
// while chatService.ts imported it from there and index.ts/agentService.ts from
// GeminiProvider.ts — two copies drifting apart, risking 404s on stale ids.
// Deployment variables survive provider migrations. Keep overrides deliberately
// narrow so an old non-Gemini router id cannot reach Gemini and fail a whole
// chat stream.
const DEFAULT_CHAT = 'gemini-3.1-flash-lite';
const DEFAULT_VISION = 'gemini-3.5-flash-lite';
const ALLOWED_MODELS = new Set([DEFAULT_CHAT, DEFAULT_VISION]);

function configuredModel(name: string | undefined, fallback: string): string {
  return name && ALLOWED_MODELS.has(name) ? name : fallback;
}

export const MODELS = {
  chat: configuredModel(process.env.MODEL_CHAT, DEFAULT_CHAT),
  cheap: configuredModel(process.env.MODEL_CHEAP, DEFAULT_CHAT),
  vision: configuredModel(process.env.MODEL_VISION, DEFAULT_VISION),
  tools: configuredModel(process.env.MODEL_TOOLS, DEFAULT_VISION),
} as const;

export const MODEL_EFFORTS = ['minimal', 'low', 'medium', 'high'] as const;
export type ModelEffort = (typeof MODEL_EFFORTS)[number];

export interface SelectableModel {
  id: string;
  label: string;
  description: string;
  efforts: ModelEffort[];
}

const MODEL_DESCRIPTIONS: Record<string, string> = {
  [DEFAULT_CHAT]: 'Fast everyday answers and quick follow-ups.',
  [DEFAULT_VISION]: 'Images, tool workflows, and longer tasks.',
};

export function isModelEffort(value: unknown): value is ModelEffort {
  return typeof value === 'string' && (MODEL_EFFORTS as readonly string[]).includes(value);
}

export function resolveModelEffort(value: unknown): ModelEffort | undefined {
  return isModelEffort(value) ? value : undefined;
}

export function modelLabel(id: string): string {
  return id.replace(/^gemini-/, '').replace(/-lite$/, '').replace(/-flash$/, ' Flash');
}

export function modelCatalog(ids: Iterable<string> = [MODELS.chat, MODELS.vision]): SelectableModel[] {
  return [...new Set(ids)].map((id) => ({
    id,
    label: modelLabel(id),
    description: MODEL_DESCRIPTIONS[id] ?? 'Nova model.',
    efforts: [...MODEL_EFFORTS],
  }));
}
