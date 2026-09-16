import { AlertCircle, ArrowRight, CheckCircle2, Loader2, RefreshCw, Sparkles } from 'lucide-react';
import { useEffect } from 'react';
import { useI18n } from '../i18n/I18nProvider';
import type { LocaleResources } from '../i18n/locales';
import type { ReviewIntervalPreview, ReviewRating } from '../types/api';

/** 四档评级与快捷键的公共顺序。 */
export const REVIEW_RATINGS: ReviewRating[] = ['AGAIN', 'HARD', 'GOOD', 'EASY'];

export function useReviewShortcuts(enabled: boolean, onRate: (rating: ReviewRating) => void) {
  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      const rating = REVIEW_RATINGS[Number(event.key) - 1];
      if (!enabled || !rating || event.repeat || event.ctrlKey || event.metaKey || event.altKey
        || (event.target instanceof HTMLElement && (event.target.isContentEditable || event.target.closest('input, textarea, select, [contenteditable="true"]')))) return;
      event.preventDefault();
      onRate(rating);
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [enabled, onRate]);
}

export default function ReviewRatingBar({ intervals, submittingRating, selectedRating, disabled = false, retryRating, onRate, onNext }: {
  intervals: ReviewIntervalPreview[];
  submittingRating?: ReviewRating;
  selectedRating?: ReviewRating;
  disabled?: boolean;
  retryRating?: ReviewRating;
  onRate: (rating: ReviewRating) => void;
  onNext: () => void;
}) {
  const { resources } = useI18n();
  return <footer className="review-rating-bar" aria-label={resources.reviewCenter.ratingAriaLabel}>
    <div className="review-rating-grid">
      {REVIEW_RATINGS.map((rating, index) => <RatingButton
        key={rating} rating={rating} shortcut={index + 1}
        description={resources.reviewCenter.ratingDescriptions[rating]}
        interval={intervals.find((item) => item.rating === rating)}
        loading={submittingRating === rating} submitted={Boolean(selectedRating)}
        selected={selectedRating === rating}
        disabled={disabled || Boolean(submittingRating) || Boolean(retryRating && retryRating !== rating)}
        onClick={() => onRate(rating)}
      />)}
    </div>
    <button className="secondary-button review-next-button" disabled={!selectedRating} onClick={onNext} type="button">
      <ArrowRight aria-hidden="true" /><span>{resources.reviewCenter.nextProblem}</span>
    </button>
  </footer>;
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
  disabled,
}: {
  description: string;
  interval?: ReviewIntervalPreview;
  loading: boolean;
  onClick: () => void;
  rating: ReviewRating;
  selected: boolean;
  shortcut: number;
  submitted: boolean;
  disabled: boolean;
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
      disabled={disabled || submitted || loading}
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

export function formatDueLabel(
  dueAt: string,
  intervalDays: number,
  resources: LocaleResources['reviewCenter'],
) {
  if (intervalDays <= 0) {
    const minutes = Math.max(1, Math.ceil((new Date(dueAt).getTime() - Date.now()) / 60_000));
    return Number.isFinite(minutes) && minutes < 24 * 60
      ? resources.minutesLater(minutes)
      : resources.reviewLater;
  }
  if (intervalDays === 1) {
    return resources.reviewTomorrow;
  }
  return resources.reviewInDays(intervalDays);
}
