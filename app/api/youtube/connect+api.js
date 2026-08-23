import { cleanOAuthValue, createOAuthState } from './_shared';

export async function GET() {
  const clientId = cleanOAuthValue(process.env.YOUTUBE_CLIENT_ID, 'client');
  const redirectUri = process.env.YOUTUBE_REDIRECT_URI;
  if (!clientId || !redirectUri || !process.env.YOUTUBE_CLIENT_SECRET) {
    return new Response('YouTube OAuth server configuration is incomplete.', { status: 503 });
  }

  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: 'https://www.googleapis.com/auth/youtube.readonly https://www.googleapis.com/auth/youtube.upload',
    access_type: 'offline',
    include_granted_scopes: 'true',
    prompt: 'consent',
    state: await createOAuthState(),
  });
  return Response.redirect(`https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`, 302);
}
