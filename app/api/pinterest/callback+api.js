import { store, validState } from './_shared';

function basicAuth(clientId, clientSecret) {
  const bytes = new TextEncoder().encode(`${clientId}:${clientSecret}`);
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

export async function GET(request) {
  const url = new URL(request.url);
  const code = url.searchParams.get('code');
  const state = url.searchParams.get('state');
  const error = url.searchParams.get('error');
  if (error) return new Response(`Pinterest authorization failed: ${error}`, { status: 400 });
  if (!code || !(await validState(state))) return new Response('Invalid or expired Pinterest authorization.', { status: 400 });

  const clientId = String(process.env.PINTEREST_APP_ID || '').trim();
  const clientSecret = String(process.env.PINTEREST_APP_SECRET || '').trim();
  const redirectUri = String(process.env.PINTEREST_REDIRECT_URI || '').trim();
  const body = new URLSearchParams({ grant_type: 'authorization_code', code, redirect_uri: redirectUri });
  const tokenResponse = await fetch('https://api.pinterest.com/v5/oauth/token', {
    method: 'POST',
    headers: {
      Authorization: `Basic ${basicAuth(clientId, clientSecret)}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: body.toString(),
  });
  if (!tokenResponse.ok) {
    const message = (await tokenResponse.text()).replace(/[<>&"]/g, '').slice(0, 300);
    return new Response(`Pinterest token exchange failed: ${message}`, { status: 502 });
  }
  const token = { ...(await tokenResponse.json()), stored_at: new Date().toISOString() };
  const profileResponse = await fetch('https://api.pinterest.com/v5/user_account', {
    headers: { Authorization: `Bearer ${token.access_token}` },
  });
  if (!profileResponse.ok) return new Response('Could not verify the Pinterest account.', { status: 502 });
  const profile = await profileResponse.json();
  if (!(await store(token, profile))) return new Response('The Pinterest permission could not be saved securely.', { status: 502 });
  return new Response(
    `<!doctype html><html lang="ru"><meta name="viewport" content="width=device-width"><body style="font-family:sans-serif;background:#0b1220;color:white;padding:32px"><h2>Pinterest подключён</h2><p>Аккаунт ${String(profile.username || 'Pinterest').replace(/[<>&"]/g, '')} подключён к Master Hub. Можно закрыть страницу.</p></body></html>`,
    { headers: { 'Content-Type': 'text/html; charset=utf-8' } }
  );
}
