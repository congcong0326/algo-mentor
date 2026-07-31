import { Archive, ArchiveRestore, BookOpenCheck, Eye, RefreshCw, Search, X } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';
import { APP_ROUTES } from '../app/navigation';
import MarkdownView from '../components/MarkdownView';
import { useI18n } from '../i18n/I18nProvider';
import type { LocaleResources, SupportedLocale } from '../i18n/locales';
import ProblemNoteEditor from '../problem-notes/ProblemNoteEditor';
import {
  archiveReviewCard,
  getReviewCardContext,
  getReviewSummary,
  listReviewCards,
  requireApiData,
} from '../services/api';
import type {
  ReviewCard,
  ReviewCardContext,
  ReviewSummaryResponse,
} from '../types/api';
import { formatUpcomingReviewTime } from '../utils/time';

interface MistakeNotebookPageProps {
  onNavigate: (path: string) => void;
}

const dayMs = 24 * 60 * 60 * 1000;

export default function MistakeNotebookPage({ onNavigate }: MistakeNotebookPageProps) {
  const { locale, resources } = useI18n();
  const [items, setItems] = useState<ReviewCard[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [keyword, setKeyword] = useState('');
  const [mistakeOnly, setMistakeOnly] = useState(false);
  const [actionError, setActionError] = useState('');
  const [detailCard, setDetailCard] = useState<ReviewCard>();
  const [context, setContext] = useState<ReviewCardContext>();
  const [contextLoading, setContextLoading] = useState(false);
  const [contextError, setContextError] = useState('');
  const [reviewSummary, setReviewSummary] = useState<ReviewSummaryResponse>();
  const detailRequestId = useRef(0);
  const detailTriggerButtonRef = useRef<HTMLButtonElement | null>(null);
  const detailCloseButtonRef = useRef<HTMLButtonElement>(null);

  const stats = useMemo(() => {
    const active = items.filter((item) => !item.archived);
    const due = active.filter((item) => new Date(item.dueAt).getTime() <= Date.now());
    const mistakes = active.filter((item) => item.source === 'REVIEW_FAILED' || item.lapses > 0);
    return { active: active.length, due: due.length, mistakes: mistakes.length };
  }, [items]);
  const currentDueCount = reviewSummary?.dueCount ?? stats.due;
  const remainingTodayCount = reviewSummary?.remainingTodayCount ?? currentDueCount;
  const reviewActionLabel = reviewSummary === undefined && loading
    ? resources.reviewCenter.loadTodayReview
    : currentDueCount > 0
      ? resources.reviewCenter.startTodayReview(currentDueCount)
      : remainingTodayCount > 0
        ? resources.reviewCenter.availableAt(formatUpcomingReviewTime(reviewSummary?.nextDueAt, Date.now(), locale))
        : resources.reviewCenter.todayCompleted;

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [keyword, locale, mistakeOnly]);

  useEffect(() => {
    const controller = new AbortController();
    void loadSummary(controller.signal);
    return () => controller.abort();
  }, [locale]);

  useEffect(() => {
    if (detailCard) {
      detailCloseButtonRef.current?.focus();
    }
  }, [detailCard]);

  async function load(signal?: AbortSignal) {
    setLoading(true);
    setError('');
    try {
      const response = await listReviewCards({ keyword, mistakeOnly, limit: 80 }, signal);
      const cards = requireApiData(response, resources.reviewCenter.cardLoadFailed);
      setItems(cards);
      if (detailCard && !cards.some((item) => item.id === detailCard.id)) {
        closeDetail();
      }
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setError(loadError instanceof Error ? loadError.message : resources.reviewCenter.cardLoadFailed);
      }
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }

  async function loadSummary(signal?: AbortSignal) {
    try {
      const response = await getReviewSummary(signal);
      setReviewSummary(requireApiData(response, resources.reviewCenter.summaryLoadFailed));
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setReviewSummary(undefined);
      }
    }
  }

  async function handleArchive(card: ReviewCard) {
    setActionError('');
    try {
      const response = await archiveReviewCard(card.id, !card.archived);
      const updated = requireApiData(response, resources.reviewCenter.archiveUpdateFailed);
      setItems((current) => current.map((item) => (item.id === updated.id ? updated : item)));
      void loadSummary();
    } catch (archiveError) {
      setActionError(archiveError instanceof Error ? archiveError.message : resources.reviewCenter.archiveUpdateFailed);
    }
  }

  async function handleOpenDetail(card: ReviewCard, triggerButton: HTMLButtonElement) {
    const requestId = detailRequestId.current + 1;
    detailRequestId.current = requestId;
    detailTriggerButtonRef.current = triggerButton;
    setDetailCard(card);
    setContext(undefined);
    setContextError('');
    setContextLoading(true);
    try {
      const response = await getReviewCardContext(card.id);
      if (detailRequestId.current === requestId) {
        setContext(requireApiData(response, resources.reviewCenter.detailLoadFailed));
      }
    } catch (loadError) {
      if (detailRequestId.current === requestId) {
        setContextError(loadError instanceof Error ? loadError.message : resources.reviewCenter.detailLoadFailed);
      }
    } finally {
      if (detailRequestId.current === requestId) {
        setContextLoading(false);
      }
    }
  }

  function closeDetail() {
    detailRequestId.current += 1;
    setDetailCard(undefined);
    setContext(undefined);
    setContextError('');
    setContextLoading(false);
    if (detailTriggerButtonRef.current?.isConnected) {
      detailTriggerButtonRef.current.focus();
    }
    detailTriggerButtonRef.current = null;
  }

  return (
    <section className="mistake-page" aria-labelledby="mistake-title">
      <header className="mistake-header">
        <h1 id="mistake-title">{resources.reviewCenter.title}</h1>
        <button
          className="primary-button mistake-review-button"
          disabled={currentDueCount === 0}
          onClick={() => currentDueCount > 0 && onNavigate(APP_ROUTES.reviewSession)}
          type="button"
        >
          <BookOpenCheck aria-hidden="true" />
          <span>{reviewActionLabel}</span>
        </button>
      </header>

      <dl className="mistake-stat-grid" aria-label={resources.reviewCenter.overviewAriaLabel}>
        <div><dt>{resources.reviewCenter.remainingToday}</dt><dd>{remainingTodayCount}</dd></div>
        <div><dt>{resources.reviewCenter.reviewProblems}</dt><dd>{stats.active}</dd></div>
        <div><dt>{resources.reviewCenter.mistakes}</dt><dd>{stats.mistakes}</dd></div>
      </dl>

      <section className="mistake-toolbar" aria-label={resources.reviewCenter.filtersAriaLabel}>
        <label className="search-field">
          <Search aria-hidden="true" />
          <input
            onChange={(event) => setKeyword(event.target.value)}
            placeholder={resources.reviewCenter.searchPlaceholder}
            value={keyword}
          />
        </label>
        <label className="checkbox-control">
          <input checked={mistakeOnly} onChange={(event) => setMistakeOnly(event.target.checked)} type="checkbox" />
          <span>{resources.reviewCenter.mistakesOnly}</span>
        </label>
        <button aria-label={resources.reviewCenter.refreshCards} className="icon-button" onClick={() => void load()} type="button">
          <RefreshCw aria-hidden="true" />
        </button>
      </section>

      {(error || actionError) && <p className="error-text" role="alert">{error || actionError}</p>}

      <div className="mistake-list" aria-busy={loading}>
        {loading ? (
          <div className="loading-panel">{resources.reviewCenter.loadingCards}</div>
        ) : items.length === 0 ? (
          <div className="loading-panel">{resources.reviewCenter.emptyCards}</div>
        ) : items.map((card) => (
          <article className="mistake-note-card" key={card.id}>
            <div className="mistake-note-main">
              <h2>{reviewCardTitle(card, locale)}</h2>
              <div className="mistake-note-meta">
                <span>{resources.reviewCenter.sourceLabels[card.source]}</span>
                <span>{dueTimingLabel(card.dueAt, resources.reviewCenter)}</span>
                {card.lastRating && (
                  <span className="mistake-note-rating">
                    {resources.reviewCenter.lastRating(resources.reviewCenter.ratingLabels[card.lastRating])}
                  </span>
                )}
                {card.lapses > 0 && (
                  <span className="mistake-note-lapses">{resources.reviewCenter.forgottenCount(card.lapses)}</span>
                )}
              </div>
            </div>
            <div className="mistake-note-actions">
              <button
                aria-label={resources.reviewCenter.viewCardDetail(reviewCardTitle(card, locale))}
                className="icon-button"
                onClick={(event) => void handleOpenDetail(card, event.currentTarget)}
                title={resources.reviewCenter.viewDetail}
                type="button"
              >
                <Eye aria-hidden="true" />
              </button>
              <button
                aria-label={card.archived ? resources.reviewCenter.restoreReview : resources.reviewCenter.removeFromReview}
                className="icon-button"
                onClick={() => void handleArchive(card)}
                title={card.archived ? resources.reviewCenter.restoreReview : resources.reviewCenter.removeFromReview}
                type="button"
              >
                {card.archived ? <ArchiveRestore aria-hidden="true" /> : <Archive aria-hidden="true" />}
              </button>
            </div>
          </article>
        ))}
      </div>

      {detailCard && (
        <div className="modal-backdrop">
          <section aria-labelledby="review-card-detail-title" aria-modal="true" className="mistake-detail-modal" role="dialog">
            <div className="modal-heading">
              <div>
                <p className="eyebrow">Review Card</p>
                <h2 id="review-card-detail-title">{reviewCardTitle(detailCard, locale)}</h2>
              </div>
              <button aria-label={resources.reviewCenter.closeDetail} className="icon-button" onClick={closeDetail} ref={detailCloseButtonRef} type="button">
                <X aria-hidden="true" />
              </button>
            </div>

            {contextLoading ? (
              <div className="loading-panel">{resources.reviewCenter.loadingDetail}</div>
            ) : contextError ? (
              <p className="error-text" role="alert">{contextError}</p>
            ) : context ? (
              <div className="mistake-detail-content">
                <section className="review-problem-content"><MarkdownView content={context.problem.contentMarkdown} /></section>
                <ProblemNoteEditor problemSlug={context.problem.slug} />
                <section className="mistake-detail-section">
                  <h3>{resources.reviewCenter.recentHistory}</h3>
                  {context.recentAttempts.length > 0 ? (
                    <ol className="review-history-list">
                      {context.recentAttempts.map((attempt) => (
                        <li key={attempt.id}>
                          <div className="review-history-meta">
                            <strong>{resources.reviewCenter.ratingLabels[attempt.rating]}</strong>
                            <span>{formatDateTime(attempt.reviewedAt, locale)}</span>
                          </div>
                          <small>{resources.reviewCenter.intervalChange(
                            attempt.schedulingBefore.intervalDays,
                            attempt.schedulingAfter.intervalDays,
                          )}</small>
                        </li>
                      ))}
                    </ol>
                  ) : <p>{resources.reviewCenter.noHistory}</p>}
                </section>
              </div>
            ) : null}
          </section>
        </div>
      )}
    </section>
  );
}

function dueTimingLabel(dueAt: string, resources: LocaleResources['reviewCenter']) {
  const dueTime = new Date(dueAt).getTime();
  if (Number.isNaN(dueTime)) {
    return resources.dueUnknown;
  }
  const diffDays = Math.round((startOfDay(dueTime) - startOfDay(Date.now())) / dayMs);
  if (diffDays < 0) return resources.overdue(Math.abs(diffDays));
  if (diffDays === 0) return resources.dueToday;
  if (diffDays === 1) return resources.reviewTomorrow;
  return resources.reviewInDays(diffDays);
}

function startOfDay(time: number) {
  const date = new Date(time);
  date.setHours(0, 0, 0, 0);
  return date.getTime();
}

function formatDateTime(value: string, locale: SupportedLocale) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString(locale, { hour12: false });
}

function reviewCardTitle(card: ReviewCard, locale: SupportedLocale) {
  return locale === 'zh-CN' ? card.problemTitle || card.problemSlug : card.problemSlug;
}
