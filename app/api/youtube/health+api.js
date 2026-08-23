import { oauthStatus } from './_shared';

export async function GET() {
  const configured = Boolean(
    process.env.YOUTUBE_CLIENT_ID && process.env.YOUTUBE_CLIENT_SECRET &&
    process.env.YOUTUBE_REDIRECT_URI && process.env.SUPABASE_URL &&
    process.env.SUPABASE_PUBLISHABLE_KEY && process.env.LIYA_DEVICE_TOKEN
  );
  const connection = configured ? await oauthStatus() : { authorized: false };
  return Response.json({
    ok: true,
    service: 'master-hub-youtube-oauth',
    configured,
    connected: connection.authorized === true,
    channel: connection.metadata || null,
  });
}
