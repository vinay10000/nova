import Fastify from 'fastify';
import cors from '@fastify/cors';
import multipart from '@fastify/multipart';
import { z } from 'zod';
import { createHash } from 'crypto';
import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { GoogleGenAI } from '@google/genai';
import { OpenAIProvider, MODELS } from './ai/OpenAIProvider.js';
import { createChatService, ConversationNotFoundError } from './services/chatService.js';
import { executeAgent, scopesForTools } from './services/agentService.js';
import { toolRegistry } from './tools/registry.js';
import { prismaChatStore } from './services/prismaChatStore.js';
import { prismaAttachmentSource } from './services/prismaAttachmentSource.js';
import { createFileService, MAX_FILE_BYTES } from './services/fileService.js';
import { hashPassword, issueToken, requireUser, verifyPassword } from './auth.js';
import {
  buildGitHubAuthorizeUrl,
  exchangeCodeForToken,
  storeGitHubConnection,
  getGitHubUser,
  disconnectGitHub,
} from './integrations/github.js';
import {
  buildGmailAuthorizeUrl,
  exchangeCodeForGmailToken,
  storeGmailConnection,
  disconnectGmail,
  GMAIL_SCOPES,
} from './integrations/gmail.js';
import { chatPlugins } from './services/chatPlugins.js';

const app = Fastify({ logger: true });

// CORS: reflect-any-origin only in dev. In production require explicit allow-list.
const corsOrigins = process.env.CORS_ORIGIN?.split(',').map((s) => s.trim()).filter(Boolean);
await app.register(cors, {
  origin: corsOrigins?.length ? corsOrigins : process.env.NODE_ENV === 'production' ? false : true,
});

// Fail fast and loud: missing infrastructure config must surface at boot, not mid-stream.
if (!process.env.DATABASE_URL) {
  throw new Error('DATABASE_URL is required');
}
if (!process.env.AUTH_JWT_SECRET || process.env.AUTH_JWT_SECRET === 'change-me' || process.env.AUTH_JWT_SECRET === 'dev-only-change-me') {
  throw new Error('AUTH_JWT_SECRET must be set to a real secret');
}
if (!process.env.AI_API_KEY) {
  app.log.warn('[config] AI_API_KEY is not set — chat features will fail.');
}

// Prisma 7 requires a driver adapter; pg connects directly to Postgres.
const db = new PrismaClient({ adapter: new PrismaPg({ connectionString: process.env.DATABASE_URL }) });
const ai = new OpenAIProvider();
const chat = createChatService(ai, prismaChatStore(db), prismaAttachmentSource(db), db);
const files = createFileService(db);
await app.register(multipart, { limits: { fileSize: MAX_FILE_BYTES } });

app.get('/health', async () => ({ ok: true }));

// ---- §29 auth ----------------------------------------------------------------

// Minimal in-memory login throttle (no new dep): 20 attempts / 60s per IP.
const loginAttempts = new Map<string, number[]>();
function loginRateLimited(req: { ip: string }, reply: { code: (n: number) => { send: (b: unknown) => unknown } }): boolean {
  const now = Date.now();
  const windowStart = now - 60_000;
  const hits = (loginAttempts.get(req.ip) ?? []).filter((t) => t > windowStart);
  hits.push(now);
  loginAttempts.set(req.ip, hits);
  if (hits.length > 20) {
    reply.code(429).send({ error: 'rate_limited' });
    return true;
  }
  return false;
}

const creds = z.object({ email: z.string().email(), password: z.string().min(8) });

app.post('/v1/auth/register', async (req, reply) => {
  if (loginRateLimited(req, reply)) return reply;
  const parsed = creds.safeParse(req.body);
  if (!parsed.success) return reply.code(400).send({ error: 'invalid_input' });
  const { email, password } = parsed.data;
  if (await db.user.findUnique({ where: { email } })) {
    return reply.code(409).send({ error: 'email_taken' });
  }
  const user = await db.user.create({ data: { email, passwordHash: await hashPassword(password) } });
  return { token: await issueToken(user.id), user: { id: user.id, email: user.email } };
});

app.post('/v1/auth/login', async (req, reply) => {
  if (loginRateLimited(req, reply)) return reply;
  const parsed = creds.safeParse(req.body);
  if (!parsed.success) return reply.code(400).send({ error: 'invalid_input' });
  const user = await db.user.findUnique({ where: { email: parsed.data.email } });
  // Same response for unknown user and bad password — no account enumeration.
  if (!user || !(await verifyPassword(parsed.data.password, user.passwordHash))) {
    return reply.code(401).send({ error: 'invalid_credentials' });
  }
  return { token: await issueToken(user.id), user: { id: user.id, email: user.email } };
});

app.get('/v1/me', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const user = await db.user.findUnique({ where: { id: userId }, select: { id: true, email: true } });
  return user ?? reply.code(404).send({ error: 'not_found' });
});

// ---- §8 conversations --------------------------------------------------------

app.get('/v1/conversations', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { q, archived } = req.query as { q?: string; archived?: string };
  return {
    conversations: await db.conversation.findMany({
      where: {
        userId,
        archived: archived === 'true',
        ...(q ? { title: { contains: q, mode: 'insensitive' as const } } : {}),
      },
      orderBy: { updatedAt: 'desc' },
      select: { id: true, title: true, archived: true, createdAt: true, updatedAt: true },
    }),
  };
});

app.post('/v1/conversations', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  return db.conversation.create({ data: { userId }, select: { id: true, title: true } });
});

app.get('/v1/conversations/:id', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { id } = req.params as { id: string };
  const conv = await db.conversation.findFirst({
    where: { id, userId }, // userId in the filter = isolation, not a post-check
    include: {
      messages: {
        orderBy: { createdAt: 'asc' },
        include: {
          attachments: { select: { id: true, filename: true, mime: true, size: true } },
        },
      },
    },
  });
  return conv ?? reply.code(404).send({ error: 'not_found' });
});

app.patch('/v1/conversations/:id', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { id } = req.params as { id: string };
  const body = z.object({ title: z.string().min(1).max(200).optional(), archived: z.boolean().optional() }).safeParse(req.body);
  if (!body.success) return reply.code(400).send({ error: 'invalid_input' });
  const { count } = await db.conversation.updateMany({ where: { id, userId }, data: body.data });
  if (!count) return reply.code(404).send({ error: 'not_found' });
  return { ok: true };
});

app.delete('/v1/conversations/:id', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { id } = req.params as { id: string };
  const { count } = await db.conversation.deleteMany({ where: { id, userId } });
  if (!count) return reply.code(404).send({ error: 'not_found' });
  return { ok: true };
});

// ---- §10 files: upload -> validate (magic bytes) -> store -> extract --------

app.post('/v1/files', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return;
  if (!req.isMultipart()) return reply.code(400).send({ error: 'multipart_required' });
  const part = await req.file();
  if (!part) return reply.code(400).send({ error: 'file_missing' });
  const buf = await part.toBuffer();
  try {
    const stored = await files.store(userId, part.filename, part.mimetype, buf);
    return stored;
  } catch (err) {
    const reason = err instanceof Error ? err.message : 'upload_failed';
    const code = ['empty_file', 'file_too_large', 'unsupported_type'].includes(reason) ? reason : 'upload_failed';
    return reply.code(400).send({ error: code });
  }
});

app.get('/v1/files/:id', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return;
  const { id } = req.params as { id: string };
  const att = await files.get(userId, id);
  // Metadata only — the binary stays out of JSON responses; inline data flows to Gemini server-side.
  if (!att) return reply.code(404).send({ error: 'not_found' });
  return { id: att.id, filename: att.filename, mime: att.mime, size: att.size, status: att.status, url: att.url };
});

app.get('/v1/files/:id/raw', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return;
  const { id } = req.params as { id: string };
  const att = await files.get(userId, id);
  if (!att || !att.data) return reply.code(404).send({ error: 'not_found' });
  return reply
    .header('Content-Type', att.mime)
    .header('Cache-Control', 'public, max-age=86400')
    .send(Buffer.from(att.data));
});

// ---- §3/§6 streaming chat (SSE) ---------------------------------------------

const streamBody = z.object({
  conversationId: z.string().min(1),
  message: z.string().min(1).max(32_000),
  model: z.string().optional(),
  attachmentIds: z.array(z.string().min(1)).max(5).optional(), // §10
});

app.post('/v1/chat/stream', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const parsed = streamBody.safeParse(req.body);
  if (!parsed.success) return reply.code(400).send({ error: 'invalid_input' });
  const { conversationId, message, model, attachmentIds } = parsed.data;

  // Ownership validated BEFORE headers: a foreign/missing conversation is HTTP 404,
  // not an SSE error event after HTTP 200.
  const conv = await db.conversation.findFirst({ where: { id: conversationId, userId }, select: { id: true } });
  if (!conv) return reply.code(404).send({ error: 'not_found' });

  // Take over the raw response lifecycle — Fastify must not send the return value.
  reply.hijack();
  reply.raw.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    Connection: 'keep-alive',
    'X-Accel-Buffering': 'no',
  });
  const send = (chunk: unknown) => {
    if (!reply.raw.writableEnded) reply.raw.write(`data: ${JSON.stringify(chunk)}\n\n`);
  };

  // Client disconnect (stop generation, §6) aborts the upstream Gemini stream.
  const ac = new AbortController();
  req.raw.on('close', () => ac.abort());

  try {
    for await (const chunk of chat.stream({ conversationId, userId, message, model, attachmentIds, signal: ac.signal })) {
      if (ac.signal.aborted) break;
      send(chunk);
    }
  } catch (err) {
    req.log.error(err);
    const code = err instanceof ConversationNotFoundError ? 'not_found' : 'upstream_error';
    send({ type: 'error', code, retryable: code === 'upstream_error' });
  } finally {
    if (!reply.raw.writableEnded) reply.raw.end();
  }
  return;
});

app.get('/v1/models', async () => ({ models: Object.values(MODELS) }));

// ---- §11 output: remote TTS via Gemini 2.5 Flash Native Audio Dialog, swappable with device TTS ----

const GEMINI_TTS_MODEL = 'gemini-2.5-flash-preview-native-audio-dialog';
const geminiTts = new GoogleGenAI({ apiKey: process.env.GEMINI_API_KEY ?? '' });

// Gemini native audio returns base64-encoded linear16 PCM at 24 kHz mono;
// wrap a 44-byte WAV header so Android MediaPlayer can play it directly.
function pcmToWav(pcm: Buffer, sampleRate: number): Buffer {
  const h = Buffer.alloc(44);
  h.write('RIFF', 0);
  h.writeUInt32LE(36 + pcm.length, 4);
  h.write('WAVE', 8);
  h.write('fmt ', 12);
  h.writeUInt32LE(16, 16);
  h.writeUInt16LE(1, 20); // PCM
  h.writeUInt16LE(1, 22); // mono
  h.writeUInt32LE(sampleRate, 24);
  h.writeUInt32LE(sampleRate * 2, 28); // byte rate
  h.writeUInt16LE(2, 32); // block align
  h.writeUInt16LE(16, 34); // bits
  h.write('data', 36);
  h.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([h, pcm]);
}

// ---- TTS cache: SHA-256(text) -> WAV buffer, capped at 500 entries ----------
const ttsCache = new Map<string, Buffer>();
const TTS_CACHE_MAX = 500;

function ttsCacheKey(text: string): string {
  return createHash('sha256').update(text).digest('hex');
}

app.post('/v1/tts', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  if (!process.env.GEMINI_API_KEY) return reply.code(503).send({ error: 'tts_unconfigured' });
  const body = z.object({ text: z.string().min(1).max(4000) }).safeParse(req.body);
  if (!body.success) return reply.code(400).send({ error: 'invalid_input' });

  const key = ttsCacheKey(body.data.text);
  const cached = ttsCache.get(key);
  if (cached) {
    return reply.header('Content-Type', 'audio/wav').header('X-TTS-Cache', 'hit').send(cached);
  }

  const response = await geminiTts.models.generateContent({
    model: GEMINI_TTS_MODEL,
    contents: body.data.text,
    config: {
      responseModalities: ['AUDIO'],
      speechConfig: {
        voiceConfig: {
          prebuiltVoiceConfig: {
            voiceName: 'Kore',
          },
        },
      },
    },
  }).catch(() => null);

  const audioB64 = response?.data;
  if (!audioB64) return reply.code(502).send({ error: 'tts_upstream_error' });

  const pcm = Buffer.from(audioB64, 'base64');
  const wav = pcmToWav(pcm, 24000); // Gemini native audio = 24 kHz mono

  // Evict oldest entry if over cap
  if (ttsCache.size >= TTS_CACHE_MAX) {
    const first = ttsCache.keys().next().value!;
    ttsCache.delete(first);
  }
  ttsCache.set(key, wav);

  return reply.header('Content-Type', 'audio/wav').header('X-TTS-Cache', 'miss').send(wav);
});

// ---- §52 agent framework: builder, config, Run Now, executions, approvals --

const agentConfigBody = z.object({
  name: z.string().min(1).max(100),
  description: z.string().max(500).optional(),
  goal: z.string().min(1).max(2000),
  instructions: z.string().min(1).max(4000),
  tools: z.array(z.string().min(1)).min(1).max(20),
  schedule: z.unknown().optional(),
});

// §12/§59 NL -> config or {questions[]} when the request is incomplete.
app.post('/v1/agents/build', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const body = z.object({ prompt: z.string().min(1).max(2000) }).safeParse(req.body);
  if (!body.success) return reply.code(400).send({ error: 'invalid_input' });
  try {
    return await ai.generateAgentConfig(body.data.prompt, [...toolRegistry.keys()]);
  } catch (err) {
    req.log.error(err);
    // Quota exhaustion is transient and retryable — never a 502.
    if (err instanceof Error && /429|quota|rate/i.test(err.message)) {
      return reply.code(429).send({ error: 'rate_limited', retryable: true });
    }
    return reply.code(502).send({ error: 'builder_upstream_error' });
  }
});

app.post('/v1/agents', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const parsed = agentConfigBody.safeParse(req.body);
  if (!parsed.success) return reply.code(400).send({ error: 'invalid_input' });
  const permissions = scopesForTools(parsed.data.tools);
  if (!permissions) return reply.code(400).send({ error: 'unknown_tool' });
  const { schedule, ...rest } = parsed.data;
  return db.agent.create({
    data: { userId, ...rest, schedule: schedule ?? undefined, permissions, status: 'draft' },
    select: { id: true, name: true, status: true },
  });
});

app.get('/v1/agents/:id', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { id } = req.params as { id: string };
  const agent = await db.agent.findFirst({ where: { id, userId } });
  return agent ?? reply.code(404).send({ error: 'not_found' });
});

const agentPatch = z.object({
  name: z.string().min(1).max(100).optional(),
  description: z.string().max(500).optional(),
  goal: z.string().min(1).max(2000).optional(),
  instructions: z.string().min(1).max(4000).optional(),
  tools: z.array(z.string().min(1)).min(1).max(20).optional(),
  schedule: z.unknown().optional(),
  status: z.enum(['draft', 'active', 'paused']).optional(),
});

app.patch('/v1/agents/:id', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { id } = req.params as { id: string };
  const parsed = agentPatch.safeParse(req.body);
  if (!parsed.success) return reply.code(400).send({ error: 'invalid_input' });
  const { tools, schedule, ...rest } = parsed.data;
  let permissions: string[] | undefined;
  if (tools) {
    permissions = scopesForTools(tools) ?? undefined;
    if (!permissions) return reply.code(400).send({ error: 'unknown_tool' });
  }
  const { count } = await db.agent.updateMany({
    where: { id, userId },
    data: { ...rest, ...(tools ? { tools, permissions } : {}), ...(schedule !== undefined && schedule !== null ? { schedule: schedule as object } : {}) },
  });
  if (!count) return reply.code(404).send({ error: 'not_found' });
  return { ok: true };
});

app.post('/v1/agents/:id/activate', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { id } = req.params as { id: string };
  const { count } = await db.agent.updateMany({ where: { id, userId }, data: { status: 'active' } });
  if (!count) return reply.code(404).send({ error: 'not_found' });
  return { ok: true };
});

app.post('/v1/agents/:id/pause', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { id } = req.params as { id: string };
  const { count } = await db.agent.updateMany({ where: { id, userId }, data: { status: 'paused' } });
  if (!count) return reply.code(404).send({ error: 'not_found' });
  return { ok: true };
});

// Stubs for Phase 3-5 — contract in shared/openapi.yaml.
app.get('/v1/agents', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  return { agents: await db.agent.findMany({ where: { userId }, orderBy: { updatedAt: 'desc' } }) };
});

// §33 Run Now — executes inline, bounded by RUN_BUDGET_MS (queue + worker land in Phase 5).
app.post('/v1/agents/:id/run', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { id } = req.params as { id: string };
  try {
    return await executeAgent(db, ai, { userId, agentId: id, trigger: 'manual' });
  } catch (err) {
    if (err instanceof Error && err.message === 'not_found') return reply.code(404).send({ error: 'not_found' });
    req.log.error(err);
    return reply.code(502).send({ error: 'run_failed' });
  }
});

app.get('/v1/executions', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { agentId } = (req.query as { agentId?: string }) ?? {};
  return {
    executions: await db.execution.findMany({
      where: { userId, ...(agentId ? { agentId } : {}) },
      orderBy: { startedAt: 'desc' },
      take: 50,
      include: { agent: { select: { name: true } } },
    }),
  };
});

// §44 execution detail incl. §35 step timeline.
app.get('/v1/executions/:id', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { id } = req.params as { id: string };
  const execution = await db.execution.findFirst({
    where: { id, userId },
    include: { steps: { orderBy: { createdAt: 'asc' } }, agent: { select: { name: true } } },
  });
  return execution ?? reply.code(404).send({ error: 'not_found' });
});

app.post('/v1/executions/:id/cancel', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { id } = req.params as { id: string };
  const { count } = await db.execution.updateMany({
    where: { id, userId, status: { in: ['QUEUED', 'RUNNING', 'WAITING_FOR_APPROVAL'] } },
    data: { status: 'CANCELLED', completedAt: new Date() },
  });
  if (!count) return reply.code(404).send({ error: 'not_found' });
  return { ok: true };
});

// §36 checkpoints: list pending, record approve/reject. Resume-on-decision is Phase 5.
app.get('/v1/approvals', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { status } = (req.query as { status?: string }) ?? {};
  return {
    approvals: await db.approval.findMany({
      where: { userId, status: status ?? 'pending' },
      orderBy: { createdAt: 'desc' },
      take: 50,
    }),
  };
});

app.post('/v1/approvals/:id', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { id } = req.params as { id: string };
  const body = z.object({ decision: z.enum(['approve', 'reject']) }).safeParse(req.body);
  if (!body.success) return reply.code(400).send({ error: 'invalid_input' });
  const { count } = await db.approval.updateMany({
    where: { id, userId, status: 'pending' },
    data: { status: body.data.decision === 'approve' ? 'approved' : 'rejected', decidedAt: new Date() },
  });
  if (!count) return reply.code(404).send({ error: 'not_found' });
  return { ok: true, resumed: false };
});

// Registry for the visual editor (§13): id, scope, write flag — never secrets.
app.get('/v1/tools', async () => ({
  tools: [...toolRegistry.values()].map((t) => ({ id: t.id, description: t.description, scope: t.scope, isWrite: t.isWrite })),
}));

// §42: Chat plugins — @mentions that activate tool-backed conversations.
app.get('/v1/plugins', async () => ({
  plugins: chatPlugins.map((p) => ({ id: p.id, name: p.name, tools: p.toolIds })),
}));

// ---- §38 Connections: OAuth flow + status ------------------------------------

// §38: list all connections for the authenticated user (no tokens exposed).
app.get('/v1/connections', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const conns = await db.connection.findMany({
    where: { userId },
    select: { provider: true, status: true, scopes: true, providerLogin: true, createdAt: true },
  });
  return { connections: conns };
});

// §38: Start GitHub OAuth — returns the authorization URL to redirect to.
app.get('/v1/connections/github/authorize', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  try {
    const { url, state, codeVerifier } = buildGitHubAuthorizeUrl(userId);
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes
    await db.oAuthState.create({
      data: {
        state,
        userId,
        provider: 'github',
        codeVerifier,
        scopes: GITHUB_READ_SCOPES.join(','),
        expiresAt,
      },
    });
    return { url, state };
  } catch (err) {
    req.log.error(err);
    return reply.code(500).send({ error: 'github_oauth_config_error' });
  }
});

const GITHUB_READ_SCOPES = ['read:user', 'repo', 'read:org'];

// §38: GitHub OAuth callback — exchanges code for token, stores encrypted connection.
// This is a backend-only endpoint that redirects to the app via deep link.
app.get('/v1/connections/github/callback', async (req, reply) => {
  const { code, state } = req.query as { code?: string; state?: string };
  if (!code || !state) return reply.code(400).send({ error: 'missing_code_or_state' });

  // Look up the state row
  const stateRow = await db.oAuthState.findUnique({ where: { state } });
  if (!stateRow) return reply.code(400).send({ error: 'invalid_state' });
  if (stateRow.expiresAt < new Date()) {
    await db.oAuthState.delete({ where: { state } });
    return reply.code(400).send({ error: 'state_expired' });
  }

  try {
    const tokens = await exchangeCodeForToken(code, stateRow.codeVerifier);
    await storeGitHubConnection(db, stateRow.userId, tokens);
    await db.oAuthState.delete({ where: { state } });
    // Redirect to the Android app via deep link — the app resumes from the Connections screen.
    const deepLink = process.env.DEEP_LINK_SCHEME ?? 'nova';
    return reply.redirect(`${deepLink}://connections/github/connected`);
  } catch (err) {
    req.log.error(err);
    await db.oAuthState.delete({ where: { state } }).catch(() => {});
    return reply.code(500).send({ error: 'github_token_exchange_failed' });
  }
});

// §38: Disconnect GitHub — removes the connection row.
app.delete('/v1/connections/github', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const removed = await disconnectGitHub(db, userId);
  return { ok: removed };
});

// §38: Get current GitHub connection status (lightweight check for polling).
app.get('/v1/connections/github/status', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const conn = await db.connection.findFirst({
    where: { userId, provider: 'github' },
    select: { status: true, providerLogin: true, scopes: true, createdAt: true },
  });
  return { connected: conn?.status === 'connected', login: conn?.providerLogin ?? null, scopes: conn?.scopes ?? [] };
});

// ---- §38 Connections: Gmail OAuth --------------------------------------------

// §38: Start Gmail OAuth — returns the Google consent URL to redirect to.
app.get('/v1/connections/gmail/authorize', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  try {
    const { url, state, codeVerifier } = buildGmailAuthorizeUrl(userId);
    const expiresAt = new Date(Date.now() + 10 * 60 * 1000); // 10 minutes
    await db.oAuthState.create({
      data: {
        state,
        userId,
        provider: 'gmail',
        codeVerifier,
        scopes: GMAIL_SCOPES.join(' '),
        expiresAt,
      },
    });
    return { url, state };
  } catch (err) {
    req.log.error(err);
    return reply.code(500).send({ error: 'gmail_oauth_config_error' });
  }
});

// §38: Gmail OAuth callback — exchanges code, stores encrypted connection.
// Backend-only; redirects to the app via deep link.
app.get('/v1/connections/gmail/callback', async (req, reply) => {
  const { code, state } = req.query as { code?: string; state?: string };
  if (!code || !state) return reply.code(400).send({ error: 'missing_code_or_state' });

  const stateRow = await db.oAuthState.findUnique({ where: { state } });
  if (!stateRow) return reply.code(400).send({ error: 'invalid_state' });
  if (stateRow.expiresAt < new Date()) {
    await db.oAuthState.delete({ where: { state } });
    return reply.code(400).send({ error: 'state_expired' });
  }

  try {
    const tokens = await exchangeCodeForGmailToken(code, stateRow.codeVerifier);
    await storeGmailConnection(db, stateRow.userId, tokens);
    await db.oAuthState.delete({ where: { state } });
    const deepLink = process.env.DEEP_LINK_SCHEME ?? 'nova';
    return reply.redirect(`${deepLink}://connections/gmail/connected`);
  } catch (err) {
    req.log.error(err);
    await db.oAuthState.delete({ where: { state } }).catch(() => {});
    const msg = err instanceof Error ? err.message : '';
    if (/refresh_token/.test(msg)) return reply.code(500).send({ error: 'gmail_refresh_token_missing' });
    return reply.code(500).send({ error: 'gmail_token_exchange_failed' });
  }
});

// §38: Disconnect Gmail — removes the connection row.
app.delete('/v1/connections/gmail', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const removed = await disconnectGmail(db, userId);
  return { ok: removed };
});

// §38: Gmail connection status for polling.
app.get('/v1/connections/gmail/status', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const conn = await db.connection.findFirst({
    where: { userId, provider: 'gmail' },
    select: { status: true, providerLogin: true, scopes: true, createdAt: true },
  });
  return { connected: conn?.status === 'connected', login: conn?.providerLogin ?? null, scopes: conn?.scopes ?? [] };
});

const port = Number(process.env.PORT ?? 3000);
// On Vercel the Fastify instance is driven by the serverless handler in api/index.js;
// binding a port there would hang the lambda.
if (!process.env.VERCEL) {
  try {
    await app.listen({ port, host: '0.0.0.0' });
  } catch (err) {
    app.log.error(err);
    process.exit(1);
  }
}

export { app };
