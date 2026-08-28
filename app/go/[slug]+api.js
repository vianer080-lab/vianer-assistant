import { callMasterHubRpc } from '../api/_masterHubData';

function referrerHost(request) {
  try { return new URL(request.headers.get('referer') || '').hostname.slice(0, 200); }
  catch { return ''; }
}

export async function GET(request, { slug }) {
  try {
    const result = await callMasterHubRpc('master_hub_record_click', {
      p_slug: String(slug || '').toLowerCase(),
      p_referrer_host: referrerHost(request),
    });
    if (!result?.destination) return new Response('Link not found', { status: 404 });
    return Response.redirect(result.destination, 302);
  } catch {
    return new Response('Link temporarily unavailable', { status: 503 });
  }
}

