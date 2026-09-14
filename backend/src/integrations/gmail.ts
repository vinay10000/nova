import type { PrismaClient } from '@prisma/client';
import { createHash } from 'node:crypto';
import { encryptToken, decryptToken, generateOAuthState, generateCodeVerifier } from '../security/crypto.js';

// §17 Gmail OAuth scopes — connecting grants READ + SEND-capable tokens, but
// send tools remain gated behind human approval (§36). Least-privilege vs
// full gmail.modify: readonly for search/read, send only for the send tool.
export const GMAIL_SCOPES = [
  'https://www.googleapis.com/auth/gmail.readonly',
  'https://www.googleapis.com/auth/gmail.send',
];

const GOOGLE_AUTHORIZE_URL = 'https://accounts.google.com/o/oauth2/v2/auth';
const GOOGLE_TOKEN_URL = 'https://oauth2.googleapis.com/token';
const GMAIL_API = 'https://gmail.googleapis.com/gmail/v1';

export interface GmailTokens {
  access_token: string;
  refresh_token?: string;
  expires_in?: number;
  scope?: string;
  token_type?: string;
}

/** Encrypted blob stored in Connection.encryptedToken — no schema migration needed. */
interface StoredGmailTokens {
  access_token: string;
  refresh_token: string;
  /** epoch ms when the access token expires */
  expiry: number;
}

function redirectUri(): string {
  return `${process.env.BACKEND_PUBLIC_URL}/v1/connections/gmail/callback`;
}

/**
 * §38: Build the Google OAuth authorization URL with PKCE (S256).
 * access_type=offline + prompt=consent guarantees a refresh_token on first grant.
 */
export function buildGmailAuthorizeUrl(_userId: string): {
  url: string;
  state: string;
  codeVerifier: string;
} {
  const clientId = process.env.GOOGLE_CLIENT_ID;
  if (!clientId) throw new Error('GOOGLE_CLIENT_ID is required');

  const state = generateOAuthState();
  const codeVerifier = generateCodeVerifier();
  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri(),
    response_type: 'code',
    scope: ['openid', 'email', 'profile', ...GMAIL_SCOPES].join(' '),
    state,
    code_challenge: createHash('sha256').update(codeVerifier).digest('base64url'),
    code_challenge_method: 'S256',
    access_type: 'offline',
    prompt: 'consent',
  });

  return { url: `${GOOGLE_AUTHORIZE_URL}?${params.toString()}`, state, codeVerifier };
}

/** §38: Exchange the authorization code for access + refresh tokens. */
export async function exchangeCodeForGmailToken(
  code: string,
  codeVerifier: string,
): Promise<GmailTokens> {
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
  if (!res.ok) throw new Error(`Gmail token exchange failed: ${res.status}`);
  const data = (await res.json()) as GmailTokens;
  if (!data.access_token) throw new Error('Gmail token exchange returned no access_token');
  return data;
}

async function refreshGmailAccessToken(refreshToken: string): Promise<GmailTokens> {
  const clientId = process.env.GOOGLE_CLIENT_ID;
  const clientSecret = process.env.GOOGLE_CLIENT_SECRET;
  if (!clientId || !clientSecret) throw new Error('GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are required');

  const res = await fetch(GOOGLE_TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      refresh_token: refreshToken,
      grant_type: 'refresh_token',
    }),
  });
  if (!res.ok) throw new Error(`Gmail token refresh failed: ${res.status}`);
  return (await res.json()) as GmailTokens;
}

async function fetchGmailProfile(accessToken: string): Promise<{ email: string; id?: string }> {
  const res = await fetch(`${GMAIL_API}/users/me/profile`, {
    headers: { Authorization: `Bearer ${accessToken}` },
  });
  if (!res.ok) throw new Error(`Gmail profile fetch failed: ${res.status}`);
  return (await res.json()) as { emailAddress: string; id?: string } as unknown as { email: string; id?: string };
}

/**
 * §38: Store or update the Gmail connection. The refresh token is the durable
 * credential — access tokens rotate. Both encrypted at rest (§46) as one blob.
 */
export async function storeGmailConnection(
  db: PrismaClient,
  userId: string,
  tokens: GmailTokens,
): Promise<void> {
  if (!tokens.refresh_token) throw new Error('Gmail OAuth did not return a refresh_token (access_type=offline required)');
  const profile = await fetchGmailProfile(tokens.access_token);
  const email = (profile as unknown as { emailAddress?: string }).emailAddress ?? profile.email ?? '';
  const blob: StoredGmailTokens = {
    access_token: tokens.access_token,
    refresh_token: tokens.refresh_token,
    expiry: Date.now() + (tokens.expires_in ?? 3600) * 1000,
  };
  const encrypted = encryptToken(JSON.stringify(blob));
  const scopes = (tokens.scope ?? GMAIL_SCOPES.join(' ')).split(' ').map((s) => s.trim()).filter(Boolean);

  await db.connection.upsert({
    where: { userId_provider: { userId, provider: 'gmail' } },
    update: {
      encryptedToken: encrypted,
      scopes,
      status: 'connected',
      providerUserId: profile.id ?? email,
      providerLogin: email,
      lastRefreshedAt: new Date(),
    },
    create: {
      userId,
      provider: 'gmail',
      encryptedToken: encrypted,
      scopes,
      status: 'connected',
      providerUserId: profile.id ?? email,
      providerLogin: email,
    },
  });
}

/**
 * Retrieve a live Gmail access token, refreshing in place when expired.
 * §46: decrypted only in tool execution scope, never logged.
 */
export async function getGmailAccessToken(
  db: PrismaClient,
  userId: string,
): Promise<string | null> {
  const conn = await db.connection.findFirst({
    where: { userId, provider: 'gmail', status: 'connected' },
  });
  if (!conn) return null;
  let stored: StoredGmailTokens;
  try {
    stored = JSON.parse(decryptToken(conn.encryptedToken)) as StoredGmailTokens;
  } catch {
    return null;
  }
  if (!stored.access_token || !stored.refresh_token) return null;
  // 60s skew so a token expiring mid-request still refreshes first.
  if (stored.expiry - Date.now() > 60_000) return stored.access_token;

  try {
    const fresh = await refreshGmailAccessToken(stored.refresh_token);
    const updated: StoredGmailTokens = {
      access_token: fresh.access_token,
      refresh_token: stored.refresh_token, // Google omits refresh_token on refresh
      expiry: Date.now() + (fresh.expires_in ?? 3600) * 1000,
    };
    await db.connection.update({
      where: { id: conn.id },
      data: { encryptedToken: encryptToken(JSON.stringify(updated)), lastRefreshedAt: new Date() },
    });
    return updated.access_token;
  } catch {
    return null;
  }
}

export async function disconnectGmail(db: PrismaClient, userId: string): Promise<boolean> {
  const { count } = await db.connection.deleteMany({ where: { userId, provider: 'gmail' } });
  return count > 0;
}

export { GMAIL_API };
