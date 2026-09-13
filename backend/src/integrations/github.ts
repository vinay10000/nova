import type { PrismaClient } from '@prisma/client';
import { createHash } from 'node:crypto';
import { encryptToken, decryptToken, generateOAuthState, generateCodeVerifier } from '../security/crypto.js';

// §17 GitHub OAuth scopes — connecting grants READ only. Write tools remain gated.
const GITHUB_READ_SCOPES = ['read:user', 'repo', 'read:org'];
const GITHUB_WRITE_SCOPES = [...GITHUB_READ_SCOPES, 'write:repo', 'write:issues'];

const GITHUB_AUTHORIZE_URL = 'https://github.com/login/oauth/authorize';
const GITHUB_TOKEN_URL = 'https://github.com/login/oauth/access_token';
const GITHUB_API = 'https://api.github.com';

export interface GitHubTokens {
  access_token: string;
  token_type: string;
  scope: string;
}

export interface GitHubUser {
  id: number;
  login: string;
  name: string | null;
  email: string | null;
}

/**
 * §38: Build the GitHub OAuth authorization URL with PKCE.
 * Returns { url, state } — the state is stored in OAuthState for callback verification.
 */
export function buildGitHubAuthorizeUrl(userId: string): {
  url: string;
  state: string;
  codeVerifier: string;
} {
  const clientId = process.env.GITHUB_CLIENT_ID;
  if (!clientId) throw new Error('GITHUB_CLIENT_ID is required');

  const state = generateOAuthState();
  const codeVerifier = generateCodeVerifier();
  // §17: default to read-only scopes on connection. Write tools still need
  // explicit per-tool approval (§36) — the broader scopes are available but
  // never auto-granted to agents.
  const scopeStr = GITHUB_READ_SCOPES.join(' ');

  const redirectUri = `${process.env.BACKEND_PUBLIC_URL}/v1/connections/github/callback`;

  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    scope: scopeStr,
    state,
    // PKCE: S256 challenge
    code_challenge: createHash('sha256').update(codeVerifier).digest('base64url'),
    code_challenge_method: 'S256',
    // Force consent so user always sees what scopes are granted
    prompt: 'consent',
  });

  return {
    url: `${GITHUB_AUTHORIZE_URL}?${params.toString()}`,
    state,
    codeVerifier,
  };
}

/**
 * §38: Exchange the authorization code for an access token.
 */
export async function exchangeCodeForToken(
  code: string,
  codeVerifier: string,
): Promise<GitHubTokens> {
  const clientId = process.env.GITHUB_CLIENT_ID;
  const clientSecret = process.env.GITHUB_CLIENT_SECRET;
  if (!clientId || !clientSecret) throw new Error('GITHUB_CLIENT_ID and GITHUB_CLIENT_SECRET are required');

  const res = await fetch(GITHUB_TOKEN_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/json',
    },
    body: JSON.stringify({
      client_id: clientId,
      client_secret: clientSecret,
      code,
      redirect_uri: `${process.env.BACKEND_PUBLIC_URL}/v1/connections/github/callback`,
      code_verifier: codeVerifier,
    }),
  });

  if (!res.ok) throw new Error(`GitHub token exchange failed: ${res.status}`);
  const data = (await res.json()) as GitHubTokens;
  if (!data.access_token) throw new Error('GitHub token exchange returned no access_token');
  return data;
}

/**
 * Fetch the authenticated GitHub user profile.
 */
export async function getGitHubUser(accessToken: string): Promise<GitHubUser> {
  const res = await fetch(`${GITHUB_API}/user`, {
    headers: {
      Authorization: `Bearer ${accessToken}`,
      Accept: 'application/vnd.github+json',
      'User-Agent': 'Nova-AI-Client',
    },
  });
  if (!res.ok) throw new Error(`GitHub user fetch failed: ${res.status}`);
  return (await res.json()) as GitHubUser;
}

/**
 * §38: Store or update the GitHub connection for a user.
 * Token is encrypted at rest with AES-256-GCM (§46).
 */
export async function storeGitHubConnection(
  db: PrismaClient,
  userId: string,
  tokens: GitHubTokens,
): Promise<void> {
  const user = await getGitHubUser(tokens.access_token);
  const encrypted = encryptToken(tokens.access_token);
  const scopes = tokens.scope.split(',').map((s) => s.trim()).filter(Boolean);

  await db.connection.upsert({
    where: { userId_provider: { userId, provider: 'github' } },
    update: {
      encryptedToken: encrypted,
      scopes,
      status: 'connected',
      providerUserId: String(user.id),
      providerLogin: user.login,
      lastRefreshedAt: new Date(),
    },
    create: {
      userId,
      provider: 'github',
      encryptedToken: encrypted,
      scopes,
      status: 'connected',
      providerUserId: String(user.id),
      providerLogin: user.login,
    },
  });
}

/**
 * Retrieve and decrypt a user's GitHub access token.
 * Returns null if no connection exists or token is invalid.
 * §46: decrypted token used ONLY in tool execution scope.
 */
export async function getGitHubToken(
  db: PrismaClient,
  userId: string,
): Promise<string | null> {
  const conn = await db.connection.findFirst({
    where: { userId, provider: 'github', status: 'connected' },
  });
  if (!conn) return null;
  try {
    return decryptToken(conn.encryptedToken);
  } catch {
    return null;
  }
}

/**
 * Disconnect a user's GitHub connection.
 */
export async function disconnectGitHub(
  db: PrismaClient,
  userId: string,
): Promise<boolean> {
  const { count } = await db.connection.deleteMany({
    where: { userId, provider: 'github' },
  });
  return count > 0;
}

/**
 * Check if a user's GitHub connection is valid and has the required scopes.
 */
export async function hasGitHubScope(
  db: PrismaClient,
  userId: string,
  requiredScope: string,
): Promise<boolean> {
  const conn = await db.connection.findFirst({
    where: { userId, provider: 'github', status: 'connected' },
  });
  if (!conn) return false;
  const scopes = (conn.scopes as string[]) ?? [];
  return scopes.includes(requiredScope);
}
