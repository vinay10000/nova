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
