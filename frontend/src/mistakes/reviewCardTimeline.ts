import type { ReviewCardCodeReviewIndexEntry } from '../types/api';

export type ReviewCardTimelineScoreBand = 'success' | 'warning' | 'danger' | 'neutral';

const MAX_REVIEW_CARD_TIMELINE_ENTRIES = 10;

export function reviewCardTimelineScoreBand(score: number): ReviewCardTimelineScoreBand {
  if (!Number.isFinite(score)) return 'neutral';
  if (score >= 8) return 'success';
  if (score >= 6) return 'warning';
  return 'danger';
}

export function orderedReviewCardTimelineEntries(
  entries: ReviewCardCodeReviewIndexEntry[],
): ReviewCardCodeReviewIndexEntry[] {
  return [...entries].sort((left, right) => {
    const createdAtDifference = new Date(left.createdAt).getTime() - new Date(right.createdAt).getTime();
    return createdAtDifference || left.reviewId - right.reviewId;
  }).slice(-MAX_REVIEW_CARD_TIMELINE_ENTRIES);
}

export function reviewCardTimelineScoreText(score: number): string {
  return Number.isFinite(score) ? score.toFixed(1) : '--';
}

export function reviewCardTimelineFeedback(feedback: string | null | undefined, fallback: string): string {
  const normalized = feedback?.replace(/\s+/g, ' ').trim();
  return normalized || fallback;
}
