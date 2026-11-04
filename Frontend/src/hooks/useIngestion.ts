import { useCallback, useEffect, useState } from 'react';
import { api, routes, type IngestionJob } from '../api';

const isActive = (job: IngestionJob) => ['EN_COLA', 'EN_PROCESO'].includes(job.estado);

/** Conserva el seguimiento al cambiar de pestaña y consulta solo trabajos activos. */
export function useIngestion() {
  const [jobs, setJobs] = useState<IngestionJob[]>([]);
  const [loading, setLoading] = useState(true);
  const [starting, setStarting] = useState(false);
  const [error, setError] = useState('');
  const [revision, setRevision] = useState(0);
  const active = jobs.find(isActive);
  const activeId = active?.id;

  const refresh = useCallback(async (signal?: AbortSignal) => {
    setLoading(true);

    try {
      const result = await api<IngestionJob[]>(routes.ingestions, { signal });

      if (!signal?.aborted) {
        setJobs(result);
        setError('');
      }
    } catch (cause) {
      if (!signal?.aborted) setError((cause as Error).message);
    } finally {
      if (!signal?.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    const controller = new AbortController();
    void refresh(controller.signal);
    return () => controller.abort();
  }, [refresh]);

  useEffect(() => {
    if (activeId === undefined) return;

    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout>;

    async function poll() {
      try {
        const updated = await api<IngestionJob>(routes.ingestion(activeId!), {
          signal: controller.signal,
        });

        if (controller.signal.aborted) return;

        setJobs((current) => current.map((job) => (job.id === updated.id ? updated : job)));
        setError('');

        if (!isActive(updated)) {
          setRevision((current) => current + 1);
          return;
        }
      } catch (cause) {
        if (controller.signal.aborted) return;
        setError((cause as Error).message);
      }

      timer = setTimeout(() => void poll(), 2000);
    }

    void poll();

    return () => {
      controller.abort();
      clearTimeout(timer);
    };
  }, [activeId]);

  async function start() {
    setStarting(true);
    setError('');

    try {
      const job = await api<IngestionJob>(routes.ingestions, { method: 'POST' });
      setJobs((current) => [job, ...current.filter((previous) => previous.id !== job.id)]);
    } catch (cause) {
      setError((cause as Error).message);
    } finally {
      setStarting(false);
    }
  }

  return { jobs, active, loading, starting, error, revision, start, refresh };
}
