import type { PrismaClient } from '@prisma/client';
import { createHash } from 'node:crypto';
import { encryptToken, decryptToken, generateOAuthState, generateCodeVerifier } from '../security/crypto.js';

// §17/§18 Google Calendar — read events plus approval-gated event creation.
// Reuses the same Google OAuth client as Gmail (one Cloud project, one consent
// screen); granted scopes are stored per connection. The provider id is
// 'calendar' so the connection matches the `calendar_*` tool-id prefix.

export const CALENDAR_SCOPES = [
  'https://www.googleapis.com/auth/calendar.readonly',
  'https://www.googleapis.com/auth/calendar.events',
];

const GOOGLE_AUTHORIZE_URL = 'https://accounts.google.com/o/oauth2/v2/auth';
const GOOGLE_TOKEN_URL = 'https://oauth2.googleapis.com/token';
const CALENDAR_API = 'https://www.googleapis.com/calendar/v3';

export interface GoogleTokens {
  access_token: string;
  refresh_token?: string;
  expires_in?: number;
  scope?: string;
  token_type?: string;
}

interface StoredTokens {
  access_token: string;
  refresh_token: string;
  expiry: number;
}

function redirectUri(): string {
  return `${process.env.BACKEND_PUBLIC_URL}/v1/connections/calendar/callback`;
}

/** §38: authorization URL with PKCE; offline access keeps a refresh token. */
export function buildCalendarAuthorizeUrl(): { url: string; state: string; codeVerifier: string } {
  const clientId = process.env.GOOGLE_CLIENT_ID;
  if (!clientId) throw new Error('GOOGLE_CLIENT_ID is required');
  const state = generateOAuthState();
  const codeVerifier = generateCodeVerifier();
  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri(),
    response_type: 'code',
    scope: ['openid', 'email', ...CALENDAR_SCOPES].join(' '),
    state,
    code_challenge: createHash('sha256').update(codeVerifier).digest('base64url'),
    code_challenge_method: 'S256',
    access_type: 'offline',
    prompt: 'consent',
  });
  return { url: `${GOOGLE_AUTHORIZE_URL}?${params.toString()}`, state, codeVerifier };
}

export async function exchangeCalendarCode(code: string, codeVerifier: string): Promise<GoogleTokens> {
  const clientId = process.env.GOOGLE_CLIENT_ID;
  const clientSecret = process.env.GOOGLE_CLIENT_SECRET;
  if (!clientId || !clientSecret) throw new Error('GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are required');
  const res = await fetch(GOOGLE_TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      code,
      code_verifier: codeVerifier,
      grant_type: 'authorization_code',
      redirect_uri: redirectUri(),
    }),
  });
  if (!res.ok) throw new Error(`Calendar token exchange failed: ${res.status}`);
  const data = (await res.json()) as GoogleTokens;
  if (!data.access_token) throw new Error('Calendar token exchange returned no access_token');
  return data;
}

async function refreshAccessToken(refreshToken: string): Promise<GoogleTokens> {
  const res = await fetch(GOOGLE_TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: process.env.GOOGLE_CLIENT_ID ?? '',
      client_secret: process.env.GOOGLE_CLIENT_SECRET ?? '',
      refresh_token: refreshToken,
      grant_type: 'refresh_token',
    }),
  });
  if (!res.ok) throw new Error(`Calendar token refresh failed: ${res.status}`);
  return (await res.json()) as GoogleTokens;
}

async function primaryCalendar(accessToken: string): Promise<string> {
  const res = await fetch(`${CALENDAR_API}/calendars/primary`, { headers: { Authorization: `Bearer ${accessToken}` } });
  if (!res.ok) throw new Error(`calendar_profile_${res.status}`);
  const body = (await res.json()) as { id?: string };
  return body.id ?? 'primary';
}

export { CALENDAR_API };

/** §38: store the encrypted token blob; providerLogin is the primary calendar id. */
export async function storeCalendarConnection(db: PrismaClient, userId: string, tokens: GoogleTokens): Promise<void> {
  if (!tokens.refresh_token) throw new Error('Calendar OAuth did not return a refresh_token (access_type=offline required)');
  const login = await primaryCalendar(tokens.access_token).catch(() => 'primary');
  const blob: StoredTokens = {
    access_token: tokens.access_token,
    refresh_token: tokens.refresh_token,
    expiry: Date.now() + (tokens.expires_in ?? 3600) * 1000,
  };
  const scopes = (tokens.scope ?? CALENDAR_SCOPES.join(' ')).split(' ').map((s) => s.trim()).filter(Boolean);
  await db.connection.upsert({
    where: { userId_provider: { userId, provider: 'calendar' } },
    update: { encryptedToken: encryptToken(JSON.stringify(blob)), scopes, status: 'connected', providerLogin: login, lastRefreshedAt: new Date() },
    create: { userId, provider: 'calendar', encryptedToken: encryptToken(JSON.stringify(blob)), scopes, status: 'connected', providerLogin: login },
  });
}

/**
 * Live access token, refreshed in place (§38). An 'expired' row is still tried:
 * a successful refresh means Google accepted us again, and the status heals.
 */
export async function getCalendarAccessToken(
  db: PrismaClient,
  userId: string,
  opts?: { forceRefresh?: boolean },
): Promise<string | null> {
  const conn = await db.connection.findFirst({ where: { userId, provider: 'calendar' } });
  if (!conn) return null;
  let stored: StoredTokens;
  try {
    stored = JSON.parse(decryptToken(conn.encryptedToken)) as StoredTokens;
  } catch {
    return null;
  }
  if (!stored.access_token || !stored.refresh_token) return null;
  if (opts?.forceRefresh !== true && stored.expiry - Date.now() > 60_000) return stored.access_token;
  try {
    const refreshed = await refreshAccessToken(stored.refresh_token);
    const updated: StoredTokens = {
      access_token: refreshed.access_token,
      refresh_token: stored.refresh_token, // Google omits refresh_token on refresh
      expiry: Date.now() + (refreshed.expires_in ?? 3600) * 1000,
    };
    await db.connection.update({
      where: { id: conn.id },
      data: {
        encryptedToken: encryptToken(JSON.stringify(updated)),
        lastRefreshedAt: new Date(),
        ...(conn.status === 'connected' ? {} : { status: 'connected' }),
      },
    });
    return updated.access_token;
  } catch {
    return null;
  }
}

export async function markCalendarConnectionExpired(db: PrismaClient, userId: string): Promise<void> {
  await db.connection.updateMany({ where: { userId, provider: 'calendar' }, data: { status: 'expired' } }).catch(() => {});
}

export async function disconnectCalendar(db: PrismaClient, userId: string): Promise<boolean> {
  const { count } = await db.connection.deleteMany({ where: { userId, provider: 'calendar' } });
  return count > 0;
}
