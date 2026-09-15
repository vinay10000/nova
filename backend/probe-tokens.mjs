// Throwaway diagnostic: decrypt stored connection tokens locally and test them
// against the provider APIs. Prints status only — never tokens.
import pg from 'pg';
import { createDecipheriv } from 'node:crypto';

const { Client } = pg;
const c = new Client({ connectionString: process.env.DATABASE_URL });
await c.connect();

function key() {
  const k = process.env.OAUTH_ENCRYPTION_KEY ?? '';
  return /^[0-9a-f]{64}$/i.test(k) ? Buffer.from(k, 'hex') : Buffer.from(k, 'base64');
}
function decrypt(encoded) {
  const buf = Buffer.from(encoded, 'base64');
  const iv = buf.subarray(0, 12);
  const tag = buf.subarray(12, 28);
  const ct = buf.subarray(28);
  const d = createDecipheriv('aes-256-gcm', key(), iv, { authTagLength: 16 });
  d.setAuthTag(tag);
  return Buffer.concat([d.update(ct), d.final()]).toString('utf8');
}

const rows = (await c.query('SELECT id, "userId", provider, status, "encryptedToken" FROM "Connection" ORDER BY "createdAt" DESC LIMIT 5')).rows;

for (const r of rows) {
  let plain;
  try { plain = decrypt(r.encryptedToken); } catch (e) { console.log(r.provider, 'DECRYPT FAILED:', e.message); continue; }
  console.log(`\n${r.provider}: decrypt OK, plaintext length ${plain.length}`);

  if (r.provider === 'github') {
    const res = await fetch('https://api.github.com/user', {
      headers: { Authorization: `Bearer ${plain}`, Accept: 'application/vnd.github+json', 'User-Agent': 'Nova-Probe' },
    });
    console.log('  GET /user ->', res.status, 'scopes:', res.headers.get('x-oauth-scopes') ?? '(none)');
    if (!res.ok) console.log('  body:', (await res.text()).slice(0, 200));
    else console.log('  login:', (await res.json()).login);
  } else {
    let blob;
    try { blob = JSON.parse(plain); } catch { console.log('  blob unparseable:', plain.slice(0, 40)); continue; }
    console.log('  expiry:', new Date(blob.expiry).toISOString(), 'expired:', blob.expiry < Date.now(), 'hasRefresh:', !!blob.refresh_token);
    let access = blob.access_token;
    if (blob.expiry < Date.now()) {
      const res = await fetch('https://oauth2.googleapis.com/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({
          client_id: process.env.GOOGLE_CLIENT_ID ?? '',
          client_secret: process.env.GOOGLE_CLIENT_SECRET ?? '',
          refresh_token: blob.refresh_token,
          grant_type: 'refresh_token',
        }),
      });
      const refreshText = await res.text();
      console.log('  refresh ->', res.status, res.ok ? 'ok' : refreshText.slice(0, 200));
      if (!res.ok) continue;
      access = JSON.parse(refreshText).access_token;
    }
    const prof = await fetch('https://gmail.googleapis.com/gmail/v1/users/me/profile', { headers: { Authorization: `Bearer ${access}` } });
    console.log('  gmail profile ->', prof.status, (await prof.text()).slice(0, 200));
  }
}
await c.end();