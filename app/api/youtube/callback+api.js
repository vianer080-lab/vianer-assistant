const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
import { cleanOAuthValue, isValidOAuthState, storeOAuth } from './_shared';

function safeOAuthError(payload) {
  const code = String(payload?.error || 'unknown_error').replace(/[^a-z0-9_.-]/gi, '');
  const description = String(payload?.error_description || '')
    .replace(/[<>&"]/g, '')
    .slice(0, 300);
  return description ? `${code}: ${description}` : code;
}

export async function GET(request) {
  const url = new URL(request.url);
  const code = url.searchParams.get('code');
  const error = url.searchParams.get('error');
  const state = url.searchParams.get('state');

  if (error) {
    return new Response(`YouTube authorization failed: ${error}`, { status: 400 });
  }

  if (!code) {
    return new Response('Missing OAuth authorization code.', { status: 400 });
  }
  if (!(await isValidOAuthState(state))) {
    return new Response('Invalid or expired OAuth state.', { status: 400 });
  }

  const clientId = cleanOAuthValue(process.env.YOUTUBE_CLIENT_ID, 'client');
  const clientSecret = cleanOAuthValue(process.env.YOUTUBE_CLIENT_SECRET, 'secret');
  const redirectUri = process.env.YOUTUBE_REDIRECT_URI;

  if (!clientId || !clientSecret || !redirectUri) {
    return new Response('YouTube OAuth server configuration is incomplete.', { status: 500 });
  }

  const body = new URLSearchParams({
    code,
    client_id: clientId,
    client_secret: clientSecret,
    redirect_uri: redirectUri,
    grant_type: 'authorization_code',
  });

  const tokenResponse = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: body.toString(),
  });

  if (!tokenResponse.ok) {
    let payload = {};
    try {
      payload = await tokenResponse.json();
    } catch {
      payload = { error: `http_${tokenResponse.status}` };
    }
    return new Response(`YouTube token exchange failed: ${safeOAuthError(payload)}`, {
      status: 502,
      headers: { 'Content-Type': 'text/plain; charset=utf-8' },
    });
  }

  const token = await tokenResponse.json();
  const channelResponse = await fetch(
    'https://www.googleapis.com/youtube/v3/channels?part=id,snippet&mine=true',
    { headers: { Authorization: `Bearer ${token.access_token}` } }
  );
  if (!channelResponse.ok) return new Response('Could not verify the YouTube channel.', { status: 502 });
  const channelData = await channelResponse.json();
  const channel = channelData.items?.[0];
  if (!channel) return new Response('No YouTube channel was found for this Google account.', { status: 400 });

  const stored = await storeOAuth(token, {
    channelId: channel.id,
    title: channel.snippet?.title || 'YouTube',
    thumbnail: channel.snippet?.thumbnails?.default?.url || null,
  });
  if (!stored) return new Response('The YouTube permission could not be saved securely.', { status: 502 });

  return new Response(
    `<!doctype html><html lang="ru"><meta name="viewport" content="width=device-width"><body style="font-family:sans-serif;background:#0b1220;color:white;padding:32px"><h2>YouTube подключён</h2><p>Канал «${String(channel.snippet?.title || 'YouTube').replace(/[<>&"]/g, '')}» подключён к Master Hub. Можно закрыть эту страницу и вернуться в приложение.</p></body></html>`,
    { headers: { 'Content-Type': 'text/html; charset=utf-8' } }
  );
}
