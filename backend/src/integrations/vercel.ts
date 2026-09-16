// F2 Vercel — token-based (no OAuth app needed). User pastes a Vercel token
// (account token or project token); stored AES-GCM in Connection provider 'vercel'.
import type { PrismaClient } from '@prisma/client';
import { encryptToken, decryptToken } from '../security/crypto.js';

export const VERCEL_API = 'https://api.vercel.com';

export async function storeVercelConnection(db: PrismaClient, userId: string, token: string, login?: string): Promise<void> {
  if (!token.trim()) throw new Error('invalid_input: token is required');
  await db.connection.upsert({
    where: { userId_provider: { userId, provider: 'vercel' } },
    update: { encryptedToken: encryptToken(token.trim()), scopes: ['vercel.read', 'vercel.deploy'], status: 'connected', providerLogin: login ?? null, lastRefreshedAt: new Date() },
    create: { userId, provider: 'vercel', encryptedToken: encryptToken(token.trim()), scopes: ['vercel.read', 'vercel.deploy'], status: 'connected', providerLogin: login ?? null },
  });
}

export async function getVercelToken(db: PrismaClient, userId: string): Promise<string | null> {
  const conn = await db.connection.findFirst({ where: { userId, provider: 'vercel' } });
  if (!conn || conn.status === 'expired') return null;
  try { return decryptToken(conn.encryptedToken); } catch { return null; }
}

export async function markVercelExpired(db: PrismaClient, userId: string): Promise<void> {
  await db.connection.updateMany({ where: { userId, provider: 'vercel' }, data: { status: 'expired' } }).catch(() => {});
}

export async function disconnectVercel(db: PrismaClient, userId: string): Promise<boolean> {
  const { count } = await db.connection.deleteMany({ where: { userId, provider: 'vercel' } });
  return count > 0;
}
