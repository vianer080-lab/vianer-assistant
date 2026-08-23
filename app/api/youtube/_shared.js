const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_KEY = process.env.SUPABASE_PUBLISHABLE_KEY;

function bytesToBase64Url(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

async function hmac(value, secret) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey(
    'raw', encoder.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']
  );
  return bytesToBase64Url(new Uint8Array(await crypto.subtle.sign('HMAC', key, encoder.encode(value))));
}

export async function createOAuthState() {
  const secret = process.env.YOUTUBE_CLIENT_SECRET;
  const payload = `${Date.now()}.${crypto.randomUUID()}`;
  return `${payload}.${await hmac(payload, secret)}`;
}

export async function isValidOAuthState(state) {
  if (!state || !process.env.YOUTUBE_CLIENT_SECRET) return false;
  const parts = state.split('.');
  if (parts.length !== 3 || Date.now() - Number(parts[0]) > 10 * 60 * 1000) return false;
  const payload = `${parts[0]}.${parts[1]}`;
  const expected = await hmac(payload, process.env.YOUTUBE_CLIENT_SECRET);
  if (expected.length !== parts[2].length) return false;
  let difference = 0;
  for (let i = 0; i < expected.length; i += 1) difference |= expected.charCodeAt(i) ^ parts[2].charCodeAt(i);
  return difference === 0;
}

export async function oauthStatus() {
  if (!SUPABASE_URL || !SUPABASE_KEY || !process.env.LIYA_DEVICE_TOKEN) return { authorized: false };
  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/master_hub_oauth_status`, {
    method: 'POST',
    headers: { apikey: SUPABASE_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({ p_device_token: process.env.LIYA_DEVICE_TOKEN, p_service: 'youtube' }),
  });
  return response.ok ? response.json() : { authorized: false };
}

export async function storeOAuth(token, metadata) {
  if (!SUPABASE_URL || !SUPABASE_KEY || !process.env.LIYA_DEVICE_TOKEN) return false;
  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/master_hub_store_oauth`, {
    method: 'POST',
    headers: { apikey: SUPABASE_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      p_device_token: process.env.LIYA_DEVICE_TOKEN,
      p_service: 'youtube',
      p_token: token,
      p_metadata: metadata,
    }),
  });
  return response.ok && (await response.json()) === true;
}

