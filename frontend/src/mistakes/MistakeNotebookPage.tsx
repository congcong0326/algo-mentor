import { dueTimingLabel } from '../review-center/reviewPresentation';
import { ReviewCenterHeader, ReviewCenterStats, ReviewCenterToolbar, ReviewCenterCard, ReviewCenterPagination, ReviewCenterDetail } from '../review-center/ReviewCenterLayout';
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
import type { SupportedLocale } from '../i18n/locales';
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
      <ReviewCenterHeader mode="problems" onNavigate={onNavigate} actionLabel={reviewActionLabel}
        canStart={currentDueCount > 0} onStart={() => onNavigate(APP_ROUTES.reviewSession)} />
      <ReviewCenterStats items={[
        { label: resources.reviewCenter.remainingToday, value: remainingTodayCount },
        { label: resources.reviewCenter.reviewProblems, value: activeCount },
        { label: resources.reviewCenter.mistakes, value: mistakeCount },
      ]} />
      <ReviewCenterToolbar keyword={keyword} placeholder={resources.reviewCenter.searchPlaceholder}
        onSearch={(value) => updateFilters(value, mistakeOnly)} checked={mistakeOnly}
        filterLabel={resources.reviewCenter.mistakesOnly} onFilter={(value) => updateFilters(keyword, value)}
        onRefresh={() => void Promise.all([load(), loadSummary()])} />

      {(error || actionError) && <p className="error-text" role="alert">{error || actionError}</p>}

      <div className="mistake-list" aria-busy={loading}>
        {loading ? (
          <div className="loading-panel">{resources.reviewCenter.loadingCards}</div>
        ) : items.length === 0 ? (
          <div className="loading-panel">{resources.reviewCenter.emptyCards}</div>
        ) : items.map((overview) => {
          const card = overview.card;
          return (
          <ReviewCenterCard
            key={card.id} title={reviewCardTitle(card, locale)} archived={card.archived}
            cardRef={(element) => {
              if (element) cardRefs.current.set(card.id, element);
              else cardRefs.current.delete(card.id);
            }}
            onDetail={(trigger) => void handleOpenDetail(card, trigger)}
            onArchive={() => void handleArchive(card)}
            meta={<>
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
            </>}
            timeline={<ReviewCardTimeline
              locale={locale}
              onOpenReview={(review) => openReview(card, review)}
              resources={resources.reviewCenter}
              reviews={overview.recentCodeReviews}
              source={card.source}
            />}
          />
          );
        })}
      </div>

      {!loading && items.length > 0 && (
        <ReviewCenterPagination page={page} totalPages={totalPages} onPage={updatePage} />
      )}

      {detailCard && (
        <ReviewCenterDetail title={context?.problem.title || reviewCardTitle(detailCard, locale)} onClose={closeDetail}>
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
        </ReviewCenterDetail>
      )}
    </section>
  );
}

function formatDateTime(value: string, locale: SupportedLocale) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString(locale, { hour12: false });
}

function reviewCardTitle(card: ReviewCard, locale: SupportedLocale) {
  return locale === 'zh-CN' ? card.problemTitle || card.problemSlug : card.problemSlug;
}
