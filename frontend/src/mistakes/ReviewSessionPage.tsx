import {
  AlertCircle,
  ArrowLeft,
  ArrowRight,
  CheckCircle2,
  Loader2,
  RefreshCw,
  Sparkles,
} from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';
import { APP_ROUTES } from '../app/navigation';
import MarkdownView from '../components/MarkdownView';
import { formatDifficulty } from '../i18n/formatters';
import { useI18n } from '../i18n/I18nProvider';
import type { LocaleResources, SupportedLocale } from '../i18n/locales';
import ProblemNoteEditor from '../problem-notes/ProblemNoteEditor';
import {
  getReviewCardContext,
  getReviewQueue,
  requireApiData,
  submitReviewAttempt,
} from '../services/api';
import type {
  ReviewAttempt,
  ReviewCard,
  ReviewCardContext,
  ReviewIntervalPreview,
  ReviewRating,
} from '../types/api';
import { generateClientId } from '../utils/id';
import { reviewProblemStatementMarkdown } from './reviewProblemStatement';

interface ReviewSessionPageProps {
  onNavigate: (path: string) => void;
}

const ratingKeys: Record<string, ReviewRating> = {
  '1': 'AGAIN',
  '2': 'HARD',
  '3': 'GOOD',
  '4': 'EASY',
};

export default function ReviewSessionPage({ onNavigate }: ReviewSessionPageProps) {
  const { locale, resources } = useI18n();
  const [queue, setQueue] = useState<ReviewCard[]>([]);
  const [index, setIndex] = useState(0);
  const [context, setContext] = useState<ReviewCardContext>();
  const [attempt, setAttempt] = useState<ReviewAttempt>();
  const [clientAttemptId, setClientAttemptId] = useState('');
  const [loading, setLoading] = useState(true);
  const [contextLoading, setContextLoading] = useState(false);
  const [submittingRating, setSubmittingRating] = useState<ReviewRating>();
  const [noteDirty, setNoteDirty] = useState(false);
  const [error, setError] = useState('');

  const current = queue[index];
  const finished = !loading && !error && queue.length === 0;
  const progressLabel = useMemo(() => (
    queue.length > 0 ? `${Math.min(index + 1, queue.length)} / ${queue.length}` : '0 / 0'
  ), [index, queue.length]);

  useEffect(() => {
    const controller = new AbortController();
    void loadQueue(controller.signal);
    return () => controller.abort();
  }, [locale]);

  useEffect(() => {
    if (!current) {
      setContext(undefined);
      return;
    }
    const controller = new AbortController();
    void loadContext(current.id, controller.signal);
    return () => controller.abort();
  }, [current?.id, locale]);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      const rating = ratingKeys[event.key];
      if (!rating || isTextEditingTarget(event.target) || !context || attempt || submittingRating) {
        return;
      }
      event.preventDefault();
      void handleRate(rating);
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [attempt, context, submittingRating]);

  async function loadQueue(signal?: AbortSignal) {
    setLoading(true);
    setError('');
    try {
      const response = await getReviewQueue(20, signal);
      const data = requireApiData(response, resources.reviewCenter.queueLoadFailed);
      setQueue(data.items);
      setIndex(0);
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setQueue([]);
        setError(loadError instanceof Error ? loadError.message : resources.reviewCenter.queueLoadFailed);
      }
    } finally {
      if (!signal?.aborted) {
        setLoading(false);
      }
    }
  }

  async function loadContext(cardId: number, signal?: AbortSignal) {
    setContextLoading(true);
    setContext(undefined);
    setAttempt(undefined);
    setSubmittingRating(undefined);
    setNoteDirty(false);
    setClientAttemptId(generateClientId());
    setError('');
    try {
      const response = await getReviewCardContext(cardId, signal);
      setContext(requireApiData(response, resources.reviewCenter.cardLoadFailed));
    } catch (loadError) {
      if (!(loadError instanceof DOMException && loadError.name === 'AbortError')) {
        setError(loadError instanceof Error ? loadError.message : resources.reviewCenter.cardLoadFailed);
      }
    } finally {
      if (!signal?.aborted) {
        setContextLoading(false);
      }
    }
  }

  async function handleRate(rating: ReviewRating) {
    if (!current || !context || attempt || submittingRating || !clientAttemptId) {
      return;
    }
    setSubmittingRating(rating);
    setError('');
    try {
      const response = await submitReviewAttempt(current.id, clientAttemptId, rating);
      setAttempt(requireApiData(response, resources.reviewCenter.ratingSubmitFailed));
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : resources.reviewCenter.ratingSubmitFailed);
    } finally {
      setSubmittingRating(undefined);
    }
  }

  function handleBack() {
    if (!confirmDiscardNote()) {
      return;
    }
    onNavigate(APP_ROUTES.mistakes);
  }

  function nextCard() {
    if (!attempt || !confirmDiscardNote()) {
      return;
    }
    if (index + 1 >= queue.length) {
      setQueue([]);
      setIndex(0);
      return;
    }
    setIndex((currentIndex) => currentIndex + 1);
  }

  function confirmDiscardNote() {
    return !noteDirty || window.confirm(resources.reviewCenter.discardNoteConfirm);
  }

  function intervalFor(rating: ReviewRating) {
    return context?.intervalPreviews.find((item) => item.rating === rating);
  }

  return (
    <section className="review-session-page" aria-labelledby="review-session-title">
      <header className="review-workbench-toolbar">
        <button className="secondary-button compact" onClick={handleBack} type="button">
          <ArrowLeft aria-hidden="true" />
          <span>{resources.reviewCenter.backToReviewCenter}</span>
        </button>
        <div className="review-workbench-heading">
          <h1 id="review-session-title">{reviewProblemTitle(context, current, locale, resources.reviewCenter.spacedReview)}</h1>
          <span>
            {context?.problem.difficulty || current?.problemDifficulty
              ? formatDifficulty(context?.problem.difficulty || current?.problemDifficulty, resources)
              : resources.reviewCenter.unknownDifficulty}
          </span>
        </div>
        <div className="review-workbench-status">
          <strong>{progressLabel}</strong>
          <span>{resources.reviewCenter.fsrsStateLabels[context?.card.fsrsState || current?.fsrsState || 'LEARNING']}</span>
        </div>
      </header>

      {error && <p className="error-text" role="alert">{error}</p>}

      {loading ? (
        <div className="loading-panel">{resources.reviewCenter.preparingQueue}</div>
      ) : finished ? (
        <div className="review-complete">
          <CheckCircle2 aria-hidden="true" />
          <h2>{resources.reviewCenter.queueCompleted}</h2>
          <button className="primary-button" onClick={() => onNavigate(APP_ROUTES.mistakes)} type="button">
            {resources.reviewCenter.backToReviewCenter}
          </button>
        </div>
      ) : contextLoading || !context ? (
        <div className="loading-panel">{resources.reviewCenter.loadingStatement}</div>
      ) : (
        <article className="review-card-workbench">
          <div className="review-card-scroll">
            <section className="review-problem-content" aria-label={resources.reviewCenter.fullStatementAriaLabel}>
              <MarkdownView content={reviewProblemStatementMarkdown(context.problem)} />
            </section>

            <ProblemNoteEditor onDirtyChange={setNoteDirty} problemSlug={context.problem.slug} />

            <details className="review-attempt-history">
              <summary>
                <span>
                  <strong>{resources.reviewCenter.history}</strong>
                  <small>
                    {context.recentAttempts.length > 0
                      ? resources.reviewCenter.recentCount(context.recentAttempts.length)
                      : resources.reviewCenter.noHistory}
                  </small>
                </span>
              </summary>
              {context.recentAttempts.length > 0 ? (
                <ol>
                  {context.recentAttempts.map((history) => (
                    <li key={history.id}>
                      <strong>{resources.reviewCenter.ratingLabels[history.rating]}</strong>
                      <span>{formatDateTime(history.reviewedAt, locale)}</span>
                      <small>
                        {resources.reviewCenter.intervalChange(
                          history.schedulingBefore.intervalDays,
                          history.schedulingAfter.intervalDays,
                        )}
                      </small>
                    </li>
                  ))}
                </ol>
              ) : (
                <p>{resources.reviewCenter.historyAfterRating}</p>
              )}
            </details>

            {attempt && (
              <section className="review-result" aria-label={resources.reviewCenter.resultAriaLabel}>
                <h3>{resources.reviewCenter.ratingLabels[attempt.rating]}</h3>
                <p>{resources.reviewCenter.nextReview(formatDueLabel(
                  attempt.schedulingAfter.dueAt,
                  attempt.schedulingAfter.intervalDays,
                  resources.reviewCenter,
                ))}</p>
              </section>
            )}
          </div>

          <footer className="review-rating-bar" aria-label={resources.reviewCenter.ratingAriaLabel}>
            <div className="review-rating-grid">
              {(Object.keys(resources.reviewCenter.ratingLabels) as ReviewRating[]).map((rating, ratingIndex) => (
                <RatingButton
                  description={resources.reviewCenter.ratingDescriptions[rating]}
                  interval={intervalFor(rating)}
                  key={rating}
                  loading={submittingRating === rating}
                  onClick={() => void handleRate(rating)}
                  rating={rating}
                  shortcut={ratingIndex + 1}
                  selected={attempt?.rating === rating}
                  submitted={Boolean(attempt)}
                />
              ))}
            </div>
            <button className="secondary-button review-next-button" disabled={!attempt} onClick={nextCard} type="button">
              <ArrowRight aria-hidden="true" />
              <span>{resources.reviewCenter.nextProblem}</span>
            </button>
          </footer>
        </article>
      )}
    </section>
  );
}

function isTextEditingTarget(target: EventTarget | null): boolean {
  return target instanceof HTMLElement
    && (target.isContentEditable || target.matches('input, textarea, select'));
}

function RatingButton({
  description,
  interval,
  loading,
  onClick,
  rating,
  selected,
  shortcut,
  submitted,
}: {
  description: string;
  interval?: ReviewIntervalPreview;
  loading: boolean;
  onClick: () => void;
  rating: ReviewRating;
  selected: boolean;
  shortcut: number;
  submitted: boolean;
}) {
  const { resources } = useI18n();
  const label = resources.reviewCenter.ratingLabels[rating];
  const intervalLabel = interval
    ? formatDueLabel(interval.dueAt, interval.intervalDays, resources.reviewCenter)
    : resources.reviewCenter.calculating;
  const Icon = rating === 'AGAIN'
    ? RefreshCw
    : rating === 'HARD'
      ? AlertCircle
      : rating === 'GOOD'
        ? CheckCircle2
        : Sparkles;

  return (
    <button
      aria-keyshortcuts={String(shortcut)}
      aria-label={resources.reviewCenter.ratingButtonAriaLabel(label, description, intervalLabel)}
      className={`review-rating-button review-rating-button-${rating.toLowerCase()}${selected ? ' is-selected' : ''}`}
      disabled={submitted || loading}
      onClick={onClick}
      type="button"
    >
      {loading ? <Loader2 aria-hidden="true" className="review-rating-icon" /> : <Icon aria-hidden="true" className="review-rating-icon" />}
      <span className="review-rating-copy">
        <span className="review-rating-label">{label}</span>
        <small>{intervalLabel}</small>
      </span>
    </button>
  );
}

function formatDueLabel(
  dueAt: string,
  intervalDays: number,
  resources: LocaleResources['reviewCenter'],
) {
  if (intervalDays <= 0) {
    const minutes = Math.max(1, Math.round((new Date(dueAt).getTime() - Date.now()) / 60_000));
    return Number.isFinite(minutes) && minutes < 24 * 60
      ? resources.minutesLater(minutes)
      : resources.reviewLater;
  }
  if (intervalDays === 1) {
    return resources.reviewTomorrow;
  }
  return resources.reviewInDays(intervalDays);
}

function formatDateTime(value: string, locale: SupportedLocale) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString(locale, { hour12: false });
}

function reviewProblemTitle(
  context: ReviewCardContext | undefined,
  current: ReviewCard | undefined,
  locale: SupportedLocale,
  fallback: string,
) {
  return context?.problem.title
    || (locale === 'zh-CN' ? current?.problemTitle : undefined)
    || current?.problemSlug
    || fallback;
}
