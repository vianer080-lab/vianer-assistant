const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_KEY = process.env.SUPABASE_PUBLISHABLE_KEY;
const DEVICE_TOKEN = process.env.LIYA_DEVICE_TOKEN;

export async function callMasterHubRpc(name, body) {
  if (!SUPABASE_URL || !SUPABASE_KEY || !DEVICE_TOKEN) {
    throw new Error('Master Hub data backend is not configured');
  }
  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: 'POST',
    headers: {
      apikey: SUPABASE_KEY,
      'Content-Type': 'application/json',
      'Cache-Control': 'no-store',
    },
    body: JSON.stringify({ p_device_token: DEVICE_TOKEN, ...body }),
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.message || `Supabase RPC failed: ${response.status}`);
  return data;
}

export async function callMasterHubRpcWithToken(name, body, deviceToken) {
  if (!SUPABASE_URL || !SUPABASE_KEY || !deviceToken) throw new Error('unauthorized');
  const response = await fetch(`${SUPABASE_URL}/rest/v1/rpc/${name}`, {
    method: 'POST', headers: { apikey: SUPABASE_KEY, 'Content-Type': 'application/json', 'Cache-Control': 'no-store' },
    body: JSON.stringify({ p_device_token: deviceToken, ...body }),
  });
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.message || `Supabase RPC failed: ${response.status}`);
  return data;
}

export function noStoreJson(data, status = 200) {
  return Response.json(data, { status, headers: { 'Cache-Control': 'no-store' } });
}
