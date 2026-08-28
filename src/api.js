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
};
