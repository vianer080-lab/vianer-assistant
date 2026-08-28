import { callMasterHubRpcWithToken,noStoreJson } from '../_masterHubData';

const SUPABASE_URL=process.env.SUPABASE_URL;
const SUPABASE_KEY=process.env.SUPABASE_PUBLISHABLE_KEY;
const MAX_BYTES=25*1024*1024;

export async function POST(request){
 const token=String(request.headers.get('authorization')||'').replace(/^Bearer\s+/i,'');
 if(!token)return noStoreJson({ok:false,error:'unauthorized'},401);
 try{
  const form=await request.formData();const file=form.get('video');const title=String(form.get('title')||'').trim().slice(0,100);const description=String(form.get('description')||'').slice(0,5000);const privacyStatus=String(form.get('privacyStatus')||'private');const scheduledAt=new Date(String(form.get('scheduledAt')||new Date().toISOString()));
  if(!file||typeof file.arrayBuffer!=='function'||!title||!Number.isFinite(scheduledAt.getTime()))return noStoreJson({ok:false,error:'invalid_publication'},400);
  const bytes=await file.arrayBuffer();if(!bytes.byteLength||bytes.byteLength>MAX_BYTES)return noStoreJson({ok:false,error:'invalid_video_size'},413);
  const expiresAt=new Date(Math.max(Date.now()+3600000,scheduledAt.getTime()+86400000)).toISOString();
  const grant=await callMasterHubRpcWithToken('master_hub_create_upload_grant',{p_expires_at:expiresAt},token);if(!grant?.ok)throw new Error(grant?.error||'upload_grant_failed');
  const name=`${grant.uploadToken}/${crypto.randomUUID()}.mp4`;
  const upload=await fetch(`${SUPABASE_URL}/storage/v1/object/master-hub-publications/${name}`,{method:'POST',headers:{apikey:SUPABASE_KEY,Authorization:`Bearer ${SUPABASE_KEY}`,'Content-Type':'video/mp4','x-upsert':'false'},body:bytes});
  if(!upload.ok)throw new Error(`video_storage_${upload.status}`);
  const mediaUrl=`${SUPABASE_URL}/storage/v1/object/authenticated/master-hub-publications/${name}`;
  const queued=await callMasterHubRpcWithToken('master_hub_enqueue_publication',{p_platform:'youtube',p_title:title,p_description:description,p_media_url:mediaUrl,p_privacy_status:privacyStatus,p_scheduled_at:scheduledAt.toISOString()},token);
  return noStoreJson(queued,queued?.ok?200:400);
 }catch(error){return noStoreJson({ok:false,error:String(error?.message||'create_publication_failed')},502)}
}
