// F5 browser history + session contexts. The Prisma schema has no
// browserTask/browserSession models yet, so the store probes for them and
// stays inert (null / []) when absent — browser runs still work, history is
// just not persisted. Pass { required: true } to fail loudly instead.
import type { PrismaClient } from '@prisma/client';

interface StartInput {
  userId: string;
  agentId?: string;
  executionId?: string;
  provider: string;
  url: string;
  instruction: string;
  /** Fail loudly when history is unavailable instead of returning null. */
  required?: boolean;
}

interface CompleteInput {
  status: string;
  text?: string;
  sessionId?: string;
}

export class BrowserTaskStore {
  private db: PrismaClient;

  constructor(ctx: { db: PrismaClient }) {
    this.db = ctx.db;
  }

  private get tasks(): unknown {
    return (this.db as unknown as Record<string, unknown>).browserTask ?? null;
  }

  private get sessions(): unknown {
    return (this.db as unknown as Record<string, unknown>).browserSession ?? null;
  }

  async recordStart(input: StartInput): Promise<Record<string, unknown> | null> {
    const t = this.tasks as {
      create: (args: { data: Record<string, unknown> }) => Promise<Record<string, unknown>>;
    } | null;
    if (!t) {
      if (input.required) throw new Error('browser_history_unavailable: browser history is not configured');
      return null;
    }
    const { required: _required, ...data } = input;
    void _required;
    return t.create({ data: { ...data, status: 'running', startedAt: new Date() } });
  }

  async recordComplete(id: string, done: CompleteInput): Promise<Record<string, unknown> | null> {
    const t = this.tasks as {
      update: (args: { where: { id: string }; data: Record<string, unknown> }) => Promise<Record<string, unknown>>;
    } | null;
    if (!t) return null;
    return t.update({ where: { id }, data: { ...done, completedAt: new Date() } });
  }

  async listForUser(userId: string): Promise<Record<string, unknown>[]> {
    const t = this.tasks as {
      findMany: (args: { where: Record<string, unknown> }) => Promise<Record<string, unknown>[]>;
    } | null;
    if (!t) return [];
    return t.findMany({ where: { userId } });
  }

  async findLiveSession(opts: { userId: string; provider: string; now: Date }): Promise<Record<string, unknown> | null> {
    const s = this.sessions as {
      findFirst: (args: { where: Record<string, unknown> }) => Promise<Record<string, unknown> | null>;
    } | null;
    if (!s) return null;
    const row = await s.findFirst({ where: { userId: opts.userId, provider: opts.provider } });
    if (!row) return null;
    const expires = row.expiresAt as Date | string | undefined;
    if (expires && new Date(expires) < opts.now) return null;
    return row;
  }

  async saveSession(opts: { userId: string; provider: string; contextId: string; ttlMs: number }): Promise<Record<string, unknown>> {
    const s = this.sessions as {
      create: (args: { data: Record<string, unknown> }) => Promise<Record<string, unknown>>;
    } | null;
    if (!s) throw new Error('browser_history_unavailable: browser sessions are not configured');
    return s.create({
      data: {
        userId: opts.userId,
        provider: opts.provider,
        contextId: opts.contextId,
        expiresAt: new Date(Date.now() + opts.ttlMs),
      },
    });
  }
}
