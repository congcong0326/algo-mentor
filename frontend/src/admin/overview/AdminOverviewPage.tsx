import { RefreshCw } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import { useI18n } from '../../i18n/I18nProvider';
import { ApiRequestError, getAdminOverview, requireApiData } from '../../services/api';
import type { AdminOverview } from '../../types/api';
import AdminOverviewSection from './AdminOverviewSection';

export default function AdminOverviewPage({ onNavigate }: { onNavigate: (path: string) => void }) {
  const { locale, resources } = useI18n();
  const [overview, setOverview] = useState<AdminOverview>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const abortRef = useRef<AbortController | undefined>(undefined);

  useEffect(() => {
    void load();
    const interval = window.setInterval(() => {
      if (!document.hidden) void load();
    }, 60_000);
    return () => {
      abortRef.current?.abort();
      window.clearInterval(interval);
    };
  }, [locale]);

  async function load() {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    setLoading(true);
    try {
      setOverview(requireApiData(await getAdminOverview(controller.signal), resources.adminOverview.loadFailed));
      setError('');
    } catch (caught) {
      if (!controller.signal.aborted) {
        setError(caught instanceof ApiRequestError || caught instanceof Error
          ? caught.message
          : resources.adminOverview.loadFailed);
      }
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }

  if (loading && !overview) {
    return <section className="admin-overview-page"><p>{resources.adminOverview.loading}</p></section>;
  }

  if (!overview) {
    return (
      <section className="admin-overview-page">
        <h1>{resources.adminOverview.title}</h1>
        <p className="error-text">{error}</p>
        <button className="secondary-button" onClick={() => void load()} type="button">{resources.adminOverview.retry}</button>
      </section>
    );
  }

  const enabledLabel = (enabled: boolean) => enabled ? resources.adminOverview.enabled : resources.adminOverview.disabled;

  return (
    <section className="admin-overview-page">
      <header className="admin-overview-header">
        <div>
          <h1>{resources.adminOverview.title}</h1>
          <p>{resources.adminOverview.generatedMeta(new Date(overview.generatedAt).toLocaleString(locale), overview.quotaZone)}</p>
          {error ? <p className="error-text">{error}</p> : null}
        </div>
        <button aria-label={resources.adminOverview.refresh} className="icon-button" onClick={() => void load()} title={resources.adminOverview.refresh} type="button">
          <RefreshCw aria-hidden="true" />
        </button>
      </header>
      <div className="admin-overview-status-row">
        <AdminOverviewSection section={overview.betaAccess} title={resources.adminOverview.betaAccess}>
          {(data) => (
            <button className="overview-link-button" onClick={() => onNavigate('/admin/beta-access')} type="button">
              {enabledLabel(data.emailAllowlistEnabled)} · {resources.adminOverview.betaSummary(
                data.allowedEmailCount,
                data.registeredAllowedEmailCount,
              )}
            </button>
          )}
        </AdminOverviewSection>
        <AdminOverviewSection section={overview.aiRuntime} title={resources.adminOverview.aiRuntime}>
          {(data) => (
            <button className="overview-link-button" onClick={() => onNavigate('/admin/ai')} type="button">
              {enabledLabel(data.aiEnabled)} · {resources.adminOverview.aiRuntimeSummary(data.defaultDailyRequestLimit)}
            </button>
          )}
        </AdminOverviewSection>
      </div>
      <div className="admin-overview-metrics">
        <AdminOverviewSection section={overview.aiToday} title={resources.adminOverview.aiToday}>
          {(data) => (
            <>
              <dl>
                <div><dt>{resources.adminOverview.entryRequests}</dt><dd>{data.entryRequests.total}</dd></div>
                <div><dt>{resources.adminOverview.success}</dt><dd>{data.entryRequests.completed}</dd></div>
                <div><dt>{resources.adminOverview.failed}</dt><dd>{data.entryRequests.failed}</dd></div>
                <div><dt>{resources.adminOverview.quotaRejected}</dt><dd>{data.entryRequests.quotaRejected}</dd></div>
                <div><dt>{resources.adminOverview.modelCalls}</dt><dd>{data.modelCallCount}</dd></div>
                <div><dt>Token</dt><dd>{data.totalTokens.toLocaleString(locale)}</dd></div>
                <div><dt>{resources.adminOverview.estimatedCost}</dt><dd>${data.estimatedCostUsd}</dd></div>
              </dl>
              {data.unpricedCallCount > 0 ? (
                <button className="warning-link" onClick={() => onNavigate('/admin/ai?tab=pricing')} type="button">
                  {resources.adminOverview.unpricedCalls(data.unpricedCallCount)}
                </button>
              ) : null}
            </>
          )}
        </AdminOverviewSection>
      </div>
      <div className="admin-overview-columns">
        <AdminOverviewSection section={overview.quotaRisks} title={resources.adminOverview.quotaRiskUsers}>
          {(data) => data.items.length ? (
            <ul className="overview-list">
              {data.items.map((item) => (
                <li key={item.userId}>
                  <button onClick={() => onNavigate(`/admin/users?userId=${item.userId}`)} type="button">
                    {item.displayName || item.email || resources.adminOverview.userFallback(item.userId)}
                  </button>
                  <span>{item.requestCount} / {item.effectiveDailyRequestLimit} · {item.usagePercent}%</span>
                </li>
              ))}
            </ul>
          ) : <p>{resources.adminOverview.noQuotaRisks}</p>}
        </AdminOverviewSection>
        <AdminOverviewSection section={overview.feedback} title={resources.adminOverview.feedbackTasks}>
          {(data) => (
            <div className="overview-feedback-links">
              <button onClick={() => onNavigate('/admin/feedback?status=OPEN')} type="button">OPEN {data.openThreadCount}</button>
              <button onClick={() => onNavigate('/admin/feedback?unreadOnly=true')} type="button">
                {resources.adminOverview.adminUnread(data.adminUnreadMessageCount)}
              </button>
            </div>
          )}
        </AdminOverviewSection>
      </div>
      <AdminOverviewSection section={overview.recentFailedRuns} title={resources.adminOverview.recentFailedRuns}>
        {(data) => data.items.length
          ? <p>{resources.adminOverview.failedRuns(data.items.length)}</p>
          : <p>{resources.adminOverview.noRunQuery}</p>}
      </AdminOverviewSection>
    </section>
  );
}
