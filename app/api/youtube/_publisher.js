import { cleanOAuthValue, loadOAuthToken } from './_shared';

const TOKEN_ENDPOINT = 'https://oauth2.googleapis.com/token';
const UPLOAD_ENDPOINT = 'https://www.googleapis.com/upload/youtube/v3/videos';
export const MAX_VIDEO_BYTES = 25 * 1024 * 1024;

async function refreshAccessToken(token) {
  if (!token?.refresh_token) return token;
  const clientId = cleanOAuthValue(process.env.YOUTUBE_CLIENT_ID, 'client');
  const clientSecret = cleanOAuthValue(process.env.YOUTUBE_CLIENT_SECRET, 'secret');
  if (!clientId || !clientSecret) throw new Error('youtube_oauth_not_configured');
  const response = await fetch(TOKEN_ENDPOINT, {
    method: 'POST', headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({ client_id: clientId, client_secret: clientSecret,
      refresh_token: token.refresh_token, grant_type: 'refresh_token' }).toString(),
  });
  if (!response.ok) throw new Error(`youtube_token_refresh_${response.status}`);
  return { ...token, ...(await response.json()), refresh_token: token.refresh_token };
}

export async function publishYouTubeVideo({ bytes, title, description = '', privacyStatus = 'private' }) {
  if (!bytes?.byteLength || bytes.byteLength > MAX_VIDEO_BYTES) throw new Error('invalid_video_size');
  const storedToken = await loadOAuthToken();
  if (!storedToken) throw new Error('youtube_not_connected');
  const token = await refreshAccessToken(storedToken);
  const session = await fetch(`${UPLOAD_ENDPOINT}?uploadType=resumable&part=snippet,status`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${token.access_token}`, 'Content-Type': 'application/json; charset=UTF-8',
      'X-Upload-Content-Length': String(bytes.byteLength), 'X-Upload-Content-Type': 'video/mp4' },
    body: JSON.stringify({ snippet: { title, description, categoryId: '22' },
      status: { privacyStatus, selfDeclaredMadeForKids: false } }),
  });
  if (!session.ok) throw new Error(`youtube_upload_session_${session.status}`);
  const location = session.headers.get('location');
  if (!location) throw new Error('youtube_upload_location_missing');
  const upload = await fetch(location, { method: 'PUT',
    headers: { 'Content-Type': 'video/mp4', 'Content-Length': String(bytes.byteLength) }, body: bytes });
  const result = await upload.json().catch(() => ({}));
  if (!upload.ok || !result.id) throw new Error(`youtube_upload_${upload.status}`);
  return { id: result.id, url: `https://www.youtube.com/watch?v=${result.id}` };
}
