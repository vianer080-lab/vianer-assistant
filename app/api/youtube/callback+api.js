const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';

export async function GET(request) {
  const url = new URL(request.url);
  const code = url.searchParams.get('code');
  const error = url.searchParams.get('error');

  if (error) {
    return new Response(`YouTube authorization failed: ${error}`, { status: 400 });
  }

  if (!code) {
    return new Response('Missing OAuth authorization code.', { status: 400 });
  }

  const clientId = process.env.YOUTUBE_CLIENT_ID;
  const clientSecret = process.env.YOUTUBE_CLIENT_SECRET;
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
    return new Response('YouTube token exchange failed.', { status: 502 });
  }

  // Tokens deliberately remain server-side. Persistent encrypted token storage
  // will be connected before enabling unattended YouTube actions.
  return new Response(
    '<!doctype html><html><body><h2>YouTube connected to Master Hub.</h2><p>You can close this window and return to the app.</p></body></html>',
    { headers: { 'Content-Type': 'text/html; charset=utf-8' } }
  );
}
