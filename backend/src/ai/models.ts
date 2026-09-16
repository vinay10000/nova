// Single model registry (F1 fix). Previously MODELS lived in OpenAIProvider.ts
// while chatService.ts imported it from there and index.ts/agentService.ts from
// GeminiProvider.ts — two copies drifting apart, risking 404s on stale ids.
export const MODELS = {
  chat: process.env.MODEL_CHAT ?? 'gemini-3.1-flash-lite',
  cheap: process.env.MODEL_CHAT ?? 'gemini-3.1-flash-lite',
  vision: process.env.MODEL_VISION ?? 'gemini-3.5-flash-lite',
  tools: process.env.MODEL_TOOLS ?? 'gemini-3.5-flash-lite',
} as const;
