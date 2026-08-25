import { createState } from './_shared';

export async function GET() {
  const clientId = String(process.env.INSTAGRAM_APP_ID || '').trim();
  const redirectUri = String(process.env.INSTAGRAM_REDIRECT_URI || '').trim();
  if (!clientId || !redirectUri || !process.env.INSTAGRAM_APP_SECRET) {
    return new Response('Instagram OAuth server configuration is incomplete.', { status: 503 });
  }
  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: 'instagram_business_basic,instagram_business_content_publish',
    enable_fb_login: '0',
    force_authentication: '1',
    state: await createState(),
  });
  return Response.redirect(`https://www.instagram.com/oauth/authorize?${params.toString()}`, 302);
}
