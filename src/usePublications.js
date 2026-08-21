import { useCallback, useEffect, useState } from 'react';
import { masterHubApi } from './api';

export function usePublications() {
  const [data, setData] = useState({ queue: [], history: [] });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const refresh = useCallback(async () => {
    setLoading(true); setError('');
    try {
      const result = await masterHubApi.publications();
      setData({ queue: result.data.queue || [], history: result.data.history || [] });
    } catch (e) { setError(e?.message || 'Не удалось загрузить публикации'); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { refresh(); }, [refresh]);
  return { ...data, loading, error, refresh };
}
