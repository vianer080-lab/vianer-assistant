import {
  recordYouTubePublication,
} from './_shared';
import { MAX_VIDEO_BYTES, publishYouTubeVideo } from './_publisher';

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
    const result = await publishYouTubeVideo({ bytes, title, description, privacyStatus });
    await recordYouTubePublication({ id: result.id, title, url: result.url, status: 'published' });
    return json({ ok: true, ...result, privacyStatus });
  } catch (error) {
    await recordYouTubePublication({ title, status: 'failed' }).catch(() => false);
    return json({ ok: false, error: String(error?.message || 'youtube_upload_failed') }, 502);
  }
}
