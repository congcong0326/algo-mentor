import { RefreshCw, Search, Trash2, UserCheck, UserX } from 'lucide-react';
import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import {
  addBetaAllowedEmails,
  ApiRequestError,
  getBetaAccess,
  removeBetaAllowedEmail,
  requireApiData,
  updateBetaAccessSettings,
} from '../services/api';
import type {
  BetaAccessPage,
  BetaAllowedEmail,
  BetaAllowedEmailBatchResponse,
} from '../types/api';
import { useI18n } from '../i18n/I18nProvider';

const defaultPageSize = 20;

interface BetaAccessPageProps {
  onNavigateHome: () => void;
}

export default function BetaAccessPage({ onNavigateHome }: BetaAccessPageProps) {
  const { resources } = useI18n();
  const t = resources.betaAccess;
  const [data, setData] = useState<BetaAccessPage>();
  const [page, setPage] = useState(1);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [emailsInput, setEmailsInput] = useState('');
  const [batchResult, setBatchResult] = useState<BetaAllowedEmailBatchResponse>();
  const [pendingSetting, setPendingSetting] = useState<boolean>();
  const [pendingRemoval, setPendingRemoval] = useState<BetaAllowedEmail>();
  const [loading, setLoading] = useState(false);
  const [operationPending, setOperationPending] = useState(false);
  const [error, setError] = useState('');
  const [forbidden, setForbidden] = useState(false);
  const requestIdRef = useRef(0);

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil((data?.total ?? 0) / (data?.pageSize ?? defaultPageSize))),
    [data],
  );

  useEffect(() => {
    const controller = new AbortController();
    void load(page, controller.signal);
    return () => controller.abort();
  }, [keyword, page]);

  async function load(pageToLoad: number, signal?: AbortSignal) {
    const requestId = requestIdRef.current + 1;
    requestIdRef.current = requestId;
    const isCurrent = () => requestId === requestIdRef.current && !signal?.aborted;
    setLoading(true);
    setError('');
    setForbidden(false);
    try {
      const response = requireApiData(await getBetaAccess({
        page: pageToLoad,
        pageSize: defaultPageSize,
        keyword,
      }, signal), t.loadFailed);
      if (isCurrent()) {
        setData(response);
      }
    } catch (caught) {
      if (!isCurrent()) {
        return;
      }
      if (caught instanceof ApiRequestError && (caught.status === 401 || caught.status === 403)) {
        setForbidden(true);
        setError(t.forbidden);
      } else {
        setError(caught instanceof Error ? caught.message : t.loadFailed);
      }
    } finally {
      if (isCurrent()) {
        setLoading(false);
      }
    }
  }

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPage(1);
    setKeyword(keywordInput.trim());
  }

  async function confirmSettingUpdate() {
    if (pendingSetting === undefined || operationPending) {
      return;
    }
    setOperationPending(true);
    setError('');
    try {
      const settings = requireApiData(await updateBetaAccessSettings({
        emailAllowlistEnabled: pendingSetting,
      }), t.operationFailed);
      setData((current) => current ? { ...current, settings } : current);
      setPendingSetting(undefined);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.operationFailed);
    } finally {
      setOperationPending(false);
    }
  }

  async function addEmails(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (operationPending) {
      return;
    }
    const emails = emailsInput
      .split(/[\n,;]+/)
      .map((email) => email.trim())
      .filter(Boolean);
    if (emails.length === 0) {
      setError(t.emailInputRequired);
      return;
    }
    setOperationPending(true);
    setError('');
    try {
      const result = requireApiData(await addBetaAllowedEmails(emails), t.operationFailed);
      setBatchResult(result);
      if (result.addedCount > 0) {
        setEmailsInput('');
        setPage(1);
        await load(1);
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.operationFailed);
    } finally {
      setOperationPending(false);
    }
  }

  async function confirmRemoval() {
    if (!pendingRemoval || operationPending) {
      return;
    }
    setOperationPending(true);
    setError('');
    try {
      requireApiData(
        await removeBetaAllowedEmail(pendingRemoval.id),
        t.operationFailed,
      );
      setPendingRemoval(undefined);
      const nextPage = data?.items.length === 1 && page > 1 ? page - 1 : page;
      if (nextPage !== page) {
        setPage(nextPage);
      } else {
        await load(nextPage);
      }
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : t.operationFailed);
    } finally {
      setOperationPending(false);
    }
  }

  if (forbidden) {
    return (
      <section className="beta-access-page" aria-label={t.ariaLabel}>
        <div className="admin-users-forbidden" role="alert">
          <h1>{t.title}</h1>
          <p>{t.forbidden}</p>
          <button className="primary-button" onClick={onNavigateHome} type="button">
            {t.backHome}
          </button>
        </div>
      </section>
    );
  }

  return (
    <section className="beta-access-page" aria-label={t.ariaLabel}>
      <div className="beta-access-toolbar">
        <div>
          <h1>{t.title}</h1>
          {error ? <p className="error-text" role="alert">{error}</p> : null}
        </div>
        <form className="beta-access-search" onSubmit={handleSearch}>
          <input
            aria-label={t.searchPlaceholder}
            onChange={(event) => setKeywordInput(event.target.value)}
            placeholder={t.searchPlaceholder}
            type="search"
            value={keywordInput}
          />
          <button className="secondary-button" type="submit">
            <Search aria-hidden="true" />
            <span>{t.search}</span>
          </button>
          <button
            aria-label={t.refresh}
            className="icon-button"
            disabled={loading}
            onClick={() => void load(page)}
            title={t.refresh}
            type="button"
          >
            <RefreshCw aria-hidden="true" />
          </button>
        </form>
      </div>

      <div className="beta-access-settings-band">
        <div>
          <strong>{t.allowlistSetting}</strong>
          <span>{data?.settings.emailAllowlistEnabled ? t.enabled : t.disabled}</span>
        </div>
        <label className="beta-access-switch">
          <input
            checked={data?.settings.emailAllowlistEnabled ?? false}
            disabled={!data || loading || operationPending}
            onChange={(event) => setPendingSetting(event.target.checked)}
            role="switch"
            type="checkbox"
          />
          <span aria-hidden="true" />
          <span className="visually-hidden">{t.allowlistSetting}</span>
        </label>
      </div>

      <form className="beta-access-add-tool" onSubmit={(event) => void addEmails(event)}>
        <label htmlFor="beta-access-emails">{t.batchAdd}</label>
        <textarea
          id="beta-access-emails"
          onChange={(event) => setEmailsInput(event.target.value)}
          placeholder={t.batchPlaceholder}
          rows={4}
          value={emailsInput}
        />
        <div className="beta-access-add-actions">
          <span>{t.batchLimit}</span>
          <button className="primary-button" disabled={operationPending} type="submit">
            {operationPending ? t.saving : t.add}
          </button>
        </div>
      </form>

      {batchResult ? (
        <div className="beta-access-batch-result" role="status">
          <div>
            <strong>{t.batchResult}</strong>
            <span>{t.batchSummary(
              batchResult.addedCount,
              batchResult.existingCount,
              batchResult.invalidCount,
            )}</span>
          </div>
          <ul>
            {batchResult.results.map((result, index) => (
              <li key={`${result.email}-${index}`}>
                <span>{result.email || resources.common.empty}</span>
                <strong className={`beta-add-status ${result.status.toLowerCase()}`}>
                  {t.addStatus[result.status]}
                </strong>
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <div className="beta-access-table-wrap">
        <table className="beta-access-table">
          <thead>
            <tr>
              <th>{t.email}</th>
              <th>{t.registration}</th>
              <th>{t.userStatus}</th>
              <th>{t.createdBy}</th>
              <th>{t.createdAt}</th>
              <th>{t.actions}</th>
            </tr>
          </thead>
          <tbody>
            {loading ? Array.from({ length: 4 }, (_, index) => (
              <tr key={`loading-${index}`}>
                <td colSpan={6}>{t.loading}</td>
              </tr>
            )) : null}
            {!loading && data?.items.map((item) => (
              <tr key={item.id}>
                <td>{item.email}</td>
                <td>
                  <span className={`beta-registration-status ${item.registered ? 'registered' : 'unregistered'}`}>
                    {item.registered ? <UserCheck aria-hidden="true" /> : <UserX aria-hidden="true" />}
                    {item.registered ? t.registered : t.unregistered}
                  </span>
                </td>
                <td>{item.associatedUserStatus ? t.userStatuses[item.associatedUserStatus] : resources.common.empty}</td>
                <td>{item.createdByDisplayName || `#${item.createdBy}`}</td>
                <td>{formatDateTime(item.createdAt)}</td>
                <td>
                  <button
                    aria-label={t.removeEmail(item.email)}
                    className="icon-button danger-button"
                    onClick={() => setPendingRemoval(item)}
                    title={t.remove}
                    type="button"
                  >
                    <Trash2 aria-hidden="true" />
                  </button>
                </td>
              </tr>
            ))}
            {!loading && (data?.items.length ?? 0) === 0 ? (
              <tr>
                <td colSpan={6}>{t.empty}</td>
              </tr>
            ) : null}
          </tbody>
        </table>
      </div>

      <div className="admin-users-pagination">
        <button
          className="secondary-button"
          disabled={page <= 1 || loading}
          onClick={() => setPage((current) => Math.max(1, current - 1))}
          type="button"
        >
          {resources.common.previousPage}
        </button>
        <span>{resources.common.pageStatus(page, totalPages)}</span>
        <button
          className="secondary-button"
          disabled={page >= totalPages || loading}
          onClick={() => setPage((current) => current + 1)}
          type="button"
        >
          {resources.common.nextPage}
        </button>
      </div>

      {pendingSetting !== undefined ? (
        <div className="admin-confirm-dialog-backdrop">
          <div className="admin-confirm-dialog" aria-modal="true" role="dialog">
            <h2>{pendingSetting ? t.confirmEnableTitle : t.confirmDisableTitle}</h2>
            <p>{pendingSetting && (data?.total ?? 0) === 0
              ? t.confirmEnableEmptyDescription
              : pendingSetting ? t.confirmEnableDescription : t.confirmDisableDescription}</p>
            <div className="button-row">
              <button className="secondary-button" disabled={operationPending} onClick={() => setPendingSetting(undefined)} type="button">
                {resources.common.cancel}
              </button>
              <button className="primary-button" disabled={operationPending} onClick={() => void confirmSettingUpdate()} type="button">
                {t.confirm}
              </button>
            </div>
          </div>
        </div>
      ) : null}

      {pendingRemoval ? (
        <div className="admin-confirm-dialog-backdrop">
          <div className="admin-confirm-dialog" aria-modal="true" role="dialog">
            <h2>{t.confirmRemoveTitle}</h2>
            <p>{pendingRemoval.registered
              ? t.confirmRegisteredRemoval(pendingRemoval.email)
              : t.confirmRemoval(pendingRemoval.email)}</p>
            <div className="button-row">
              <button className="secondary-button" disabled={operationPending} onClick={() => setPendingRemoval(undefined)} type="button">
                {resources.common.cancel}
              </button>
              <button className="primary-button danger-button" disabled={operationPending} onClick={() => void confirmRemoval()} type="button">
                {t.remove}
              </button>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}
