import { store, validState } from './_shared';

export async function GET(request) {
  const url = new URL(request.url);
  const code = url.searchParams.get('code');
  const state = url.searchParams.get('state');
  const error = url.searchParams.get('error');
  if (error) return new Response(`Instagram authorization failed: ${error}`, { status: 400 });
  if (!code || !(await validState(state))) return new Response('Invalid or expired Instagram authorization.', { status: 400 });

  const clientId = String(process.env.INSTAGRAM_APP_ID || '').trim();
  const clientSecret = String(process.env.INSTAGRAM_APP_SECRET || '').trim();
  const redirectUri = String(process.env.INSTAGRAM_REDIRECT_URI || '').trim();
  const body = new URLSearchParams({
    client_id: clientId,
    client_secret: clientSecret,
    grant_type: 'authorization_code',
    redirect_uri: redirectUri,
    code,
  });
  const tokenResponse = await fetch('https://api.instagram.com/oauth/access_token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  });
  if (!tokenResponse.ok) {
    const message = (await tokenResponse.text()).replace(/[<>&"]/g, '').slice(0, 300);
    return new Response(`Instagram token exchange failed: ${message}`, { status: 502 });
  }
  const shortToken = await tokenResponse.json();
  const longResponse = await fetch(
    `https://graph.instagram.com/access_token?grant_type=ig_exchange_token&client_secret=${encodeURIComponent(clientSecret)}&access_token=${encodeURIComponent(shortToken.access_token)}`
  );
  const token = longResponse.ok ? await longResponse.json() : shortToken;
  const accessToken = token.access_token || shortToken.access_token;
  const profileResponse = await fetch(
    `https://graph.instagram.com/me?fields=user_id,username,account_type,media_count&access_token=${encodeURIComponent(accessToken)}`
  );
  if (!profileResponse.ok) return new Response('Could not verify the Instagram account.', { status: 502 });
  const profile = await profileResponse.json();
  const stored = await store({ ...token, access_token: accessToken }, profile);
  if (!stored) return new Response('The Instagram permission could not be saved securely.', { status: 502 });
  return new Response(
    `<!doctype html><html lang="ru"><meta name="viewport" content="width=device-width"><body style="font-family:sans-serif;background:#0b1220;color:white;padding:32px"><h2>Instagram подключён</h2><p>Аккаунт @${String(profile.username || 'Instagram').replace(/[<>&"]/g, '')} подключён к Master Hub. Можно закрыть страницу.</p></body></html>`,
    { headers: { 'Content-Type': 'text/html; charset=utf-8' } }
  );
}
