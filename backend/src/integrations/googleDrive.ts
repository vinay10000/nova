// F2 Google Drive/Docs/Sheets — one Google OAuth client, one Connection row.
// Provider id is 'drive'; every tool id starts with 'drive_' so the safety
// gate (toolId.split('_')[0]) resolves the same connection. Chat plugins
// @docs and @sheets map onto drive_-prefixed tools with requires:'drive'.
import type { PrismaClient } from '@prisma/client';
import { createHash } from 'node:crypto';
import { encryptToken, decryptToken, generateOAuthState, generateCodeVerifier } from '../security/crypto.js';

export const DRIVE_SCOPES = [
  'https://www.googleapis.com/auth/drive.readonly',
  'https://www.googleapis.com/auth/documents',
  'https://www.googleapis.com/auth/spreadsheets',
];

const GOOGLE_AUTHORIZE_URL = 'https://accounts.google.com/o/oauth2/v2/auth';
const GOOGLE_TOKEN_URL = 'https://oauth2.googleapis.com/token';
export const DRIVE_API = 'https://www.googleapis.com/drive/v3';
export const DOCS_API = 'https://docs.googleapis.com/v1';
export const SHEETS_API = 'https://sheets.googleapis.com/v4';

interface GoogleTokens { access_token: string; refresh_token?: string; expires_in?: number; scope?: string }
interface Stored { access_token: string; refresh_token: string; expiry: number }

function redirectUri(): string {
  return `${process.env.BACKEND_PUBLIC_URL}/v1/connections/drive/callback`;
}

export function buildDriveAuthorizeUrl(): { url: string; state: string; codeVerifier: string } {
  const clientId = process.env.GOOGLE_CLIENT_ID;
  if (!clientId) throw new Error('GOOGLE_CLIENT_ID is required');
  const state = generateOAuthState();
  const codeVerifier = generateCodeVerifier();
  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri(),
    response_type: 'code',
    scope: ['openid', 'email', ...DRIVE_SCOPES].join(' '),
    state,
    code_challenge: createHash('sha256').update(codeVerifier).digest('base64url'),
    code_challenge_method: 'S256',
    access_type: 'offline',
    prompt: 'consent',
  });
  return { url: `${GOOGLE_AUTHORIZE_URL}?${params.toString()}`, state, codeVerifier };
}

export async function exchangeDriveCode(code: string, codeVerifier: string): Promise<GoogleTokens> {
  const { GOOGLE_CLIENT_ID: id, GOOGLE_CLIENT_SECRET: secret } = process.env;
  if (!id || !secret) throw new Error('GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are required');
  const res = await fetch(GOOGLE_TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: id, client_secret: secret, code, code_verifier: codeVerifier, grant_type: 'authorization_code', redirect_uri: redirectUri() }),
  });
  if (!res.ok) throw new Error(`Drive token exchange failed: ${res.status}`);
  const data = (await res.json()) as GoogleTokens;
  if (!data.access_token) throw new Error('Drive token exchange returned no access_token');
  return data;
}

async function refreshToken(refresh: string): Promise<GoogleTokens> {
  const { GOOGLE_CLIENT_ID: id, GOOGLE_CLIENT_SECRET: secret } = process.env;
  if (!id || !secret) throw new Error('GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are required');
  const res = await fetch(GOOGLE_TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: id, client_secret: secret, refresh_token: refresh, grant_type: 'refresh_token' }),
  });
  if (!res.ok) throw new Error(`Drive token refresh failed: ${res.status}`);
  return (await res.json()) as GoogleTokens;
}

export async function storeDriveConnection(db: PrismaClient, userId: string, tokens: GoogleTokens): Promise<void> {
  if (!tokens.refresh_token) throw new Error('Drive OAuth did not return a refresh_token (access_type=offline required)');
  const blob: Stored = { access_token: tokens.access_token, refresh_token: tokens.refresh_token, expiry: Date.now() + (tokens.expires_in ?? 3600) * 1000 };
  const scopes = (tokens.scope ?? DRIVE_SCOPES.join(' ')).split(' ').map((s) => s.trim()).filter(Boolean);
  await db.connection.upsert({
    where: { userId_provider: { userId, provider: 'drive' } },
    update: { encryptedToken: encryptToken(JSON.stringify(blob)), scopes, status: 'connected', lastRefreshedAt: new Date() },
    create: { userId, provider: 'drive', encryptedToken: encryptToken(JSON.stringify(blob)), scopes, status: 'connected' },
  });
}

export async function getDriveAccessToken(db: PrismaClient, userId: string, opts?: { forceRefresh?: boolean }): Promise<string | null> {
  const conn = await db.connection.findFirst({ where: { userId, provider: 'drive' } });
  if (!conn) return null;
  let stored: Stored;
  try { stored = JSON.parse(decryptToken(conn.encryptedToken)) as Stored; } catch { return null; }
  if (!stored.access_token || !stored.refresh_token) return null;
  if (!opts?.forceRefresh && stored.expiry - Date.now() > 60_000) return stored.access_token;
  try {
    const r = await refreshToken(stored.refresh_token);
    const updated: Stored = { access_token: r.access_token, refresh_token: stored.refresh_token, expiry: Date.now() + (r.expires_in ?? 3600) * 1000 };
    await db.connection.update({ where: { id: conn.id }, data: { encryptedToken: encryptToken(JSON.stringify(updated)), lastRefreshedAt: new Date(), status: 'connected' } });
    return updated.access_token;
  } catch { return null; }
}

export async function markDriveConnectionExpired(db: PrismaClient, userId: string): Promise<void> {
  await db.connection.updateMany({ where: { userId, provider: 'drive' }, data: { status: 'expired' } }).catch(() => {});
}

export async function disconnectDrive(db: PrismaClient, userId: string): Promise<boolean> {
  const { count } = await db.connection.deleteMany({ where: { userId, provider: 'drive' } });
  return count > 0;
}

export async function verifyDriveConnection(db: PrismaClient, userId: string): Promise<{ connected: boolean; ok: boolean; login: string | null }> {
  const conn = await db.connection.findFirst({ where: { userId, provider: 'drive' }, select: { status: true, providerLogin: true } });
  const token = await getDriveAccessToken(db, userId, { forceRefresh: true });
  if (!token) {
    if (conn) await markDriveConnectionExpired(db, userId);
    return { connected: !!conn, ok: false, login: conn?.providerLogin ?? null };
  }
  return { connected: true, ok: true, login: conn?.providerLogin ?? null };
}
