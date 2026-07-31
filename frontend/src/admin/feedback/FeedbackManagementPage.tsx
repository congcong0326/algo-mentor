import { RefreshCw } from 'lucide-react';
import { useEffect, useState } from 'react';
import FeedbackComposer from '../../feedback/FeedbackComposer';
import FeedbackThreadTimeline from '../../feedback/FeedbackThreadTimeline';
import { useI18n } from '../../i18n/I18nProvider';
import {
  ApiRequestError,
  getAdminFeedbackThread,
  getAdminFeedbackThreads,
  markAdminFeedbackRead,
  replyAdminFeedback,
  requireApiData,
  updateAdminFeedbackStatus,
} from '../../services/api';
import type {
  AdminFeedbackListQuery,
  FeedbackCategory,
  FeedbackStatus,
  FeedbackThreadDetail,
  FeedbackThreadPage,
} from '../../types/api';

interface FeedbackManagementPageProps {
  onNavigate: (path: string) => void;
  onUnreadCountChanged?: (count: number) => void;
  search: string;
}

export default function FeedbackManagementPage({
  search,
  onNavigate,
  onUnreadCountChanged,
}: FeedbackManagementPageProps) {
  const { locale, resources } = useI18n();
  const params = new URLSearchParams(search);
  const selectedId = positive(params.get('threadId'));
  const query: AdminFeedbackListQuery = {
    status: (params.get('status') as FeedbackStatus) || '',
    category: (params.get('category') as AdminFeedbackListQuery['category']) || '',
    userId: positive(params.get('userId')),
    unreadOnly: params.get('unreadOnly') === 'true',
  };
  const [page, setPage] = useState<FeedbackThreadPage>();
  const [detail, setDetail] = useState<FeedbackThreadDetail>();
  const [error, setError] = useState('');
  const [pending, setPending] = useState(false);

  useEffect(() => {
    void load();
  }, [locale, search]);

  useEffect(() => {
    if (selectedId) {
      void loadDetail(selectedId);
    } else {
      setDetail(undefined);
    }
  }, [locale, selectedId]);

  async function load() {
    try {
      const next = requireApiData(await getAdminFeedbackThreads(query), resources.adminFeedback.listLoadFailed);
      setPage(next);
      setError('');
      onUnreadCountChanged?.(next.unreadMessageCount);
    } catch (caught) {
      setError(errorText(caught, resources.adminFeedback.requestFailed));
    }
  }

  async function loadDetail(id: number) {
    try {
      const next = requireApiData(await getAdminFeedbackThread(id), resources.adminFeedback.detailLoadFailed);
      setDetail(next);
      if (next.unreadMessageCount) {
        const read = requireApiData(await markAdminFeedbackRead(id), resources.adminFeedback.markReadFailed);
        setDetail({ ...next, unreadMessageCount: 0 });
        onUnreadCountChanged?.(read.unreadMessageCount);
        await load();
      }
    } catch (caught) {
      setError(errorText(caught, resources.adminFeedback.requestFailed));
    }
  }

  function changeFilters(next: Record<string, string>) {
    const value = new URLSearchParams(search);
    Object.entries(next).forEach(([key, item]) => (item ? value.set(key, item) : value.delete(key)));
    value.delete('threadId');
    onNavigate(`/admin/feedback${value.size ? `?${value}` : ''}`);
  }

  function select(id: number) {
    const value = new URLSearchParams(search);
    value.set('threadId', String(id));
    onNavigate(`/admin/feedback?${value}`);
  }

  async function send(content: string) {
    if (!detail) return;
    setPending(true);
    try {
      setDetail(requireApiData(
        await replyAdminFeedback(detail.id, { content }),
        resources.adminFeedback.replyFailed,
      ));
      await load();
    } catch (caught) {
      setError(errorText(caught, resources.adminFeedback.requestFailed));
    } finally {
      setPending(false);
    }
  }

  async function setStatus(status: FeedbackStatus) {
    if (!detail) return;
    setPending(true);
    try {
      setDetail(requireApiData(
        await updateAdminFeedbackStatus(detail.id, status),
        resources.adminFeedback.statusUpdateFailed,
      ));
      await load();
    } catch (caught) {
      setError(errorText(caught, resources.adminFeedback.requestFailed));
    } finally {
      setPending(false);
    }
  }

  const statusLabel = (status: FeedbackStatus) => status === 'OPEN' ? resources.feedback.open : resources.feedback.closed;
  const categoryLabel = (category: FeedbackCategory) => ({
    BUG: resources.feedback.categoryBug,
    SUGGESTION: resources.feedback.categorySuggestion,
    OTHER: resources.feedback.categoryOther,
  })[category];

  return (
    <section className="admin-feedback-page">
      <header className="feedback-page-header">
        <div>
          <h1>{resources.adminFeedback.title}</h1>
          {error ? <p className="error-text">{error}</p> : null}
        </div>
        <button
          aria-label={resources.adminFeedback.refresh}
          className="icon-button"
          onClick={() => void load()}
          title={resources.adminFeedback.refresh}
          type="button"
        >
          <RefreshCw aria-hidden="true" />
        </button>
      </header>
      <div className="feedback-filter-bar">
        <select aria-label={resources.adminFeedback.status} onChange={(event) => changeFilters({ status: event.target.value })} value={query.status}>
          <option value="">{resources.adminFeedback.allStatuses}</option>
          <option value="OPEN">{resources.feedback.open}</option>
          <option value="CLOSED">{resources.feedback.closed}</option>
        </select>
        <select aria-label={resources.adminFeedback.category} onChange={(event) => changeFilters({ category: event.target.value })} value={query.category}>
          <option value="">{resources.adminFeedback.allCategories}</option>
          <option value="BUG">{resources.feedback.categoryBug}</option>
          <option value="SUGGESTION">{resources.feedback.categorySuggestion}</option>
          <option value="OTHER">{resources.feedback.categoryOther}</option>
        </select>
        <label>
          <input checked={query.unreadOnly} onChange={(event) => changeFilters({ unreadOnly: String(event.target.checked) })} type="checkbox" />
          {resources.adminFeedback.unreadOnly}
        </label>
      </div>
      <div className="admin-feedback-layout">
        <div className="admin-feedback-table-wrap">
          <table>
            <thead>
              <tr>
                <th>{resources.adminFeedback.unread}</th>
                <th>{resources.adminFeedback.status}</th>
                <th>{resources.adminFeedback.category}</th>
                <th>{resources.adminFeedback.user}</th>
                <th>{resources.adminFeedback.subject}</th>
                <th>{resources.adminFeedback.updatedAt}</th>
              </tr>
            </thead>
            <tbody>
              {(page?.items ?? []).map((item) => (
                <tr className={item.id === selectedId ? 'selected' : ''} key={item.id} onClick={() => select(item.id)}>
                  <td>{item.unreadMessageCount || ''}</td>
                  <td>{statusLabel(item.status)}</td>
                  <td>{categoryLabel(item.category)}</td>
                  <td>{item.user?.email || item.user?.id || item.id}</td>
                  <td>{item.subject || resources.adminFeedback.untitled}</td>
                  <td>{new Date(item.updatedAt).toLocaleString(locale)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <aside className="admin-feedback-detail">
          {detail ? (
            <>
              <header>
                <h2>{detail.subject || resources.adminFeedback.untitled}</h2>
                <p>{statusLabel(detail.status)} · {categoryLabel(detail.category)}</p>
                {detail.sourcePath ? <small>{detail.sourcePath}</small> : null}
              </header>
              <FeedbackThreadTimeline detail={detail} />
              <div className="admin-feedback-actions">
                <button className="secondary-button" disabled={pending || detail.status === 'CLOSED'} onClick={() => void setStatus('CLOSED')} type="button">
                  {resources.adminFeedback.close}
                </button>
                <button className="secondary-button" disabled={pending || detail.status === 'OPEN'} onClick={() => void setStatus('OPEN')} type="button">
                  {resources.adminFeedback.reopen}
                </button>
              </div>
              <FeedbackComposer onSend={send} pending={pending} />
            </>
          ) : <p className="feedback-empty">{resources.adminFeedback.selectThread}</p>}
        </aside>
      </div>
    </section>
  );
}

function positive(value: string | null): number | undefined {
  const id = Number(value);
  return Number.isSafeInteger(id) && id > 0 ? id : undefined;
}

function errorText(error: unknown, fallback: string): string {
  return error instanceof ApiRequestError || error instanceof Error ? error.message || fallback : fallback;
}
