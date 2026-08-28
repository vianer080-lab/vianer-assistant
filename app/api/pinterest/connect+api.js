import { createState } from './_shared';

export async function GET() {
  const clientId = String(process.env.PINTEREST_APP_ID || '').trim();
  const redirectUri = String(process.env.PINTEREST_REDIRECT_URI || '').trim();
  if (!clientId || !redirectUri || !process.env.PINTEREST_APP_SECRET) {
    return new Response('Pinterest OAuth server configuration is incomplete.', { status: 503 });
  }
  const params = new URLSearchParams({
    client_id: clientId,
    redirect_uri: redirectUri,
    response_type: 'code',
    scope: 'boards:read,boards:write,pins:read,pins:write,user_accounts:read',
    state: await createState(),
  });
  return Response.redirect(`https://www.pinterest.com/oauth/?${params.toString()}`, 302);
}
