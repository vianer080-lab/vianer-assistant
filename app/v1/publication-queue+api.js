import { callMasterHubRpc, noStoreJson } from '../api/_masterHubData';

export async function GET() {
  try { return noStoreJson(await callMasterHubRpc('master_hub_publication_queue', { p_limit: 100 })); }
  catch (error) { return noStoreJson({ ok: false, error: error.message }, 503); }
}

