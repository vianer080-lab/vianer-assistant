import { status } from './_shared';

export async function GET() {
  const configured = Boolean(
    process.env.PINTEREST_APP_ID && process.env.PINTEREST_APP_SECRET &&
    process.env.PINTEREST_REDIRECT_URI && process.env.SUPABASE_URL &&
    process.env.SUPABASE_PUBLISHABLE_KEY && process.env.LIYA_DEVICE_TOKEN
  );
  const connection = configured ? await status() : { authorized: false };
  return Response.json({
    ok: true,
    service: 'master-hub-pinterest-oauth',
    configured,
    connected: connection.authorized === true,
    account: connection.metadata || null,
  });
}
