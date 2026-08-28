const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_KEY = process.env.SUPABASE_PUBLISHABLE_KEY;

function base64Url(bytes) {
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/g, '');
}

async function signature(value, secret) {
  const encoder = new TextEncoder();
  const key = await crypto.subtle.importKey('raw', encoder.encode(secret), { name: 'HMAC', hash: 'SHA-256' }, false, ['sign']);
  return base64Url(new Uint8Array(await crypto.subtle.sign('HMAC', key, encoder.encode(value))));
}

export async function createState() {
  const secret = String(process.env.PINTEREST_APP_SECRET || '').trim();
  const payload = `${Date.now()}.${crypto.randomUUID()}`;
  return `${payload}.${await signature(payload, secret)}`;
}

export async function validState(state) {
  const secret = String(process.env.PINTEREST_APP_SECRET || '').trim();
  if (!state || !secret) return false;
  const parts = state.split('.');
  if (parts.length !== 3 || Date.now() - Number(parts[0]) > 10 * 60 * 1000) return false;
  const expected = await signature(`${parts[0]}.${parts[1]}`, secret);
  if (expected.length !== parts[2].length) return false;
  let difference = 0;
  for (let i = 0; i < expected.length; i += 1) difference |= expected.charCodeAt(i) ^ parts[2].charCodeAt(i);
  return difference === 0;
}

export async function status() {
  if (!SUPABASE_URL || !SUPABASE_KEY || !process.env.LIYA_DEVICE_TOKEN) return { authorized: false };
  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/master_hub_oauth_status`, {
    method: 'POST',
    headers: { apikey: SUPABASE_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({ p_device_token: process.env.LIYA_DEVICE_TOKEN, p_service: 'pinterest' }),
  });
  return response.ok ? response.json() : { authorized: false };
}

export async function store(token, metadata) {
  if (!SUPABASE_URL || !SUPABASE_KEY || !process.env.LIYA_DEVICE_TOKEN) return false;
  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/master_hub_store_oauth`, {
    method: 'POST',
    headers: { apikey: SUPABASE_KEY, 'Content-Type': 'application/json' },
    body: JSON.stringify({
      p_device_token: process.env.LIYA_DEVICE_TOKEN,
      p_service: 'pinterest',
      p_token: token,
      p_metadata: metadata,
    }),
  });
  return response.ok && (await response.json()) === true;
}

export async function loadToken() {
  if (!SUPABASE_URL || !SUPABASE_KEY || !process.env.LIYA_DEVICE_TOKEN) return null;
  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/master_hub_get_oauth_token`, {
    method: 'POST',
    headers: { apikey: SUPABASE_KEY, 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
    body: JSON.stringify({ p_device_token: process.env.LIYA_DEVICE_TOKEN, p_service: 'pinterest' }),
  });
  if (!response.ok) return null;
  const result = await response.json();
  return result?.authorized === true ? result.token : null;
}
