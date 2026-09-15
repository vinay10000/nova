// Throwaway: inspect live Connection / OAuthState rows (no secrets printed).
import pg from 'pg';
const { Client } = pg;
const c = new Client({ connectionString: process.env.DATABASE_URL });
await c.connect();
const conns = await c.query('SELECT id, "userId", provider, status, scopes, "providerLogin", "createdAt", "lastRefreshedAt" FROM "Connection" ORDER BY "createdAt" DESC LIMIT 10');
console.log('connections:', conns.rows);
const states = await c.query('SELECT provider, "userId", "expiresAt", "createdAt" FROM "OAuthState" ORDER BY "createdAt" DESC LIMIT 10');
console.log('oauth states:', states.rows);
const users = await c.query('SELECT id, email, "createdAt" FROM "User" ORDER BY "createdAt" DESC LIMIT 10');
console.log('users:', users.rows);
await c.end();
