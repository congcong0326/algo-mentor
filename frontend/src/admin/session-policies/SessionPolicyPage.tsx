import { ChevronLeft, ChevronRight, CircleAlert, Pencil, Plus, RefreshCw, Search, Trash2 } from 'lucide-react';
import { FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import HeaderActionTooltip from '../../app/HeaderActionTooltip';
import { useI18n } from '../../i18n/I18nProvider';
import { ApiRequestError, deleteAdminPolicy, getAdminPolicies, requireApiData } from '../../services/api';
import type { AdminGenericPolicy, AdminGenericPolicyPage, GenericPolicyStatus, PolicySubjectRange } from '../../types/api';
import SessionPolicyDialog from './SessionPolicyDialog';
import { AUTH_USER_SESSION_POLICY_TYPE, durationParts, userSessionPolicyContent } from './sessionPolicy';

const pageSize = 20;
type StatusFilter = '' | GenericPolicyStatus;

export default function SessionPolicyPage() {
  const { locale, resources } = useI18n();
  const t = resources.sessionPolicy;
  const [data, setData] = useState<AdminGenericPolicyPage>({ items: [], total: 0, page: 1, pageSize });
  const [page, setPage] = useState(1);
  const [keywordInput, setKeywordInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [status, setStatus] = useState<StatusFilter>('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [editingPolicy, setEditingPolicy] = useState<AdminGenericPolicy | null | undefined>(undefined);
  const [deletingPolicy, setDeletingPolicy] = useState<AdminGenericPolicy>();
  const [deleting, setDeleting] = useState(false);
  const abortRef = useRef<AbortController | undefined>(undefined);

  const totalPages = useMemo(
    () => Math.max(1, Math.ceil(data.total / data.pageSize)),
    [data.pageSize, data.total],
  );

  useEffect(() => {
    void load(page);
    return () => abortRef.current?.abort();
  }, [keyword, page, status]);

  async function load(pageToLoad: number) {
    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;
    const current = () => abortRef.current === controller && !controller.signal.aborted;
    setLoading(true);
    setError('');
    try {
      const result = requireApiData(await getAdminPolicies({
        typeCode: AUTH_USER_SESSION_POLICY_TYPE,
        page: pageToLoad,
        pageSize,
        keyword,
        status,
      }, controller.signal), t.loadFailed);
      if (current()) {
        setData(result);
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

  function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setNotice('');
    setPage(1);
    setKeyword(keywordInput.trim());
  }

  async function confirmDelete() {
    if (!deletingPolicy || deleting) {
      return;
    }
    setDeleting(true);
    setError('');
    setNotice('');
    try {
      requireApiData(await deleteAdminPolicy(deletingPolicy.id, deletingPolicy.version), t.deleteFailed);
      setDeletingPolicy(undefined);
      setNotice(t.deleteSucceeded);
      const nextPage = data.items.length === 1 && page > 1 ? page - 1 : page;
      if (nextPage !== page) {
        setPage(nextPage);
      } else {
        await load(nextPage);
      }
    } catch (caught) {
      setError(errorMessage(caught, t.deleteFailed));
    } finally {
      setDeleting(false);
    }
  }

  function saved() {
    setEditingPolicy(undefined);
    setNotice(t.saveSucceeded);
    void load(page);
  }

  return (
    <section aria-label={t.ariaLabel} className="admin-data-page session-policy-page">
      <header className="admin-page-toolbar">
        <div>
          <h1>{t.title}</h1>
          <p className="session-policy-page-description">{t.pageDescription}</p>
        </div>
        <div className="admin-page-commands">
          <button className="primary-button compact" onClick={() => setEditingPolicy(null)} type="button">
            <Plus aria-hidden="true" />
            <span>{t.create}</span>
          </button>
          <HeaderActionTooltip id="session-policy-refresh-tooltip" label={t.refresh}>
            <button aria-describedby="session-policy-refresh-tooltip" aria-label={t.refresh} className="icon-button" disabled={loading} onClick={() => void load(page)} type="button">
              <RefreshCw aria-hidden="true" />
            </button>
          </HeaderActionTooltip>
        </div>
        <form className="admin-page-filters session-policy-filters" onSubmit={search}>
          <label className="session-policy-search">
            <Search aria-hidden="true" />
            <span className="visually-hidden">{t.searchPlaceholder}</span>
            <input aria-label={t.searchPlaceholder} onChange={(event) => setKeywordInput(event.target.value)} placeholder={t.searchPlaceholder} type="search" value={keywordInput} />
          </label>
          <button className="secondary-button compact" type="submit"><Search aria-hidden="true" /><span>{t.search}</span></button>
          <label className="session-policy-status-filter">
            <span>{t.status}</span>
            <select aria-label={t.status} onChange={(event) => { setNotice(''); setPage(1); setStatus(event.target.value as StatusFilter); }} value={status}>
              <option value="">{t.statusAll}</option>
              <option value="ENABLED">{t.statuses.ENABLED}</option>
              <option value="DISABLED">{t.statuses.DISABLED}</option>
            </select>
          </label>
        </form>
      </header>

      {error ? <p className="error-text session-policy-message" role="alert"><CircleAlert aria-hidden="true" />{error}</p> : null}
      {notice ? <p className="session-policy-notice" role="status">{notice}</p> : null}

      <div className="admin-table-wrap">
        <table className="admin-data-table session-policy-table">
          <thead>
            <tr>
              <th>{t.priority}</th>
              <th>{t.name}</th>
              <th>{t.scope}</th>
              <th>{t.maxSessions}</th>
              <th>{t.absoluteTimeout}</th>
              <th>{t.status}</th>
              <th>{t.updatedAt}</th>
              <th>{t.actions}</th>
            </tr>
          </thead>
          <tbody>
            {loading && data.items.length === 0 ? <tr><td className="session-policy-empty" colSpan={8}>{t.loading}</td></tr> : null}
            {!loading && data.items.length === 0 ? <tr><td className="session-policy-empty" colSpan={8}>{t.empty}</td></tr> : null}
            {data.items.map((policy) => <PolicyRow key={policy.id} policy={policy} />)}
          </tbody>
        </table>
      </div>

      <footer className="admin-pagination">
        <span>{resources.common.pageStatus(data.page || page, totalPages)}</span>
        <div>
          <button aria-label={resources.common.previousPage} className="icon-button compact" disabled={loading || page <= 1} onClick={() => setPage((current) => Math.max(1, current - 1))} type="button"><ChevronLeft aria-hidden="true" /></button>
          <button aria-label={resources.common.nextPage} className="icon-button compact" disabled={loading || page >= totalPages} onClick={() => setPage((current) => Math.min(totalPages, current + 1))} type="button"><ChevronRight aria-hidden="true" /></button>
        </div>
      </footer>

      {editingPolicy !== undefined ? (
        <SessionPolicyDialog
          policy={editingPolicy ?? undefined}
          onClose={() => setEditingPolicy(undefined)}
          onSaved={saved}
        />
      ) : null}
      {deletingPolicy ? (
        <div className="admin-dialog-backdrop" role="presentation">
          <section aria-labelledby="delete-session-policy-title" aria-modal="true" className="admin-dialog compact-dialog" role="dialog">
            <h2 id="delete-session-policy-title">{t.deleteTitle}</h2>
            <p>{t.deleteDescription(deletingPolicy.name)}</p>
            <footer>
              <button className="secondary-button" disabled={deleting} onClick={() => setDeletingPolicy(undefined)} type="button">{resources.common.cancel}</button>
              <button className="danger-button" disabled={deleting} onClick={() => void confirmDelete()} type="button"><Trash2 aria-hidden="true" /><span>{deleting ? t.deleting : t.delete}</span></button>
            </footer>
          </section>
        </div>
      ) : null}
    </section>
  );

  function PolicyRow({ policy }: { policy: AdminGenericPolicy }) {
    const content = userSessionPolicyContent(policy);
    const duration = durationParts(content.absoluteTimeoutSeconds);
    const editTooltipId = `session-policy-edit-${policy.id}`;
    const deleteTooltipId = `session-policy-delete-${policy.id}`;
    return (
      <tr>
        <td>{policy.priority}</td>
        <td>
          <strong>{policy.name}</strong>
          {policy.description ? <small className="session-policy-description">{policy.description}</small> : null}
        </td>
        <td>{formatScope(policy.subjectRange)}</td>
        <td>{new Intl.NumberFormat(locale).format(content.maxSessions)}</td>
        <td>{t.duration(new Intl.NumberFormat(locale).format(duration.value), duration.unit)}</td>
        <td><span className={`admin-status-badge ${policy.status.toLowerCase()}`}>{t.statuses[policy.status]}</span></td>
        <td>{formatDateTime(policy.updatedAt, locale)}</td>
        <td>
          <div className="admin-row-actions">
            <HeaderActionTooltip id={editTooltipId} label={t.editPolicy(policy.name)}>
              <button aria-describedby={editTooltipId} aria-label={t.editPolicy(policy.name)} className="icon-button compact" onClick={() => setEditingPolicy(policy)} type="button"><Pencil aria-hidden="true" /></button>
            </HeaderActionTooltip>
            <HeaderActionTooltip id={deleteTooltipId} label={t.deletePolicy(policy.name)}>
              <button aria-describedby={deleteTooltipId} aria-label={t.deletePolicy(policy.name)} className="icon-button compact danger-icon-button" onClick={() => setDeletingPolicy(policy)} type="button"><Trash2 aria-hidden="true" /></button>
            </HeaderActionTooltip>
          </div>
        </td>
      </tr>
    );
  }

  function formatScope(range: PolicySubjectRange): string {
    if (range.allSubject) {
      return t.allUsers;
    }
    const userCount = range.subjects.filter((subject) => subject.type === 'USER').length;
    const groupCount = range.subjects.length - userCount;
    return t.scopeSummary(userCount, groupCount);
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
