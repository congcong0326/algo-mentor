import type { LocaleResources } from '../i18n/locales';
import { useEffect, useRef, useState } from 'react';
import type { PracticeCodeReviewSummary } from '../types/api';
import ReviewScoreBadge from './ReviewScoreBadge';

interface ReviewVersionListProps {
  reviews: PracticeCodeReviewSummary[];
  selectedReviewId?: number;
  onSelect: (review: PracticeCodeReviewSummary) => void;
  passScore?: number;
  requestedReviewId?: number;
  resources: LocaleResources;
  sourceHighlighted?: boolean;
}

export default function ReviewVersionList({
  onSelect,
  passScore,
  requestedReviewId,
  resources,
  reviews,
  selectedReviewId,
  sourceHighlighted = false,
}: ReviewVersionListProps) {
  const requestedReviewRef = useRef<HTMLButtonElement>(null);
  const [showSourceHighlight, setShowSourceHighlight] = useState(false);

  useEffect(() => {
    if (requestedReviewId === undefined || selectedReviewId !== requestedReviewId || !requestedReviewRef.current) {
      setShowSourceHighlight(false);
      return undefined;
    }
    requestedReviewRef.current.scrollIntoView({ block: 'nearest' });
    if (!sourceHighlighted) {
      return undefined;
    }
    setShowSourceHighlight(true);
    const timer = window.setTimeout(() => setShowSourceHighlight(false), 2400);
    return () => window.clearTimeout(timer);
  }, [requestedReviewId, selectedReviewId, sourceHighlighted]);

  return (
    <div className="review-version-list" role="list">
      {reviews.map((review) => (
        <button
          aria-pressed={review.id === selectedReviewId}
          className={`review-version-button${showSourceHighlight && review.id === requestedReviewId ? ' learner-profile-source-highlight' : ''}`}
          key={review.id}
          onClick={() => onSelect(review)}
          ref={review.id === requestedReviewId ? requestedReviewRef : undefined}
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
