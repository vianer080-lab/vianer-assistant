import { oauthStatus } from './_shared';

async function sha256(value) {
  const bytes = new TextEncoder().encode(String(value || ''));
  const digest = await crypto.subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
}

export async function GET(request) {
  const configured = Boolean(
    process.env.YOUTUBE_CLIENT_ID && process.env.YOUTUBE_CLIENT_SECRET &&
    process.env.YOUTUBE_REDIRECT_URI && process.env.SUPABASE_URL &&
    process.env.SUPABASE_PUBLISHABLE_KEY && process.env.LIYA_DEVICE_TOKEN
  );
  const connection = configured ? await oauthStatus() : { authorized: false };
  const diagnostic = new URL(request.url).searchParams.get('diagnostic');
  return Response.json({
    ok: true,
    service: 'master-hub-youtube-oauth',
    configured,
    connected: connection.authorized === true,
    channel: connection.metadata || null,
    ...(diagnostic === 'server-device-hash'
      ? { serverDeviceHash: await sha256(process.env.LIYA_DEVICE_TOKEN) }
      : {}),
  });
}
