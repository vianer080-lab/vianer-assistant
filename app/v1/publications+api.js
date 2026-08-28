import { callMasterHubRpc, noStoreJson } from '../api/_masterHubData';

export async function GET() {
  try {
    const data = await callMasterHubRpc('master_hub_publications', { p_limit: 100 });
    return noStoreJson(data);
  } catch (error) {
    return noStoreJson({ ok: false, error: error.message }, 503);
  }
}

