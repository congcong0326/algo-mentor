import { useId, useState, type ReactNode } from 'react';
import type { LocaleResources, SupportedLocale } from '../i18n/locales';
import type { ReviewCardCodeReviewIndexEntry, ReviewCardSource } from '../types/api';
import {
  orderedReviewCardTimelineEntries,
  reviewCardTimelineFeedback,
  reviewCardTimelineScoreBand,
  reviewCardTimelineScoreText,
} from './reviewCardTimeline';

interface ReviewCardTimelineProps {
  locale: SupportedLocale;
  onOpenReview: (entry: ReviewCardCodeReviewIndexEntry) => void;
  reviews: ReviewCardCodeReviewIndexEntry[];
  source: ReviewCardSource;
  resources: LocaleResources['reviewCenter'];
}

export default function ReviewCardTimeline({
  locale,
  onOpenReview,
  reviews,
  source,
  resources,
}: ReviewCardTimelineProps) {
  const tooltipIdPrefix = useId();
  const [activeReviewId, setActiveReviewId] = useState<number>();
  const [manualTooltipVisible, setManualTooltipVisible] = useState(false);
  const [unavailableTooltipVisible, setUnavailableTooltipVisible] = useState(false);
  const entries = orderedReviewCardTimelineEntries(reviews);
  const activeEntry = entries.find((entry) => entry.reviewId === activeReviewId);
  const manualTooltipId = `review-card-timeline-manual-tooltip-${tooltipIdPrefix}`;
  const unavailableTooltipId = `review-card-timeline-unavailable-tooltip-${tooltipIdPrefix}`;
  const reviewTooltipId = (reviewId: number) => `review-card-timeline-tooltip-${tooltipIdPrefix}-${reviewId}`;

  if (entries.length === 0 && source === 'USER_MARKED') {
    return (
      <div aria-label={resources.codeReviewTimelineAriaLabel} className="review-card-timeline review-card-timeline-placeholder">
        <span
          aria-describedby={manualTooltipVisible ? manualTooltipId : undefined}
          aria-label={resources.codeReviewTimelineManualAriaLabel}
          className="review-card-timeline-point review-card-timeline-point-neutral"
          onBlur={() => setManualTooltipVisible(false)}
          onClick={() => setManualTooltipVisible(true)}
          onFocus={() => setManualTooltipVisible(true)}
          onMouseEnter={() => setManualTooltipVisible(true)}
          onMouseLeave={() => setManualTooltipVisible(false)}
          tabIndex={0}
        />
        {manualTooltipVisible && (
          <TimelineTooltip id={manualTooltipId}>
            <strong>{resources.codeReviewTimelineManualTitle}</strong>
            <span>{resources.codeReviewTimelineManualDescription}</span>
          </TimelineTooltip>
        )}
      </div>
    );
  }

  if (entries.length === 0) {
    return (
      <div aria-label={resources.codeReviewTimelineUnavailable} className="review-card-timeline review-card-timeline-placeholder">
        <span
          aria-describedby={unavailableTooltipVisible ? unavailableTooltipId : undefined}
          aria-label={resources.codeReviewTimelineUnavailable}
          className="review-card-timeline-point review-card-timeline-point-neutral"
          onBlur={() => setUnavailableTooltipVisible(false)}
          onClick={() => setUnavailableTooltipVisible(true)}
          onFocus={() => setUnavailableTooltipVisible(true)}
          onMouseEnter={() => setUnavailableTooltipVisible(true)}
          onMouseLeave={() => setUnavailableTooltipVisible(false)}
          tabIndex={0}
        />
        {unavailableTooltipVisible && (
          <TimelineTooltip id={unavailableTooltipId}>
            <span>{resources.codeReviewTimelineUnavailable}</span>
          </TimelineTooltip>
        )}
      </div>
    );
  }

  return (
    <div aria-label={resources.codeReviewTimelineAriaLabel} className="review-card-timeline">
      <div className="review-card-timeline-points">
        {entries.map((entry) => {
          const score = reviewCardTimelineScoreText(entry.totalScore);
          const time = formatTimelineTime(entry.createdAt, locale);
          const language = entry.language || '—';
          return (
            <button
              aria-describedby={activeEntry?.reviewId === entry.reviewId ? reviewTooltipId(entry.reviewId) : undefined}
              aria-label={resources.codeReviewTimelinePointAriaLabel(entry.versionNo, score, language, time)}
              className={`review-card-timeline-point review-card-timeline-point-${reviewCardTimelineScoreBand(entry.totalScore)}`}
              key={entry.reviewId}
              onBlur={() => setActiveReviewId(undefined)}
              onClick={() => onOpenReview(entry)}
              onFocus={() => setActiveReviewId(entry.reviewId)}
              onMouseEnter={() => setActiveReviewId(entry.reviewId)}
              onMouseLeave={() => setActiveReviewId(undefined)}
              type="button"
            />
          );
        })}
      </div>
      {activeEntry && (
        <TimelineTooltip id={reviewTooltipId(activeEntry.reviewId)}>
          <strong>{resources.codeReviewTimelineScore(
            activeEntry.versionNo,
            reviewCardTimelineScoreText(activeEntry.totalScore),
          )}</strong>
          <span>{activeEntry.language || '—'} · {formatTimelineTime(activeEntry.createdAt, locale)}</span>
          <span>{resources.codeReviewTimelineFeedback(reviewCardTimelineFeedback(
            activeEntry.primaryFeedback,
            resources.codeReviewTimelineFallbackFeedback,
          ))}</span>
        </TimelineTooltip>
      )}
    </div>
  );
}

function TimelineTooltip({ children, id }: { children: ReactNode; id: string }) {
  return <span className="review-card-timeline-tooltip" id={id} role="tooltip">{children}</span>;
}

function formatTimelineTime(value: string, locale: SupportedLocale): string {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString(locale, { hour12: false });
}
