import type { LocaleResources } from '../i18n/locales';
import type { PracticeCodeReviewSummary } from '../types/api';
import ReviewScoreBadge from './ReviewScoreBadge';

interface ReviewVersionListProps {
  reviews: PracticeCodeReviewSummary[];
  selectedReviewId?: number;
  onSelect: (review: PracticeCodeReviewSummary) => void;
  passScore?: number;
  resources: LocaleResources;
}

export default function ReviewVersionList({
  onSelect,
  passScore,
  resources,
  reviews,
  selectedReviewId,
}: ReviewVersionListProps) {
  return (
    <div className="review-version-list" role="list">
      {reviews.map((review) => (
        <button
          aria-pressed={review.id === selectedReviewId}
          className="review-version-button"
          key={review.id}
          onClick={() => onSelect(review)}
          type="button"
        >
          <span className="review-version-title">
            <strong>{resources.learningPlans.reviewVersionLabel(review.versionNo)}</strong>
            <span>{review.language}</span>
          </span>
          <ReviewScoreBadge
            passed={review.passed}
            passScore={passScore}
            resources={resources}
            score={review.totalScore}
          />
        </button>
      ))}
    </div>
  );
}
