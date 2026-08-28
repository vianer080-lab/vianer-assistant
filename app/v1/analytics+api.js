import { callMasterHubRpc, noStoreJson } from '../api/_masterHubData';

export async function GET(request) {
  try {
    const days = Math.min(30, Math.max(1, Number(new URL(request.url).searchParams.get('days')) || 1));
    const data = await callMasterHubRpc('master_hub_analytics', { p_days: days });
    return noStoreJson(data);
  } catch (error) {
    return noStoreJson({ ok: false, error: error.message }, 503);
  }
}

