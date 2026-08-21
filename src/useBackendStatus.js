import { useCallback, useState } from 'react';
import { masterHubApi } from './api';

export function useBackendStatus() {
  const [services, setServices] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');

  const refresh = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const result = await masterHubApi.status();
      setServices(result.data.services || {});
      return result.data.services || {};
    } catch (e) {
      setError(e?.message || 'Не удалось получить статус backend');
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  return { services, loading, error, refresh };
}
