import Fastify from 'fastify';
import cors from '@fastify/cors';
import { z } from 'zod';
import { PrismaClient } from '@prisma/client';
import { PrismaPg } from '@prisma/adapter-pg';
import { GeminiProvider, MODELS } from './ai/GeminiProvider.js';
import { ConversationNotFoundError, createChatService } from './services/chatService.js';
import { prismaChatStore } from './services/prismaChatStore.js';
import { hashPassword, issueToken, requireUser, verifyPassword } from './auth.js';

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
if (!process.env.GEMINI_API_KEY) {
  app.log.warn('[config] GEMINI_API_KEY is not set — chat features will fail.');
}

// Prisma 7 requires a driver adapter; pg connects directly to Postgres.
const db = new PrismaClient({ adapter: new PrismaPg({ connectionString: process.env.DATABASE_URL }) });
const ai = new GeminiProvider();
const chat = createChatService(ai, prismaChatStore(db));

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
    include: { messages: { orderBy: { createdAt: 'asc' } } },
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

// ---- §3/§6 streaming chat (SSE) ---------------------------------------------

const streamBody = z.object({
  conversationId: z.string().min(1),
  message: z.string().min(1).max(32_000),
  model: z.string().optional(),
});

app.post('/v1/chat/stream', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const parsed = streamBody.safeParse(req.body);
  if (!parsed.success) return reply.code(400).send({ error: 'invalid_input' });
  const { conversationId, message, model } = parsed.data;

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
    for await (const chunk of chat.stream({ conversationId, userId, message, model, signal: ac.signal })) {
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

// Stubs for Phase 3-5 — contract in shared/openapi.yaml.
app.get('/v1/agents', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  return { agents: await db.agent.findMany({ where: { userId } }) };
});
app.post('/v1/agents/:id/run', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  const { id } = req.params as { id: string };
  const agent = await db.agent.findFirst({ where: { id, userId }, select: { id: true } });
  if (!agent) return reply.code(404).send({ error: 'not_found' });
  return { agentId: id, status: 'QUEUED' };
});
app.get('/v1/executions', async (req, reply) => {
  const userId = await requireUser(req, reply);
  if (!userId) return reply;
  return { executions: await db.execution.findMany({ where: { userId }, orderBy: { startedAt: 'desc' }, take: 50 }) };
});

const port = Number(process.env.PORT ?? 3000);
try {
  await app.listen({ port, host: '0.0.0.0' });
} catch (err) {
  app.log.error(err);
  process.exit(1);
}
