import { callMasterHubRpc, noStoreJson } from '../_masterHubData';
import { MAX_VIDEO_BYTES, publishYouTubeVideo } from '../youtube/_publisher';
import { publishPinterestVideo } from '../pinterest/_publisher';
import { publishTelegramVideo } from '../telegram/_publisher';

function authorized(request) {
  const expected = String(process.env.LIYA_DEVICE_TOKEN || '');
  const supplied = String(request.headers.get('authorization') || '').replace(/^Bearer\s+/i, '');
  if (!expected || supplied.length !== expected.length) return false;
  let difference = 0;
  for (let i = 0; i < expected.length; i += 1) difference |= expected.charCodeAt(i) ^ supplied.charCodeAt(i);
  return difference === 0;
}

async function loadMedia(url) {
  const storageHeaders = url.startsWith(`${process.env.SUPABASE_URL}/storage/`)
    ? { apikey: process.env.SUPABASE_PUBLISHABLE_KEY, Authorization: `Bearer ${process.env.SUPABASE_PUBLISHABLE_KEY}` }
    : {};
  const response = await fetch(url, { redirect: 'follow', headers: storageHeaders });
  if (!response.ok) throw new Error(`media_download_${response.status}`);
  const declared = Number(response.headers.get('content-length') || 0);
  if (declared > MAX_VIDEO_BYTES) throw new Error('media_too_large');
  const bytes = await response.arrayBuffer();
  if (!bytes.byteLength || bytes.byteLength > MAX_VIDEO_BYTES) throw new Error('invalid_video_size');
  return bytes;
}

export async function POST(request) {
  if (!authorized(request)) return noStoreJson({ ok: false, error: 'unauthorized' }, 401);
  const outcomes = [];
  for (let index = 0; index < 3; index += 1) {
    const claimed = await callMasterHubRpc('master_hub_claim_publication', {});
    const job = claimed?.job;
    if (!job) break;
    try {
      const bytes = await loadMedia(job.media_url);
      const options = job.options || {};
      let result;
      if (job.platform === 'youtube') result = await publishYouTubeVideo({ bytes, title: job.title,
        description: job.description, privacyStatus: job.privacy_status });
      else if (job.platform === 'pinterest') result = await publishPinterestVideo({ bytes, title: job.title,
        description: job.description, destinationUrl: options.destination_url, boardId: options.board_id });
      else if (job.platform === 'telegram') result = await publishTelegramVideo({ bytes, title: job.title,
        description: job.description, destinationUrl: options.destination_url });
      else throw new Error(`platform_not_connected_${job.platform}`);
      await callMasterHubRpc('master_hub_finish_publication', { p_id: job.id, p_success: true,
        p_external_id: result.id, p_url: result.url, p_error: null });
      outcomes.push({ id: job.id, status: 'published', url: result.url });
    } catch (error) {
      const message = String(error?.message || 'publication_failed');
      const finished = await callMasterHubRpc('master_hub_finish_publication', { p_id: job.id,
        p_success: false, p_external_id: null, p_url: null, p_error: message });
      outcomes.push({ id: job.id, status: finished?.status || 'failed', error: message });
    }
  }
  return noStoreJson({ ok: true, processed: outcomes.length, outcomes });
}
