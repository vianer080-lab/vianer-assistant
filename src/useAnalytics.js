import { useCallback, useEffect, useState } from 'react';
import { masterHubApi } from './api';

export function useAnalytics(days = 1) {
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await masterHubApi.analytics(days);
      setData(result.data);
    } catch (e) {
      setError(e?.message || 'Не удалось загрузить аналитику');
    } finally {
      setLoading(false);
    }
  }, [days]);

  useEffect(() => { refresh(); }, [refresh]);
  return { data, loading, error, refresh };
}
