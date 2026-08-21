const API_BASE_URL = process.env.EXPO_PUBLIC_MASTER_HUB_API_URL || '';

async function request(path, options = {}) {
  if (!API_BASE_URL) {
    return { ok: false, configured: false, message: 'Backend API ещё не подключён' };
  }
  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(data.message || `HTTP ${response.status}`);
  return { ok: true, configured: true, data };
}

export const masterHubApi = {
  health: () => request('/health'),
  status: () => request('/v1/status'),
  analytics: (period = 1) => request(`/v1/analytics?days=${period}`),
  publications: () => request('/v1/publications'),
  triggerTelegramPost: () => request('/v1/actions/telegram/post', { method: 'POST' }),
};
