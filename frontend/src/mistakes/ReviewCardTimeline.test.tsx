import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nProvider';
import type { ReviewCardCodeReviewIndexEntry } from '../types/api';
import ReviewCardTimeline from './ReviewCardTimeline';
import { reviewCardTimelineScoreBand } from './reviewCardTimeline';

afterEach(cleanup);

describe('ReviewCardTimeline', () => {
  it('uses the fixed score boundaries', () => {
    expect(reviewCardTimelineScoreBand(8)).toBe('success');
    expect(reviewCardTimelineScoreBand(6)).toBe('warning');
    expect(reviewCardTimelineScoreBand(5.9)).toBe('danger');
    expect(reviewCardTimelineScoreBand(Number.NaN)).toBe('neutral');
  });

  it('orders points from oldest to newest, exposes a focused summary, and opens the selected review', () => {
    const onOpenReview = vi.fn();
    renderTimeline({
      reviews: [review(3, 5.9, '2026-08-03T12:00:00Z'), review(2, 6, '2026-08-02T12:00:00Z'), review(1, 8, '2026-08-01T12:00:00Z')],
      onOpenReview,
    });

    const points = screen.getAllByRole('button');
    expect(points.map((point) => point.getAttribute('aria-label'))).toEqual([
      expect.stringContaining('V1'),
      expect.stringContaining('V2'),
      expect.stringContaining('V3'),
    ]);
    expect(points[0]).toHaveClass('review-card-timeline-point-success');
    expect(points[1]).toHaveClass('review-card-timeline-point-warning');
    expect(points[2]).toHaveClass('review-card-timeline-point-danger');

    fireEvent.focus(points[1]);
    expect(screen.getByRole('tooltip')).toHaveTextContent('V2 · 6.0 / 10');
    expect(screen.getByRole('tooltip')).toHaveTextContent('未发现明显问题');
    fireEvent.click(points[1]);
    expect(onOpenReview).toHaveBeenCalledWith(expect.objectContaining({ reviewId: 2 }));
  });

  it('shows a focusable manual marker without navigation', () => {
    const onOpenReview = vi.fn();
    renderTimeline({ reviews: [], source: 'USER_MARKED', onOpenReview });

    const marker = screen.getByLabelText('手动标记，尚无代码 Review');
    fireEvent.focus(marker);
    expect(screen.getByRole('tooltip')).toHaveTextContent('该题由手动标记加入复习中心');
    fireEvent.click(marker);
    expect(onOpenReview).not.toHaveBeenCalled();
  });

  it('assigns unique tooltip IDs to markers on different cards', () => {
    render(
      <I18nProvider>
        <ReviewCardTimeline
          locale="zh-CN"
          onOpenReview={vi.fn()}
          resources={reviewResources()}
          reviews={[]}
          source="USER_MARKED"
        />
        <ReviewCardTimeline
          locale="zh-CN"
          onOpenReview={vi.fn()}
          resources={reviewResources()}
          reviews={[]}
          source="USER_MARKED"
        />
      </I18nProvider>,
    );

    screen.getAllByLabelText('手动标记，尚无代码 Review').forEach((marker) => fireEvent.focus(marker));

    const tooltipIds = screen.getAllByRole('tooltip').map((tooltip) => tooltip.id);
    expect(new Set(tooltipIds).size).toBe(2);
  });
});

function renderTimeline({
  reviews,
  source = 'REVIEW_FAILED',
  onOpenReview,
}: {
  reviews: ReviewCardCodeReviewIndexEntry[];
  source?: 'REVIEW_FAILED' | 'REVIEW_PASSED' | 'USER_MARKED';
  onOpenReview: (entry: ReviewCardCodeReviewIndexEntry) => void;
}) {
  return render(
    <I18nProvider>
      <ReviewCardTimeline
        locale="zh-CN"
        onOpenReview={onOpenReview}
        resources={reviewResources()}
        reviews={reviews}
        source={source}
      />
    </I18nProvider>,
  );
}

function reviewResources() {
  return {
    codeReviewTimelineAriaLabel: '代码 Review 时间线',
    codeReviewTimelinePointAriaLabel: (version: number, score: string, language: string, time: string) => `查看代码 Review V${version}，${score} / 10，${language}，${time}`,
    codeReviewTimelineScore: (version: number, score: string) => `V${version} · ${score} / 10`,
    codeReviewTimelineFeedback: (feedback: string) => `主要反馈：${feedback}`,
    codeReviewTimelineFallbackFeedback: '未发现明显问题',
    codeReviewTimelineManualAriaLabel: '手动标记，尚无代码 Review',
    codeReviewTimelineManualTitle: '尚无代码 Review',
    codeReviewTimelineManualDescription: '该题由手动标记加入复习中心，尚未产生代码 Review',
    codeReviewTimelineUnavailable: 'Review 记录暂不可用',
  } as unknown as import('../i18n/locales').LocaleResources['reviewCenter'];
}

function review(reviewId: number, totalScore: number, createdAt: string): ReviewCardCodeReviewIndexEntry {
  return {
    reviewId,
    planId: 9,
    phaseIndex: 1,
    problemSlug: 'two-sum',
    practiceSessionId: 5,
    versionNo: reviewId,
    language: 'java',
    contentLocale: 'zh-CN',
    totalScore,
    passed: totalScore >= 6,
    primaryFeedback: null,
    createdAt,
  };
}
