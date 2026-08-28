import { loadToken } from './_shared';

const API = 'https://api.pinterest.com/v5';
const MAX_DESCRIPTION = 800;
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

function basicAuth(clientId, clientSecret) {
  const bytes = new TextEncoder().encode(`${clientId}:${clientSecret}`);
  let binary = '';
  for (const byte of bytes) binary += String.fromCharCode(byte);
  return btoa(binary);
}

async function pinterestError(response, fallback) {
  const body = await response.json().catch(() => ({}));
  const message = String(body?.message || body?.code || fallback).replace(/[\r\n]/g, ' ').slice(0, 240);
  return new Error(`${fallback}_${response.status}${message ? `_${message}` : ''}`);
}

async function refreshToken(token) {
  const storedAt = Date.parse(token?.stored_at || '');
  const expiresIn = Number(token?.expires_in || 0) * 1000;
  if (token?.access_token && (!expiresIn || !storedAt || Date.now() < storedAt + expiresIn - 300000)) return token;
  if (!token?.refresh_token) return token;
  const clientId = String(process.env.PINTEREST_APP_ID || '').trim();
  const clientSecret = String(process.env.PINTEREST_APP_SECRET || '').trim();
  if (!clientId || !clientSecret) throw new Error('pinterest_oauth_not_configured');
  const response = await fetch(`${API}/oauth/token`, {
    method: 'POST',
    headers: { Authorization: `Basic ${basicAuth(clientId, clientSecret)}`, 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ grant_type: 'refresh_token', refresh_token: token.refresh_token }).toString(),
  });
  if (!response.ok) throw await pinterestError(response, 'pinterest_token_refresh');
  return { ...token, ...(await response.json()), refresh_token: token.refresh_token, stored_at: new Date().toISOString() };
}

async function authToken() {
  const stored = await loadToken();
  if (!stored) throw new Error('pinterest_not_connected');
  const token = await refreshToken(stored);
  if (!token?.access_token) throw new Error('pinterest_access_token_missing');
  return token.access_token;
}

async function api(path, accessToken, options = {}) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: { Authorization: `Bearer ${accessToken}`, ...(options.headers || {}) },
  });
  if (!response.ok) throw await pinterestError(response, 'pinterest_api');
  return response.json();
}

export async function listPinterestBoards() {
  const accessToken = await authToken();
  const result = await api('/boards?page_size=100', accessToken);
  return (result?.items || []).map(({ id, name, privacy }) => ({ id, name, privacy }));
}

async function uploadVideo(bytes, accessToken) {
  const registration = await api('/media', accessToken, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ media_type: 'video' }),
  });
  if (!registration?.media_id || !registration?.upload_url) throw new Error('pinterest_media_registration_invalid');
  const form = new FormData();
  for (const [key, value] of Object.entries(registration.upload_parameters || {})) form.append(key, String(value));
  form.append('file', new Blob([bytes], { type: 'video/mp4' }), 'video.mp4');
  const upload = await fetch(registration.upload_url, { method: 'POST', body: form });
  if (!upload.ok) throw new Error(`pinterest_media_upload_${upload.status}`);

  for (let attempt = 0; attempt < 12; attempt += 1) {
    const media = await api(`/media/${registration.media_id}`, accessToken);
    if (media?.status === 'succeeded') return registration.media_id;
    if (media?.status === 'failed') throw new Error('pinterest_media_processing_failed');
    await wait(3000);
  }
  throw new Error('pinterest_media_processing_timeout');
}

export async function publishPinterestVideo({ bytes, title, description = '', destinationUrl, boardId }) {
  if (!bytes?.byteLength) throw new Error('invalid_video_size');
  if (!/^https:\/\//i.test(String(destinationUrl || ''))) throw new Error('pinterest_destination_required');
  const accessToken = await authToken();
  let selectedBoard = String(boardId || process.env.PINTEREST_BOARD_ID || '').trim();
  if (!selectedBoard) {
    const boards = await api('/boards?page_size=100', accessToken);
    selectedBoard = String(boards?.items?.[0]?.id || '');
  }
  if (!selectedBoard) throw new Error('pinterest_board_required');
  const mediaId = await uploadVideo(bytes, accessToken);
  const pin = await api('/pins', accessToken, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      board_id: selectedBoard,
      title: String(title || '').slice(0, 100),
      description: String(description || '').slice(0, MAX_DESCRIPTION),
      link: String(destinationUrl).slice(0, 2048),
      media_source: { source_type: 'video_id', media_id: mediaId, cover_image_key_frame_time: 0 },
    }),
  });
  if (!pin?.id) throw new Error('pinterest_pin_response_invalid');
  return { id: pin.id, url: `https://www.pinterest.com/pin/${pin.id}` };
}
