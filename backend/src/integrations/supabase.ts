// F2 Supabase — token-based. User supplies project ref + service/anon key;
// stored as one encrypted JSON blob in Connection provider 'supabase'.
import type { PrismaClient } from '@prisma/client';
import { encryptToken, decryptToken } from '../security/crypto.js';

interface Stored { projectRef: string; key: string }

export async function storeSupabaseConnection(db: PrismaClient, userId: string, projectRef: string, key: string): Promise<void> {
  if (!projectRef.trim() || !key.trim()) throw new Error('invalid_input: projectRef and key are required');
  const blob: Stored = { projectRef: projectRef.trim(), key: key.trim() };
  await db.connection.upsert({
    where: { userId_provider: { userId, provider: 'supabase' } },
    update: { encryptedToken: encryptToken(JSON.stringify(blob)), scopes: ['supabase.read', 'supabase.write'], status: 'connected', providerLogin: projectRef.trim(), lastRefreshedAt: new Date() },
    create: { userId, provider: 'supabase', encryptedToken: encryptToken(JSON.stringify(blob)), scopes: ['supabase.read', 'supabase.write'], status: 'connected', providerLogin: projectRef.trim() },
  });
}

export async function getSupabaseCreds(db: PrismaClient, userId: string): Promise<Stored | null> {
  const conn = await db.connection.findFirst({ where: { userId, provider: 'supabase' } });
  if (!conn || conn.status === 'expired') return null;
  try {
    const s = JSON.parse(decryptToken(conn.encryptedToken)) as Stored;
    return s.projectRef && s.key ? s : null;
  } catch { return null; }
}

export function supabaseRestBase(projectRef: string): string {
  return `https://${projectRef}.supabase.co/rest/v1`;
}

export async function disconnectSupabase(db: PrismaClient, userId: string): Promise<boolean> {
  const { count } = await db.connection.deleteMany({ where: { userId, provider: 'supabase' } });
  return count > 0;
}

export async function markSupabaseExpired(db: PrismaClient, userId: string): Promise<void> {
  await db.connection.updateMany({ where: { userId, provider: 'supabase' }, data: { status: 'expired' } }).catch(() => {});
}
