import {
  cleanOAuthValue,
  loadOAuthToken,
  recordYouTubePublication,
} from './_shared';

const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const UPLOAD_ENDPOINT = 'https://www.googleapis.com/upload/youtube/v3/videos';
const MAX_VIDEO_BYTES = 25 * 1024 * 1024;

function json(data, status = 200) {
  return Response.json(data, { status, headers: { 'Cache-Control': 'no-store' } });
}

function authorized(request) {
  const expected = String(process.env.LIYA_DEVICE_TOKEN || '');
  const supplied = String(request.headers.get('authorization') || '').replace(/^Bearer\s+/i, '');
  if (!expected || supplied.length !== expected.length) return false;
  let difference = 0;
  for (let i = 0; i < expected.length; i += 1) {
    difference |= expected.charCodeAt(i) ^ supplied.charCodeAt(i);
  }
  return difference === 0;
}

async function refreshAccessToken(token) {
  if (!token?.refresh_token) return token;
  const clientId = cleanOAuthValue(process.env.YOUTUBE_CLIENT_ID, 'client');
  const clientSecret = cleanOAuthValue(process.env.YOUTUBE_CLIENT_SECRET, 'secret');
  if (!clientId || !clientSecret) throw new Error('youtube_oauth_not_configured');

  const response = await fetch(TOKEN_ENDPOINT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      client_id: clientId,
      client_secret: clientSecret,
      refresh_token: token.refresh_token,
      grant_type: 'refresh_token',
    }).toString(),
  });
  if (!response.ok) throw new Error(`youtube_token_refresh_${response.status}`);
  const refreshed = await response.json();
  return { ...token, ...refreshed, refresh_token: token.refresh_token };
}

async function startResumableUpload(accessToken, metadata, size) {
  const response = await fetch(`${UPLOAD_ENDPOINT}?uploadType=resumable&part=snippet,status`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${accessToken}`,
      'Content-Type': 'application/json; charset=UTF-8',
      'X-Upload-Content-Length': String(size),
      'X-Upload-Content-Type': 'video/mp4',
    },
    body: JSON.stringify(metadata),
  });
  if (!response.ok) throw new Error(`youtube_upload_session_${response.status}`);
  const location = response.headers.get('location');
  if (!location) throw new Error('youtube_upload_location_missing');
  return location;
}

export async function POST(request) {
  if (!authorized(request)) return json({ ok: false, error: 'unauthorized' }, 401);

  const url = new URL(request.url);
  const title = String(url.searchParams.get('title') || '').trim().slice(0, 100);
  const description = String(url.searchParams.get('description') || '').trim().slice(0, 5000);
  const privacyStatus = ['private', 'unlisted', 'public'].includes(url.searchParams.get('privacyStatus'))
    ? url.searchParams.get('privacyStatus')
    : 'private';
  if (!title) return json({ ok: false, error: 'title_required' }, 400);

  const bytes = await request.arrayBuffer();
  if (!bytes.byteLength || bytes.byteLength > MAX_VIDEO_BYTES) {
    return json({ ok: false, error: 'invalid_video_size', maxBytes: MAX_VIDEO_BYTES }, 413);
  }

  try {
    const storedToken = await loadOAuthToken();
    if (!storedToken) return json({ ok: false, error: 'youtube_not_connected' }, 409);
    const token = await refreshAccessToken(storedToken);
    const metadata = {
      snippet: { title, description, categoryId: '22' },
      status: { privacyStatus, selfDeclaredMadeForKids: false },
    };
    const location = await startResumableUpload(token.access_token, metadata, bytes.byteLength);
    const uploadResponse = await fetch(location, {
      method: 'PUT',
      headers: { 'Content-Type': 'video/mp4', 'Content-Length': String(bytes.byteLength) },
      body: bytes,
    });
    const result = await uploadResponse.json().catch(() => ({}));
    if (!uploadResponse.ok || !result.id) {
      await recordYouTubePublication({ title, status: 'failed' });
      return json({ ok: false, error: `youtube_upload_${uploadResponse.status}` }, 502);
    }

    const videoUrl = `https://www.youtube.com/watch?v=${result.id}`;
    await recordYouTubePublication({ id: result.id, title, url: videoUrl, status: 'published' });
    return json({ ok: true, id: result.id, url: videoUrl, privacyStatus });
  } catch (error) {
    await recordYouTubePublication({ title, status: 'failed' }).catch(() => false);
    return json({ ok: false, error: String(error?.message || 'youtube_upload_failed') }, 502);
  }
}
