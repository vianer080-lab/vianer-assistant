import * as SecureStore from 'expo-secure-store';

const TOKEN_KEY='master_hub_device_token';
const SUPABASE_URL='https://ceozpugxrwgblkwtxiew.supabase.co';
const SUPABASE_KEY='sb_publishable_1AhIOiv0elKK_2wWnaLs6w_z8lWl0M2';

export const getDeviceToken=()=>SecureStore.getItemAsync(TOKEN_KEY);
export const clearDeviceToken=()=>SecureStore.deleteItemAsync(TOKEN_KEY);

export async function pairDevice(code){
  const response=await fetch(`${SUPABASE_URL}/functions/v1/liya-device`,{
    method:'POST',headers:{apikey:SUPABASE_KEY,'Content-Type':'application/json'},
    body:JSON.stringify({action:'register',code:String(code||'').trim(),device_name:'Master Hub Android'}),
  });
  const data=await response.json().catch(()=>({}));
  if(!response.ok||!data.device_token)throw new Error(data.error||'Не удалось подключить устройство');
  await SecureStore.setItemAsync(TOKEN_KEY,data.device_token,{keychainAccessible:SecureStore.AFTER_FIRST_UNLOCK});
  return data.device_token;
}
