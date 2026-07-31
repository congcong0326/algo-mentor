import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nProvider';
import {
  createOrReusePracticeSession,
  getPracticeSessionReviewDetail,
  getPracticeSessionReviews,
} from '../services/api';
import type {
  ApiResponse,
  LearningPlanDetailResponse,
  PracticeCodeReviewHistoryResponse,
  PracticeCodeReviewSummary,
  PracticeSessionResponse,
} from '../types/api';
import PracticeSubmissionHistoryPage from './PracticeSubmissionHistoryPage';

vi.mock('../services/api', () => ({
  createOrReusePracticeSession: vi.fn(),
  getPracticeSessionReviewDetail: vi.fn(),
  getPracticeSessionReviews: vi.fn(),
  requireApiData: <T,>(response: ApiResponse<T>, fallbackMessage: string): T => {
    if (response.success && response.data !== undefined) return response.data;
    throw new Error(fallbackMessage);
  },
  setApiLocale: vi.fn(),
}));

const reviewOne = reviewSummary(1, 1);
const reviewTwo = reviewSummary(2, 2);
const scrollIntoView = vi.fn();

beforeEach(() => {
  Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
    configurable: true,
    value: scrollIntoView,
  });
  vi.mocked(createOrReusePracticeSession).mockResolvedValue(apiResponse(practiceSession()));
  vi.mocked(getPracticeSessionReviews).mockResolvedValue(apiResponse(reviewHistory()));
  vi.mocked(getPracticeSessionReviewDetail).mockImplementation((_sessionId, reviewId) => Promise.resolve(apiResponse({
    id: reviewId,
    sessionId: 50,
    versionNo: reviewId,
    language: 'java',
    evidence: [],
    contextSummary: '',
    scores: { correctness: 4, complexity: 2, edgeCases: 2, codeQuality: 1, problemFit: 1, total: 90 },
    passed: true,
    deductionReasons: [],
    improvementSuggestions: [],
    reviewMarkdown: `Review ${reviewId}`,
    createdAt: '2026-07-20T12:00:00Z',
  })));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  scrollIntoView.mockReset();
});

describe('PracticeSubmissionHistoryPage deep links', () => {
  it('selects, highlights, scrolls to, and loads the requested review without overriding a manual selection', async () => {
    renderPage({ requestedReviewId: 2, returnProfileAnchor: 'learner-profile-statement-101' });

    const requestedReview = await screen.findByRole('button', { name: /V2/ });
    await waitFor(() => expect(requestedReview).toHaveAttribute('aria-pressed', 'true'));
    await waitFor(() => expect(requestedReview).toHaveClass('learner-profile-source-highlight'));
    expect(await screen.findByText('Review 2')).toBeInTheDocument();
    expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest' });
    expect(getPracticeSessionReviewDetail).toHaveBeenCalledWith(50, 2, expect.any(AbortSignal));

    fireEvent.click(screen.getByRole('button', { name: /V1/ }));
    expect(await screen.findByText('Review 1')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /V1/ })).toHaveAttribute('aria-pressed', 'true');
    expect(getPracticeSessionReviewDetail).toHaveBeenCalledWith(50, 1, expect.any(AbortSignal));
  });

  it('does not request a detail outside the current review history and keeps the profile return action controlled', async () => {
    const onBack = vi.fn();
    renderPage({ onBack, requestedReviewId: 99, returnProfileAnchor: 'learner-profile-statement-101' });

    expect(await screen.findByRole('status')).toHaveTextContent('该提交不可用。');
    expect(getPracticeSessionReviewDetail).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: '返回学习画像' }));
    expect(onBack).toHaveBeenCalledTimes(1);
  });
});

function renderPage({
  onBack = vi.fn(),
  requestedReviewId,
  returnProfileAnchor,
}: {
  onBack?: () => void;
  requestedReviewId?: number;
  returnProfileAnchor?: string;
} = {}) {
  return render(
    <I18nProvider>
      <PracticeSubmissionHistoryPage
        onBack={onBack}
        phaseIndex={1}
        plan={learningPlan()}
        problemSlug="two-sum"
        requestedReviewId={requestedReviewId}
        returnProfileAnchor={returnProfileAnchor}
      />
    </I18nProvider>,
  );
}

function apiResponse<T>(data: T): ApiResponse<T> {
  return { success: true, data, timestamp: '2026-07-20T12:00:00Z' };
}

function learningPlan(): LearningPlanDetailResponse {
  return {
    id: 31,
    phases: [{
      phaseIndex: 1,
      title: 'Phase One',
      problems: [{ slug: 'two-sum', title: 'Two Sum', difficulty: 'EASY' }],
    }],
  } as LearningPlanDetailResponse;
}

function practiceSession(): PracticeSessionResponse {
  return {
    session: { id: 50, progressStatus: 'IN_PROGRESS' },
    problem: { slug: 'two-sum', title: 'Two Sum', difficulty: 'EASY' },
    messages: [],
  } as unknown as PracticeSessionResponse;
}

function reviewHistory(): PracticeCodeReviewHistoryResponse {
  return {
    latestReview: reviewTwo,
    reviews: [reviewTwo, reviewOne],
    completionGate: { canComplete: true, reasonCode: 'PASSED', message: '', passScore: 60 },
  };
}

function reviewSummary(id: number, versionNo: number): PracticeCodeReviewSummary {
  return {
    id,
    versionNo,
    language: 'java',
    totalScore: 90,
    passed: true,
    createdAt: '2026-07-20T12:00:00Z',
  };
}
