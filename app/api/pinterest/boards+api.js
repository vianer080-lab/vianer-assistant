import { listPinterestBoards } from './_publisher';
import { noStoreJson } from '../_masterHubData';

function authorized(request) {
  const expected = String(process.env.LIYA_DEVICE_TOKEN || '');
  const supplied = String(request.headers.get('authorization') || '').replace(/^Bearer\s+/i, '');
  if (!expected || supplied.length !== expected.length) return false;
  let difference = 0;
  for (let i = 0; i < expected.length; i += 1) difference |= expected.charCodeAt(i) ^ supplied.charCodeAt(i);
  return difference === 0;
}

export async function GET(request) {
  if (!authorized(request)) return noStoreJson({ ok: false, error: 'unauthorized' }, 401);
  try { return noStoreJson({ ok: true, items: await listPinterestBoards() }); }
  catch (error) { return noStoreJson({ ok: false, error: String(error?.message || 'pinterest_boards_failed') }, 502); }
}
