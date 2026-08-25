const SUPABASE_URL = process.env.SUPABASE_URL;
const SUPABASE_KEY = process.env.SUPABASE_PUBLISHABLE_KEY;

function b64(bytes){let s='';for(const b of bytes)s+=String.fromCharCode(b);return btoa(s).replace(/\+/g,'-').replace(/\//g,'_').replace(/=+$/g,'');}
async function sign(value){const e=new TextEncoder();const k=await crypto.subtle.importKey('raw',e.encode(process.env.META_APP_SECRET||''),{name:'HMAC',hash:'SHA-256'},false,['sign']);return b64(new Uint8Array(await crypto.subtle.sign('HMAC',k,e.encode(value))));}
export async function createState(){const p=`${Date.now()}.${crypto.randomUUID()}`;return `${p}.${await sign(p)}`;}
export async function validState(state){if(!state||!process.env.META_APP_SECRET)return false;const p=state.split('.');if(p.length!==3||Date.now()-Number(p[0])>600000)return false;const x=await sign(`${p[0]}.${p[1]}`);if(x.length!==p[2].length)return false;let d=0;for(let i=0;i<x.length;i++)d|=x.charCodeAt(i)^p[2].charCodeAt(i);return d===0;}
export async function status(){if(!SUPABASE_URL||!SUPABASE_KEY||!process.env.LIYA_DEVICE_TOKEN)return{authorized:false};const r=await fetch(`${SUPABASE_URL}/rest/v1/rpc/master_hub_oauth_status`,{method:'POST',headers:{apikey:SUPABASE_KEY,'Content-Type':'application/json'},body:JSON.stringify({p_device_token:process.env.LIYA_DEVICE_TOKEN,p_service:'facebook'})});return r.ok?r.json():{authorized:false};}
export async function store(token,metadata){const r=await fetch(`${SUPABASE_URL}/rest/v1/rpc/master_hub_store_oauth`,{method:'POST',headers:{apikey:SUPABASE_KEY,'Content-Type':'application/json'},body:JSON.stringify({p_device_token:process.env.LIYA_DEVICE_TOKEN,p_service:'facebook',p_token:token,p_metadata:metadata})});return r.ok&&(await r.json())===true;}
