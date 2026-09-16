import { Archive, ArchiveRestore, BookOpen, BookOpenCheck, ChevronLeft, ChevronRight, Eye, RefreshCw, Search, X } from 'lucide-react';
import { useEffect, useRef, useState } from 'react';
import {
  APP_ROUTES,
  REVIEW_CENTER_REVIEW_ORIGIN,
  learningPlanPracticeSubmissionsPath,
  reviewCenterPath,
  reviewCenterSearchOptionsFromSearch,
} from '../app/navigation';
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
  ReviewCardOverview,
  ReviewCardOverviewPage,
  ReviewSummaryResponse,
} from '../types/api';
import { formatUpcomingReviewTime } from '../utils/time';
import ReviewCardTimeline from './ReviewCardTimeline';
import { reviewProblemStatementMarkdown } from './reviewProblemStatement';

interface MistakeNotebookPageProps {
  onNavigate: (path: string, options?: { replace?: boolean }) => void;
  search?: string;
}

const dayMs = 24 * 60 * 60 * 1000;

export default function MistakeNotebookPage({ onNavigate, search = '' }: MistakeNotebookPageProps) {
  const { locale, resources } = useI18n();
  const initialFilters = reviewCenterSearchOptionsFromSearch(search);
  const [reviewCardsPage, setReviewCardsPage] = useState<ReviewCardOverviewPage>();
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [keyword, setKeyword] = useState(initialFilters.keyword ?? '');
  const [mistakeOnly, setMistakeOnly] = useState(initialFilters.mistakeOnly ?? false);
  const [page, setPage] = useState(initialFilters.page ?? 1);
  const [actionError, setActionError] = useState('');
  const [detailCard, setDetailCard] = useState<ReviewCard>();
  const [context, setContext] = useState<ReviewCardContext>();
  const [contextLoading, setContextLoading] = useState(false);
  const [contextError, setContextError] = useState('');
  const [reviewSummary, setReviewSummary] = useState<ReviewSummaryResponse>();
  const detailRequestId = useRef(0);
  const detailTriggerButtonRef = useRef<HTMLButtonElement | null>(null);
  const detailCloseButtonRef = useRef<HTMLButtonElement>(null);
  const cardRefs = useRef(new Map<number, HTMLElement>());

  const items = reviewCardsPage?.items ?? [];
  const fallbackDueCount = items.filter((item) => (
    !item.card.archived && new Date(item.card.dueAt).getTime() <= Date.now()
  )).length;
  const activeCount = reviewCardsPage?.activeCount ?? items.filter((item) => !item.card.archived).length;
  const mistakeCount = reviewCardsPage?.mistakeCount ?? items.filter((item) => (
    !item.card.archived && (item.card.source === 'REVIEW_FAILED' || item.card.lapses > 0)
  )).length;
  const totalPages = Math.max(1, Math.ceil((reviewCardsPage?.total ?? 0) / (reviewCardsPage?.pageSize ?? 10)));
  const currentDueCount = reviewSummary?.dueCount ?? fallbackDueCount;
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
  }, [keyword, locale, mistakeOnly, page]);

  useEffect(() => {
    const filters = reviewCenterSearchOptionsFromSearch(search);
    setKeyword(filters.keyword ?? '');
    setMistakeOnly(filters.mistakeOnly ?? false);
    setPage(filters.page ?? 1);
  }, [search]);

  useEffect(() => {
    const filters = reviewCenterSearchOptionsFromSearch(search);
    const focusCard = filters.focusCard;
    if (
      !focusCard
      || loading
      || keyword !== (filters.keyword ?? '')
      || mistakeOnly !== (filters.mistakeOnly ?? false)
      || page !== (filters.page ?? 1)
    ) {
      return;
    }
    const card = cardRefs.current.get(focusCard);
    if (card) {
      card.scrollIntoView({ block: 'center' });
      card.focus();
    }
    onNavigate(reviewCenterPath({ keyword, mistakeOnly, page }), { replace: true });
  }, [items, keyword, loading, mistakeOnly, onNavigate, page, search]);

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
      const response = await listReviewCards({ keyword, mistakeOnly, page }, signal);
      const nextPage = requireApiData(response, resources.reviewCenter.cardLoadFailed);
      setReviewCardsPage(nextPage);
      if (nextPage.page !== page) {
        setPage(nextPage.page);
        onNavigate(reviewCenterPath({ keyword, mistakeOnly, page: nextPage.page }), { replace: true });
      }
      if (detailCard && !nextPage.items.some((item) => item.card.id === detailCard.id)) {
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
      requireApiData(response, resources.reviewCenter.archiveUpdateFailed);
      await Promise.all([load(), loadSummary()]);
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

  function updateFilters(nextKeyword: string, nextMistakeOnly: boolean) {
    setKeyword(nextKeyword);
    setMistakeOnly(nextMistakeOnly);
    setPage(1);
    onNavigate(reviewCenterPath({ keyword: nextKeyword, mistakeOnly: nextMistakeOnly }), { replace: true });
  }

  function updatePage(nextPage: number) {
    setPage(nextPage);
    onNavigate(reviewCenterPath({ keyword, mistakeOnly, page: nextPage }), { replace: true });
  }

  function openReview(card: ReviewCard, review: ReviewCardOverview['recentCodeReviews'][number]) {
    onNavigate(learningPlanPracticeSubmissionsPath(
      review.planId,
      review.phaseIndex,
      review.problemSlug,
      {
        reviewId: review.reviewId,
        from: REVIEW_CENTER_REVIEW_ORIGIN,
        returnTo: reviewCenterPath({ keyword, mistakeOnly, page, focusCard: card.id }),
      },
    ));
  }

  return (
    <section className="mistake-page" aria-labelledby="mistake-title">
      <header className="mistake-header">
        <h1 id="mistake-title">{resources.reviewCenter.title}</h1>
        <div className="mistake-header-actions">
          <button className="secondary-button compact" onClick={() => onNavigate(APP_ROUTES.knowledgeReview)} type="button">
            <BookOpen aria-hidden="true" />
            <span>知识库复习</span>
          </button>
          <button
            className="primary-button mistake-review-button"
            disabled={currentDueCount === 0}
            onClick={() => currentDueCount > 0 && onNavigate(APP_ROUTES.reviewSession)}
            type="button"
          >
            <BookOpenCheck aria-hidden="true" />
            <span>{reviewActionLabel}</span>
          </button>
        </div>
      </header>

      <dl className="mistake-stat-grid" aria-label={resources.reviewCenter.overviewAriaLabel}>
        <div><dt>{resources.reviewCenter.remainingToday}</dt><dd>{remainingTodayCount}</dd></div>
        <div><dt>{resources.reviewCenter.reviewProblems}</dt><dd>{activeCount}</dd></div>
        <div><dt>{resources.reviewCenter.mistakes}</dt><dd>{mistakeCount}</dd></div>
      </dl>

      <section className="mistake-toolbar" aria-label={resources.reviewCenter.filtersAriaLabel}>
        <label className="search-field">
          <Search aria-hidden="true" />
          <input
            onChange={(event) => updateFilters(event.target.value, mistakeOnly)}
            placeholder={resources.reviewCenter.searchPlaceholder}
            value={keyword}
          />
        </label>
        <label className="checkbox-control">
          <input
            checked={mistakeOnly}
            onChange={(event) => updateFilters(keyword, event.target.checked)}
            type="checkbox"
          />
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
        ) : items.map((overview) => {
          const card = overview.card;
          return (
          <article
            className="mistake-note-card"
            key={card.id}
            ref={(element) => {
              if (element) cardRefs.current.set(card.id, element);
              else cardRefs.current.delete(card.id);
            }}
            tabIndex={-1}
          >
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
            <ReviewCardTimeline
              locale={locale}
              onOpenReview={(review) => openReview(card, review)}
              resources={resources.reviewCenter}
              reviews={overview.recentCodeReviews}
              source={card.source}
            />
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
          );
        })}
      </div>

      {!loading && items.length > 0 && (
        <nav aria-label={resources.common.pageStatus(page, totalPages)} className="pagination-row mistake-pagination">
          <button
            aria-label={resources.common.previousPage}
            className="icon-button"
            disabled={page <= 1}
            onClick={() => updatePage(page - 1)}
            type="button"
          >
            <ChevronLeft aria-hidden="true" />
          </button>
          <span>{resources.common.pageStatus(page, totalPages)}</span>
          <button
            aria-label={resources.common.nextPage}
            className="icon-button"
            disabled={page >= totalPages}
            onClick={() => updatePage(page + 1)}
            type="button"
          >
            <ChevronRight aria-hidden="true" />
          </button>
        </nav>
      )}

      {detailCard && (
        <div className="modal-backdrop">
          <section aria-labelledby="review-card-detail-title" aria-modal="true" className="mistake-detail-modal" role="dialog">
            <div className="modal-heading">
              <div>
                <p className="eyebrow">Review Card</p>
                <h2 id="review-card-detail-title">
                  {context?.problem.title || reviewCardTitle(detailCard, locale)}
                </h2>
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
                <section className="review-problem-content">
                  <MarkdownView content={reviewProblemStatementMarkdown(context.problem)} />
                </section>
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
