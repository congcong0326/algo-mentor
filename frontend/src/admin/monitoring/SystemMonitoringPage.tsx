import { CircleAlert, CircleCheck, LoaderCircle, RefreshCw } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import HeaderActionTooltip from '../../app/HeaderActionTooltip';
import { useI18n } from '../../i18n/I18nProvider';
import { ApiRequestError, getHealth, requireApiData } from '../../services/api';
import type { HealthStatus } from '../../types/api';

type MonitoringState = 'checking' | 'healthy' | 'unavailable';

export default function SystemMonitoringPage() {
  const { locale, resources } = useI18n();
  const t = resources.adminMonitoring;
  const [health, setHealth] = useState<HealthStatus>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [lastCheckedAt, setLastCheckedAt] = useState<Date>();
  const abortRef = useRef<AbortController | undefined>(undefined);

  useEffect(() => {
    void loadHealth();
    const intervalId = window.setInterval(() => {
      if (!document.hidden) {
        void loadHealth();
      }
    }, 60_000);

    return () => {
      abortRef.current?.abort();
      window.clearInterval(intervalId);
    };
  }, []);

  async function loadHealth() {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    const current = () => abortRef.current === controller && !controller.signal.aborted;
    setLoading(true);
    setError('');

    try {
      const data = requireApiData(await getHealth(controller.signal), t.loadFailed);
      if (current()) {
        setHealth(data);
        setLastCheckedAt(new Date());
      }
    } catch (caught) {
      if (current()) {
        setHealth(undefined);
        setError(errorMessage(caught, t.loadFailed));
        setLastCheckedAt(new Date());
      }
    } finally {
      if (current()) {
        setLoading(false);
      }
    }
  }

  const state = monitoringState(health, error, loading);
  const StatusIcon = state === 'healthy' ? CircleCheck : state === 'unavailable' ? CircleAlert : LoaderCircle;
  const statusLabel = state === 'healthy' ? t.healthy : state === 'unavailable' ? t.unavailable : t.checking;

  return (
    <section className="system-monitoring-page" aria-label={t.ariaLabel}>
      <header className="system-monitoring-header">
        <div>
          <h1>{t.title}</h1>
          <p>{t.lastChecked}: {lastCheckedAt ? formatCheckedAt(lastCheckedAt, locale) : t.notChecked}</p>
        </div>
        <HeaderActionTooltip id="system-monitoring-refresh-tooltip" label={t.refresh}>
          <button
            aria-describedby="system-monitoring-refresh-tooltip"
            aria-label={t.refresh}
            className="icon-button"
            disabled={loading}
            onClick={() => void loadHealth()}
            type="button"
          >
            <RefreshCw aria-hidden="true" />
          </button>
        </HeaderActionTooltip>
      </header>

      <section className="system-monitoring-health" aria-live="polite">
        <header className="system-monitoring-health-heading">
          <h2>{t.serviceHealth}</h2>
          <span className={`system-monitoring-status ${state}`} role="status">
            <StatusIcon aria-hidden="true" />
            {statusLabel}
          </span>
        </header>
        <dl className="system-monitoring-health-grid">
          <div>
            <dt>{t.apiService}</dt>
            <dd>{health?.status ?? '-'}</dd>
          </div>
          <div>
            <dt>{t.status}</dt>
            <dd>{statusLabel}</dd>
          </div>
        </dl>
      </section>

      {error ? <p className="error-text" role="alert">{error}</p> : null}
    </section>
  );
}

function monitoringState(health: HealthStatus | undefined, error: string, loading: boolean): MonitoringState {
  if (health?.status === 'UP') {
    return 'healthy';
  }
  if (health || error) {
    return 'unavailable';
  }
  return loading ? 'checking' : 'unavailable';
}

function formatCheckedAt(value: Date, locale: string): string {
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(value);
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError) {
    return error.message || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
