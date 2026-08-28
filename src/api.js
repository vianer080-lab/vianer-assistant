const API_BASE_URL = process.env.EXPO_PUBLIC_MASTER_HUB_API_URL || 'https://vianer-assistant.expo.app';

async function request(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.message || data.error || `HTTP ${response.status}`);
  return { ok: true, configured: true, data };
}

export const masterHubApi = {
  health: () => request('/api/status'),
  status: () => request('/api/status'),
  analytics: (period = 1) => request(`/v1/analytics?days=${period}`),
  publications: () => request('/v1/publication-queue'),
  createPublication: async ({ token, video, title, description, privacyStatus, scheduledAt }) => {
    const form=new FormData();
    form.append('video',{uri:video.uri,name:video.name||'video.mp4',type:'video/mp4'});
    form.append('title',title);form.append('description',description);form.append('privacyStatus',privacyStatus);form.append('scheduledAt',scheduledAt);
    const response=await fetch(`${API_BASE_URL}/api/publications/create`,{method:'POST',headers:{Authorization:`Bearer ${token}`},body:form});
    const data=await response.json().catch(()=>({}));if(!response.ok||data.ok===false)throw new Error(data.error||`HTTP ${response.status}`);return{ok:true,configured:true,data};
  },
};
