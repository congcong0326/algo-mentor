import { ArrowLeft, Plus, RefreshCw, X } from 'lucide-react';
import { type FormEvent, type KeyboardEvent, useEffect, useRef, useState } from 'react';
import { useI18n } from '../i18n/I18nProvider';
import { ApiRequestError, createFeedback, getFeedbackThread, getFeedbackThreads, markFeedbackRead, replyFeedback, requireApiData } from '../services/api';
import type { FeedbackCategory, FeedbackStatus, FeedbackThreadDetail, FeedbackThreadPage } from '../types/api';
import { captureFeedbackNavigationContext, getFeedbackSourceContext, type FeedbackSourceContext } from './feedbackSourceContext';
import FeedbackComposer from './FeedbackComposer';
import FeedbackThreadList from './FeedbackThreadList';
import FeedbackThreadTimeline from './FeedbackThreadTimeline';

type DialogView = 'list' | 'detail' | 'create';

export default function UserFeedbackDialog({ onClose, onUnreadCountChanged }: {
  onClose: () => void;
  onUnreadCountChanged?: (count: number) => void;
}) {
  const { resources } = useI18n();
  const [status, setStatus] = useState<FeedbackStatus | ''>('');
  const [page, setPage] = useState<FeedbackThreadPage>();
  const [selectedId, setSelectedId] = useState<number>();
  const [detail, setDetail] = useState<FeedbackThreadDetail>();
  const [view, setView] = useState<DialogView>('list');
  const [sourceContext, setSourceContext] = useState<FeedbackSourceContext>();
  const [loading, setLoading] = useState(true);
  const [detailLoading, setDetailLoading] = useState(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const dialogRef = useRef<HTMLDivElement>(null);
  const closeButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    const previousFocus = document.activeElement instanceof HTMLElement ? document.activeElement : undefined;
    closeButtonRef.current?.focus();
    const onKeyDown = (event: globalThis.KeyboardEvent) => {
      if (event.key === 'Escape') {
        event.preventDefault();
        onClose();
      }
    };
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('keydown', onKeyDown);
      previousFocus?.focus();
    };
  }, [onClose]);

  useEffect(() => {
    void load();
  }, [status]);

  async function load() {
    setLoading(true);
    setError('');
    try {
      const next = requireApiData(await getFeedbackThreads({ status }), resources.feedback.listLoadFailed);
      setPage(next);
      onUnreadCountChanged?.(next.unreadMessageCount);
    } catch (caught) {
      setError(errorMessage(caught, resources.feedback.listLoadFailed));
    } finally {
      setLoading(false);
    }
  }

  async function loadDetail(id: number) {
    setDetailLoading(true);
    setError('');
    try {
      const next = requireApiData(await getFeedbackThread(id), resources.feedback.detailLoadFailed);
      setDetail(next);
      if (next.unreadMessageCount > 0) {
        const read = requireApiData(await markFeedbackRead(id), resources.feedback.markReadFailed);
        setDetail({
          ...next,
          unreadMessageCount: 0,
          messages: next.messages.map((message) => message.senderType === 'ADMIN' && !message.readAt
            ? { ...message, readAt: new Date().toISOString() }
            : message),
        });
        onUnreadCountChanged?.(read.unreadMessageCount);
        await load();
      }
    } catch (caught) {
      setError(errorMessage(caught, resources.feedback.detailLoadFailed));
    } finally {
      setDetailLoading(false);
    }
  }

  function selectThread(id: number) {
    setSelectedId(id);
    setDetail(undefined);
    setView('detail');
    void loadDetail(id);
  }

  function changeStatus(next: FeedbackStatus | '') {
    setStatus(next);
    setSelectedId(undefined);
    setDetail(undefined);
    setView('list');
  }

  function openCreateForm() {
    captureFeedbackNavigationContext();
    setSourceContext(getFeedbackSourceContext());
    setError('');
    setView('create');
  }

  async function refresh() {
    await load();
  }

  async function send(content: string) {
    if (!detail) return;
    setPending(true);
    setError('');
    try {
      const updated = requireApiData(await replyFeedback(detail.id, { content }), resources.feedback.replyFailed);
      setDetail(updated);
      await load();
    } catch (caught) {
      setError(errorMessage(caught, resources.feedback.replyFailed));
    } finally {
      setPending(false);
    }
  }

  async function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (pending) return;
    const form = new FormData(event.currentTarget);
    const category = form.get('category') as FeedbackCategory;
    const content = String(form.get('content') ?? '');
    setPending(true);
    setError('');
    try {
      const created = requireApiData(await createFeedback({
        category,
        subject: String(form.get('subject') ?? '') || undefined,
        content,
        ...(sourceContext ? {
          sourcePath: sourceContext.sourcePath,
          sourceRequestId: sourceContext.sourceRequestId,
          sourceRunId: sourceContext.sourceRunId,
        } : {}),
      }), resources.feedback.createFailed);
      setDetail(created);
      setSelectedId(created.id);
      setSourceContext(undefined);
      setView('detail');
      if (status === 'CLOSED') {
        setStatus('');
      } else {
        await load();
      }
    } catch (caught) {
      setError(errorMessage(caught, resources.feedback.createFailed));
    } finally {
      setPending(false);
    }
  }

  function trapFocus(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key !== 'Tab') return;
    const focusable = dialogRef.current?.querySelectorAll<HTMLElement>(
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])',
    );
    if (!focusable?.length) return;
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  const showList = view === 'list';
  const showCreateForm = view === 'create';
  const showDetail = view === 'detail';

  return (
    <div className="feedback-dialog-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div
        aria-describedby={error ? 'feedback-dialog-error' : undefined}
        aria-labelledby="feedback-dialog-title"
        aria-modal="true"
        className="feedback-dialog"
        onKeyDown={trapFocus}
        ref={dialogRef}
        role="dialog"
      >
        <header className="feedback-dialog-header">
          <div>
            <h2 id="feedback-dialog-title">{resources.feedback.title}</h2>
            {error ? <p className="error-text" id="feedback-dialog-error" role="alert">{error}</p> : null}
          </div>
          <div className="feedback-dialog-actions">
            <button aria-label={resources.feedback.refresh} className="icon-button" onClick={() => void refresh()} title={resources.feedback.refresh} type="button">
              <RefreshCw aria-hidden="true" />
            </button>
            <button aria-label={resources.feedback.closeDialog} className="icon-button" onClick={onClose} ref={closeButtonRef} title={resources.feedback.closeDialog} type="button">
              <X aria-hidden="true" />
            </button>
          </div>
        </header>

        {showCreateForm ? (
          <form aria-label={resources.feedback.createFormLabel} className="feedback-create-form" onSubmit={(event) => void create(event)}>
            <div className="feedback-mobile-back-row">
              <button className="icon-button" onClick={() => setView(selectedId ? 'detail' : 'list')} title={resources.feedback.backToList} type="button">
                <ArrowLeft aria-hidden="true" />
              </button>
            </div>
            <label>{resources.feedback.category}
              <select defaultValue="BUG" name="category">
                <option value="BUG">{resources.feedback.categoryBug}</option>
                <option value="SUGGESTION">{resources.feedback.categorySuggestion}</option>
                <option value="OTHER">{resources.feedback.categoryOther}</option>
              </select>
            </label>
            <label>{resources.feedback.subject}
              <input maxLength={200} name="subject" />
            </label>
            <label>{resources.feedback.content}
              <textarea maxLength={4000} name="content" required />
            </label>
            <footer>
              <button className="secondary-button" onClick={() => setView(selectedId ? 'detail' : 'list')} type="button">{resources.feedback.cancel}</button>
              <button className="primary-button" disabled={pending} type="submit">{pending ? resources.feedback.creating : resources.feedback.create}</button>
            </footer>
          </form>
        ) : (
          <>
            <div className="feedback-dialog-toolbar">
              <div className="feedback-filter-bar" role="group" aria-label={resources.feedback.filterLabel}>
                <button aria-pressed={status === ''} onClick={() => changeStatus('')} type="button">{resources.feedback.all}</button>
                <button aria-pressed={status === 'OPEN'} onClick={() => changeStatus('OPEN')} type="button">{resources.feedback.open}</button>
                <button aria-pressed={status === 'CLOSED'} onClick={() => changeStatus('CLOSED')} type="button">{resources.feedback.closed}</button>
              </div>
              <button className="primary-button" onClick={openCreateForm} type="button">
                <Plus aria-hidden="true" />
                <span>{resources.feedback.newFeedback}</span>
              </button>
            </div>
            <div className="feedback-dialog-workspace" data-view={view}>
              <aside aria-label={resources.feedback.threadListLabel} className={showList ? undefined : 'feedback-mobile-hidden'}>
                {loading ? <p className="feedback-loading" role="status">{resources.feedback.loading}</p> : <FeedbackThreadList items={page?.items ?? []} onSelect={selectThread} selectedId={selectedId} />}
              </aside>
              <article className={showDetail ? 'feedback-detail' : 'feedback-detail feedback-mobile-hidden'}>
                <button className="feedback-mobile-back icon-button" onClick={() => setView('list')} title={resources.feedback.backToList} type="button">
                  <ArrowLeft aria-hidden="true" />
                </button>
                {detailLoading ? <p className="feedback-loading" role="status">{resources.feedback.detailLoading}</p> : detail ? <>
                  <header>
                    <div>
                      <h3>{detail.subject || resources.feedback.untitled}</h3>
                      <p>{detail.category} · {detail.status}</p>
                    </div>
                  </header>
                  <FeedbackThreadTimeline detail={detail} />
                  <FeedbackComposer closed={detail.status === 'CLOSED'} onSend={send} pending={pending} />
                </> : <p className="feedback-empty">{resources.feedback.selectThread}</p>}
              </article>
            </div>
          </>
        )}
      </div>
    </div>
  );
}

function errorMessage(error: unknown, fallback: string): string {
  return error instanceof ApiRequestError || error instanceof Error ? error.message || fallback : fallback;
}
