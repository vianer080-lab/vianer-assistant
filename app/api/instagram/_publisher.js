import { loadConnection } from './_shared';

const API = 'https://graph.instagram.com';
const WAIT_MS = 3000;
const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function instagramError(response, fallback) {
  const body = await response.json().catch(() => ({}));
  const message = String(body?.error?.message || body?.error?.code || fallback).replace(/[\r\n]/g, ' ').slice(0, 240);
  return new Error(`${fallback}_${response.status}${message ? `_${message}` : ''}`);
}

async function api(path, accessToken, options = {}) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: { Authorization: `Bearer ${accessToken}`, ...(options.headers || {}) },
  });
  if (!response.ok) throw await instagramError(response, 'instagram_api');
  return response.json();
}

async function uploadVideo(containerId, bytes, accessToken) {
  const response = await fetch(`https://rupload.facebook.com/ig-api-upload/v1/${encodeURIComponent(containerId)}`, {
    method: 'POST',
    headers: {
      Authorization: `OAuth ${accessToken}`,
      offset: '0',
      file_size: String(bytes.byteLength),
      'Content-Type': 'application/octet-stream',
    },
    body: bytes,
  });
  if (!response.ok) throw await instagramError(response, 'instagram_video_upload');
}

export async function publishInstagramReel({ bytes, title, description = '' }) {
  if (!bytes?.byteLength) throw new Error('invalid_video_size');
  const connection = await loadConnection();
  const accessToken = connection?.token?.access_token;
  const userId = String(connection?.token?.user_id || connection?.metadata?.user_id || '').trim();
  if (!accessToken || !userId) throw new Error('instagram_not_connected');

  const caption = [String(title || '').trim(), String(description || '').trim()].filter(Boolean).join('\n\n').slice(0, 2200);
  const container = await api(`/${encodeURIComponent(userId)}/media`, accessToken, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ media_type: 'REELS', upload_type: 'resumable', caption, share_to_feed: true }),
  });
  if (!container?.id) throw new Error('instagram_container_invalid');
  await uploadVideo(container.id, bytes, accessToken);

  for (let attempt = 0; attempt < 40; attempt += 1) {
    const state = await api(`/${encodeURIComponent(container.id)}?fields=status_code,status`, accessToken);
    if (state?.status_code === 'FINISHED') break;
    if (state?.status_code === 'ERROR' || state?.status_code === 'EXPIRED') {
      throw new Error(`instagram_processing_${String(state.status_code).toLowerCase()}`);
    }
    if (attempt === 39) throw new Error('instagram_processing_timeout');
    await wait(WAIT_MS);
  }

  const published = await api(`/${encodeURIComponent(userId)}/media_publish`, accessToken, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ creation_id: container.id }),
  });
  if (!published?.id) throw new Error('instagram_publish_response_invalid');
  const media = await api(`/${encodeURIComponent(published.id)}?fields=permalink`, accessToken).catch(() => ({}));
  return { id: published.id, url: media?.permalink || `https://www.instagram.com/` };
}
