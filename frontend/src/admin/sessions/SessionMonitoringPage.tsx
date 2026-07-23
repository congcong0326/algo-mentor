import { ChevronLeft, ChevronRight, CircleAlert, LogOut, RefreshCw, Search } from 'lucide-react';
import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import HeaderActionTooltip from '../../app/HeaderActionTooltip';
import { useI18n } from '../../i18n/I18nProvider';
import {
  ApiRequestError,
  getAdminAuthSessions,
  requireApiData,
  revokeAdminAuthSession,
} from '../../services/api';
import type { AdminAuthSession, AdminAuthSessionPage, AuthSessionActivity } from '../../types/api';
import SessionRevocationDialog from './SessionRevocationDialog';

const pageSize = 20;
type ActivityFilter = 'ALL' | AuthSessionActivity;

export default function SessionMonitoringPage() {
  const { locale, resources } = useI18n();
  const t = resources.sessionMonitoring;
  const [data, setData] = useState<AdminAuthSessionPage>();
  const [page, setPage] = useState(1);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [activity, setActivity] = useState<ActivityFilter>('ALL');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [pendingRevocation, setPendingRevocation] = useState<AdminAuthSession>();
  const [revoking, setRevoking] = useState(false);
  const abortRef = useRef<AbortController | undefined>(undefined);

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil((data?.total ?? 0) / (data?.pageSize ?? pageSize))),
    [data],
  );

  useEffect(() => {
    void load(page);
    const intervalId = window.setInterval(() => {
      if (!document.hidden) {
        void load(page);
      }
    }, 60_000);
    return () => {
      abortRef.current?.abort();
      window.clearInterval(intervalId);
    };
  }, [activity, keyword, page]);

  async function load(pageToLoad: number) {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    const current = () => abortRef.current === controller && !controller.signal.aborted;
    setLoading(true);
    setError('');

    try {
      const response = requireApiData(await getAdminAuthSessions({
        page: pageToLoad,
        pageSize,
        keyword,
        activity: activity === 'ALL' ? '' : activity,
      }, controller.signal), t.loadFailed);
      if (current()) {
        setData(response);
      }
    } catch (caught) {
      if (current()) {
        setError(errorMessage(caught, t.loadFailed));
      }
    } finally {
      if (current()) {
        setLoading(false);
      }
    }
  }

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setNotice('');
    setPage(1);
    setKeyword(keywordInput.trim());
  }

  function handleActivityChange(nextActivity: ActivityFilter) {
    setNotice('');
    setPage(1);
    setActivity(nextActivity);
  }

  async function confirmRevocation() {
    if (!pendingRevocation || revoking) {
      return;
    }
    setRevoking(true);
    setError('');
    setNotice('');
    try {
      const result = requireApiData(
        await revokeAdminAuthSession(pendingRevocation.sessionRef),
        t.operationFailed,
      );
      setPendingRevocation(undefined);
      if (result.alreadyOffline) {
        setNotice(t.alreadyOffline);
      }
      const nextPage = data?.items.length === 1 && page > 1 ? page - 1 : page;
      if (nextPage !== page) {
        setPage(nextPage);
      } else {
        await load(nextPage);
      }
    } catch (caught) {
      setError(errorMessage(caught, t.operationFailed));
    } finally {
      setRevoking(false);
    }
  }

  return (
    <section aria-label={t.ariaLabel} className="session-monitoring-page">
      <header className="session-monitoring-header">
        <div>
          <h1>{t.title}</h1>
          <p>{t.lastRefreshed}: {data?.checkedAt ? formatDateTime(data.checkedAt, locale) : t.notRefreshed}</p>
        </div>
        <HeaderActionTooltip id="session-monitoring-refresh-tooltip" label={t.refresh}>
          <button
            aria-describedby="session-monitoring-refresh-tooltip"
            aria-label={t.refresh}
            className="icon-button"
            disabled={loading}
            onClick={() => void load(page)}
            type="button"
          >
            <RefreshCw aria-hidden="true" />
          </button>
        </HeaderActionTooltip>
      </header>

      <dl className="session-monitoring-summary" aria-live="polite">
        <div><dt>{t.validSessions}</dt><dd>{data?.summary.validSessionCount ?? '-'}</dd></div>
        <div><dt>{t.activeSessions}</dt><dd>{data?.summary.activeSessionCount ?? '-'}</dd></div>
        <div><dt>{t.validUsers}</dt><dd>{data?.summary.validUserCount ?? '-'}</dd></div>
      </dl>

      <form className="session-monitoring-filters" onSubmit={handleSearch}>
        <label className="session-monitoring-search">
          <span className="visually-hidden">{t.searchPlaceholder}</span>
          <Search aria-hidden="true" />
          <input
            aria-label={t.searchPlaceholder}
            onChange={(event) => setKeywordInput(event.target.value)}
            placeholder={t.searchPlaceholder}
            type="search"
            value={keywordInput}
          />
        </label>
        <button className="secondary-button" type="submit"><Search aria-hidden="true" /><span>{t.search}</span></button>
        <label className="session-monitoring-activity-filter">
          <span>{t.activityLabel}</span>
          <select aria-label={t.activityLabel} onChange={(event) => handleActivityChange(event.target.value as ActivityFilter)} value={activity}>
            <option value="ALL">{t.activityFilters.ALL}</option>
            <option value="ACTIVE">{t.activityFilters.ACTIVE}</option>
            <option value="IDLE">{t.activityFilters.IDLE}</option>
          </select>
        </label>
      </form>

      {error ? <p className="error-text" role="alert"><CircleAlert aria-hidden="true" />{error}</p> : null}
      {notice ? <p className="session-monitoring-notice" role="status">{notice}</p> : null}

      <div className="session-monitoring-table-wrap">
        <table className="session-monitoring-table">
          <thead>
            <tr>
              <th>{t.user}</th>
              <th>{t.userStatus}</th>
              <th>{t.sessionStatus}</th>
              <th className="session-monitoring-secondary-column">{t.createdAt}</th>
              <th>{t.lastAccessedAt}</th>
              <th className="session-monitoring-secondary-column">{t.expiresAt}</th>
              <th>{t.actions}</th>
            </tr>
          </thead>
          <tbody>
            {data?.items.map((session) => (
              <SessionRow key={session.sessionRef} locale={locale} session={session} />
            ))}
            {!loading && (data?.items.length ?? 0) === 0 ? (
              <tr><td className="session-monitoring-empty" colSpan={7}>{t.empty}</td></tr>
            ) : null}
            {loading && !data ? (
              <tr><td className="session-monitoring-empty" colSpan={7}>{t.loading}</td></tr>
            ) : null}
          </tbody>
        </table>
      </div>

      <footer className="session-monitoring-pagination">
        <span>{resources.common.pageStatus(data?.page ?? page, totalPages)}</span>
        <div>
          <button aria-label={resources.common.previousPage} className="icon-button" disabled={loading || page <= 1} onClick={() => setPage((current) => Math.max(1, current - 1))} type="button"><ChevronLeft aria-hidden="true" /></button>
          <button aria-label={resources.common.nextPage} className="icon-button" disabled={loading || page >= totalPages} onClick={() => setPage((current) => Math.min(totalPages, current + 1))} type="button"><ChevronRight aria-hidden="true" /></button>
        </div>
      </footer>

      {pendingRevocation ? (
        <SessionRevocationDialog
          onCancel={() => !revoking && setPendingRevocation(undefined)}
          onConfirm={() => void confirmRevocation()}
          pending={revoking}
          session={pendingRevocation}
        />
      ) : null}
    </section>
  );

  function SessionRow({ locale, session }: { locale: string; session: AdminAuthSession }) {
    const user = session.displayName || session.email || `#${session.userId}`;
    const currentTooltipId = `session-current-${session.sessionRef}`;
    return (
      <tr>
        <td>
          <strong>{user}</strong>
          <span className="session-monitoring-user-detail">{session.email || `#${session.userId}`}</span>
        </td>
        <td><span className={`session-monitoring-user-status ${session.userStatus.toLowerCase()}`}>{t.userStatuses[session.userStatus]}</span></td>
        <td>
          <span className={`session-monitoring-activity ${session.activity.toLowerCase()}`}>{t.activities[session.activity]}</span>
          {session.current ? <span className="session-monitoring-current">{t.currentSession}</span> : null}
        </td>
        <td className="session-monitoring-secondary-column">{formatDateTime(session.createdAt, locale)}</td>
        <td>{formatDateTime(session.lastAccessedAt, locale)}</td>
        <td className="session-monitoring-secondary-column">{formatDateTime(session.expiresAt, locale)}</td>
        <td>
          {session.current ? (
            <HeaderActionTooltip id={currentTooltipId} label={t.currentSessionHint}>
              <button aria-describedby={currentTooltipId} aria-label={t.currentSessionHint} className="secondary-button session-monitoring-revoke-button" disabled type="button">
                <LogOut aria-hidden="true" /><span>{t.revoke}</span>
              </button>
            </HeaderActionTooltip>
          ) : (
            <button aria-label={t.revokeSession(user)} className="secondary-button session-monitoring-revoke-button" onClick={() => setPendingRevocation(session)} type="button">
              <LogOut aria-hidden="true" /><span>{t.revoke}</span>
            </button>
          )}
        </td>
      </tr>
    );
  }
}

function formatDateTime(value: string, locale: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(locale, {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function errorMessage(error: unknown, fallback: string): string {
  if (error instanceof ApiRequestError) {
    return error.message || fallback;
  }
  return error instanceof Error ? error.message : fallback;
}
