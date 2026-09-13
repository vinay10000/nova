// §2 — AI provider abstraction. Backend owns keys; APK never sees them (§46).
export interface ChatMessage {
  role: 'user' | 'model' | 'system';
  content: string;
}

/** §9 multimodal: inline image/document block for the Gemini Interactions input. */
export interface InlinePart {
  mime: string;
  data: string; // base64
}

// One chunk shape for chat AND agent runs, so the client has a single streaming path.
export type StreamChunk =
  | { type: 'token'; text: string }
  | { type: 'tool_call'; toolId: string; callId?: string; args?: unknown }
  | { type: 'step'; label: string }
  | { type: 'approval'; approvalId: string; toolId: string; payload: unknown }
  | { type: 'error'; code: string; message?: string; retryable?: boolean }
  | { type: 'done'; interactionId?: string };

export interface ToolDef {
  name: string;
  description: string;
  parameters: unknown;
}

export interface AIProvider {
  streamChat(
    messages: ChatMessage[],
    opts?: {
      model?: string;
      tools?: ToolDef[];
      previousInteractionId?: string;
      signal?: AbortSignal;
      /** §9: inline image/document parts attached to the last user message. */
      attachments?: InlinePart[];
      /** §9: server-side extracted text (TXT/CSV/DOCX) prepended to the turn. */
      extractedText?: string;
    },
  ): AsyncGenerator<StreamChunk>; // §3, §6 must stream progressively
  generateAgentConfig(naturalLanguage: string): Promise<unknown>; // §12-§14 conversational builder
  titleFor(firstUserMessage: string): Promise<string>; // §8 auto-title
}
