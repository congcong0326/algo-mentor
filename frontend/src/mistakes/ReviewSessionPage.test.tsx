import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  getReviewCard,
  getReviewProblemStatement,
  getReviewQueue,
  submitRecall,
} from '../services/api';
import type { ApiResponse, MistakeNote, ReviewCard } from '../types/api';
import ReviewSessionPage from './ReviewSessionPage';

vi.mock('../services/api', () => ({
  getReviewCard: vi.fn(),
  getReviewProblemStatement: vi.fn(),
  getReviewQueue: vi.fn(),
  submitRecall: vi.fn(),
  requireApiData: <T,>(response: ApiResponse<T>, message: string) => {
    if (response.data === undefined) {
      throw new Error(message);
    }
    return response.data;
  },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('ReviewSessionPage', () => {
  it('prefers queue item problemTitle over card title', async () => {
    vi.mocked(getReviewQueue).mockResolvedValue(apiResponse({
      items: [mistakeNote()],
      dueCount: 1,
    }));
    vi.mocked(getReviewCard).mockResolvedValue(apiResponse(reviewCard()));
    vi.mocked(getReviewProblemStatement).mockResolvedValue(apiResponse({
      slug: 'two-sum',
      titleCn: '两数之和',
      difficulty: 'EASY',
      contentMarkdown: 'full',
    }));
    vi.mocked(submitRecall).mockResolvedValue(apiResponse({
      grade: 'MASTERED',
      hitPoints: [],
      missedPoints: [],
      gapSummary: '',
      nextDueAt: '2026-07-03T00:00:00Z',
      masteryState: 'MASTERED',
      intervalDays: 1,
      repetitions: 2,
    }));

    render(<ReviewSessionPage onNavigate={vi.fn()} />);

    expect(await screen.findByRole('heading', { name: 'Two Sum' })).toBeInTheDocument();
    fireEvent.click(screen.getByText('查看题面'));

    await waitFor(() => expect(getReviewProblemStatement).toHaveBeenCalledWith(88, undefined));
    expect(await screen.findByText('full')).toBeInTheDocument();
  });
});

function apiResponse<T>(data: T): ApiResponse<T> {
  return { success: true, data, timestamp: '2026-07-02T00:00:00Z' };
}

function mistakeNote(): MistakeNote {
  return {
    id: 88,
    problemSlug: 'two-sum',
    problemTitle: 'Two Sum',
    problemLocale: 'en-US',
    problemDifficulty: 'EASY',
    source: 'REVIEW_FAILED',
    sourceDetail: {},
    masteryState: 'LEARNING',
    repetitions: 1,
    easeFactor: 2.5,
    intervalDays: 1,
    dueAt: '2026-07-02T00:00:00Z',
    lapses: 0,
    archived: false,
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-02T00:00:00Z',
  };
}

function reviewCard(): ReviewCard {
  return {
    cardVariant: 'RULE_BASED',
    problemRef: {
      slug: 'two-sum',
      titleCn: '两数之和',
      difficulty: 'EASY',
    },
    contextSummary: '复习上下文',
    prompts: [],
    scaffold: null,
    revealPolicy: 'HIDE_PREVIOUS_CODE_AND_SOLUTION',
    expectedEffort: 'LIGHT',
    userNotePersistent: null,
    recentRecallHistory: [],
  };
}
