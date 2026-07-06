import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  confirmRecall,
  evaluateRecall,
  getReviewCard,
  getReviewIntervals,
  getReviewProblemStatement,
  getReviewPreference,
  getReviewQueue,
  rateRecall,
} from '../services/api';
import type { ApiResponse, MistakeNote, ReviewCard } from '../types/api';
import ReviewSessionPage from './ReviewSessionPage';

vi.mock('../services/api', () => ({
  confirmRecall: vi.fn(),
  evaluateRecall: vi.fn(),
  getReviewCard: vi.fn(),
  getReviewIntervals: vi.fn(),
  getReviewProblemStatement: vi.fn(),
  getReviewPreference: vi.fn(),
  getReviewQueue: vi.fn(),
  rateRecall: vi.fn(),
  requireApiData: <T,>(response: ApiResponse<T>, message: string) => {
    if (response.data === undefined) {
      throw new Error(message);
    }
    return response.data;
  },
}));

beforeEach(() => {
  vi.mocked(getReviewPreference).mockResolvedValue(apiResponse(reviewPreference(true)));
  vi.mocked(getReviewIntervals).mockResolvedValue(apiResponse(intervalPreviews()));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('ReviewSessionPage', () => {
  it('shows review card front prompts before evaluation', async () => {
    vi.mocked(getReviewQueue).mockResolvedValue(apiResponse({
      items: [mistakeNote()],
      dueCount: 1,
    }));
    vi.mocked(getReviewCard).mockResolvedValue(apiResponse(reviewCard({
      problemStatement: {
        summary: '给定数组和目标值，返回两个数的下标。',
        hasFullContent: true,
      },
      prompts: [
        { key: 'algo_choice', label: '你会用什么算法？为什么？', hint: null },
        { key: 'complexity', label: '时间/空间复杂度', hint: null },
      ],
      scaffold: {
        templateMarkdown: '1. 我的思路是：',
        maxInputChars: 400,
      },
    })));

    render(<ReviewSessionPage onNavigate={vi.fn()} />);

    expect(await screen.findByText(/题面摘要：/)).toBeInTheDocument();
    expect(screen.getByText('给定数组和目标值，返回两个数的下标。')).toBeInTheDocument();
    expect(screen.getByText('你会用什么算法？为什么？')).toBeInTheDocument();
    expect(screen.getByText('时间/空间复杂度')).toBeInTheDocument();
    expect(screen.getByText('1. 我的思路是：')).toBeInTheDocument();
  });

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
    vi.mocked(evaluateRecall).mockResolvedValue(apiResponse({
      evaluationId: 99,
      suggestedRating: 'GOOD',
      hitPoints: [],
      missedPoints: [],
      gapSummary: '',
      aiSuggested: true,
      createdAt: '2026-07-02T00:00:00Z',
      intervals: [
        { rating: 'AGAIN', dueAt: '2026-07-02T00:10:00Z', intervalDays: 0 },
        { rating: 'HARD', dueAt: '2026-07-03T00:00:00Z', intervalDays: 1 },
        { rating: 'GOOD', dueAt: '2026-07-05T00:00:00Z', intervalDays: 3 },
        { rating: 'EASY', dueAt: '2026-07-09T00:00:00Z', intervalDays: 7 },
      ],
    }));
    vi.mocked(confirmRecall).mockResolvedValue(apiResponse({
      rating: 'GOOD',
      suggestedRating: 'GOOD',
      nextDueAt: '2026-07-03T00:00:00Z',
      intervalDays: 1,
      repetitions: 2,
      aiSuggested: true,
    }));

    render(<ReviewSessionPage onNavigate={vi.fn()} />);

    expect(await screen.findByRole('heading', { name: 'Two Sum' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('你的复述'), { target: { value: '用哈希表记录已访问数字。' } });
    fireEvent.click(screen.getByRole('button', { name: '揭示并评估' }));
    expect(await screen.findByText('AI 建议：良好')).toBeInTheDocument();
    fireEvent.click(screen.getByText('查看题面'));

    await waitFor(() => expect(getReviewProblemStatement).toHaveBeenCalledWith(88, undefined));
    expect(await screen.findByText('full')).toBeInTheDocument();
  });

  it('keeps AI suggestion flow when preference is enabled', async () => {
    vi.mocked(getReviewQueue).mockResolvedValue(apiResponse({
      items: [mistakeNote()],
      dueCount: 1,
    }));
    vi.mocked(getReviewCard).mockResolvedValue(apiResponse(reviewCard()));
    vi.mocked(evaluateRecall).mockResolvedValue(apiResponse(evaluationResult()));
    vi.mocked(confirmRecall).mockResolvedValue(apiResponse(confirmResult('GOOD', true, 'GOOD')));

    render(<ReviewSessionPage onNavigate={vi.fn()} />);

    fireEvent.change(await screen.findByLabelText('你的复述'), { target: { value: '用哈希表记录已访问数字。' } });
    fireEvent.click(screen.getByRole('button', { name: '揭示并评估' }));

    expect(await screen.findByText('AI 建议：良好')).toBeInTheDocument();
    expect(screen.getByText('3 天后复习')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /良好/ }));

    await waitFor(() => expect(confirmRecall).toHaveBeenCalledWith(88, 99, 'GOOD'));
    expect(rateRecall).not.toHaveBeenCalled();
  });

  it('shows rating buttons immediately and skips evaluation when AI suggestion is disabled', async () => {
    vi.mocked(getReviewPreference).mockResolvedValue(apiResponse(reviewPreference(false)));
    vi.mocked(getReviewQueue).mockResolvedValue(apiResponse({
      items: [mistakeNote()],
      dueCount: 1,
    }));
    vi.mocked(getReviewCard).mockResolvedValue(apiResponse(reviewCard()));
    vi.mocked(rateRecall).mockResolvedValue(apiResponse(confirmResult('HARD', false, null)));

    render(<ReviewSessionPage onNavigate={vi.fn()} />);

    const againButton = await screen.findByRole('button', { name: /重来/ });
    const hardButton = screen.getByRole('button', { name: /困难/ });
    const goodButton = screen.getByRole('button', { name: /良好/ });
    const easyButton = screen.getByRole('button', { name: /简单/ });
    expect(againButton).toHaveAttribute('aria-keyshortcuts', '1');
    expect(hardButton).toHaveAttribute('aria-keyshortcuts', '2');
    expect(goodButton).toHaveAttribute('aria-keyshortcuts', '3');
    expect(easyButton).toHaveAttribute('aria-keyshortcuts', '4');
    expect(againButton).toHaveTextContent('重来');
    expect(againButton).not.toHaveTextContent('1. 重来');
    expect(hardButton).toHaveTextContent('明天复习');
    expect(screen.queryByLabelText('你的复述')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('本次备注')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '揭示并评估' })).not.toBeInTheDocument();

    fireEvent.click(hardButton);

    await waitFor(() => expect(rateRecall).toHaveBeenCalledWith(88, 'HARD'));
    expect(evaluateRecall).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /下一题/ })).toBeEnabled();
  });

  it('submits direct self rating with number shortcuts when AI suggestion is disabled', async () => {
    vi.mocked(getReviewPreference).mockResolvedValue(apiResponse(reviewPreference(false)));
    vi.mocked(getReviewQueue).mockResolvedValue(apiResponse({
      items: [mistakeNote()],
      dueCount: 1,
    }));
    vi.mocked(getReviewCard).mockResolvedValue(apiResponse(reviewCard()));
    vi.mocked(rateRecall).mockResolvedValue(apiResponse(confirmResult('EASY', false, null)));

    render(<ReviewSessionPage onNavigate={vi.fn()} />);

    expect(await screen.findByRole('button', { name: /简单/ })).toHaveAttribute('aria-keyshortcuts', '4');
    fireEvent.keyDown(window, { key: '4' });

    await waitFor(() => expect(rateRecall).toHaveBeenCalledWith(88, 'EASY'));
    expect(screen.getByRole('button', { name: /下一题/ })).toBeEnabled();
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
    repetitions: 1,
    intervalDays: 1,
    dueAt: '2026-07-02T00:00:00Z',
    lapses: 0,
    archived: false,
    createdAt: '2026-07-01T00:00:00Z',
    updatedAt: '2026-07-02T00:00:00Z',
  };
}

function reviewCard(overrides: Partial<ReviewCard> = {}): ReviewCard {
  return {
    cardVariant: 'RULE_BASED',
    problemRef: {
      slug: 'two-sum',
      titleCn: '两数之和',
      difficulty: 'EASY',
    },
    problemStatement: null,
    contextSummary: '复习上下文',
    prompts: [],
    scaffold: null,
    revealPolicy: 'HIDE_PREVIOUS_CODE_AND_SOLUTION',
    expectedEffort: 'LIGHT',
    userNotePersistent: null,
    recentRecallHistory: [],
    ...overrides,
  };
}

function reviewPreference(aiSuggestionEnabled: boolean) {
  return {
    desiredRetention: 0.9,
    dailyNewLimit: 10,
    dailyLearningLimit: 50,
    dailyReviewLimit: 30,
    aiSuggestionEnabled,
  };
}

function evaluationResult() {
  return {
    evaluationId: 99,
    suggestedRating: 'GOOD' as const,
    hitPoints: [],
    missedPoints: [],
    gapSummary: '',
    aiSuggested: true,
    createdAt: '2026-07-02T00:00:00Z',
    intervals: [
      { rating: 'AGAIN' as const, dueAt: '2026-07-02T00:10:00Z', intervalDays: 0 },
      { rating: 'HARD' as const, dueAt: '2026-07-03T00:00:00Z', intervalDays: 1 },
      { rating: 'GOOD' as const, dueAt: '2026-07-05T00:00:00Z', intervalDays: 3 },
      { rating: 'EASY' as const, dueAt: '2026-07-09T00:00:00Z', intervalDays: 7 },
    ],
  };
}

function intervalPreviews() {
  return [
    { rating: 'AGAIN' as const, dueAt: '2026-07-02T00:10:00Z', intervalDays: 0 },
    { rating: 'HARD' as const, dueAt: '2026-07-03T00:00:00Z', intervalDays: 1 },
    { rating: 'GOOD' as const, dueAt: '2026-07-05T00:00:00Z', intervalDays: 3 },
    { rating: 'EASY' as const, dueAt: '2026-07-09T00:00:00Z', intervalDays: 7 },
  ];
}

function confirmResult(
  rating: 'AGAIN' | 'HARD' | 'GOOD' | 'EASY',
  aiSuggested: boolean,
  suggestedRating: 'AGAIN' | 'HARD' | 'GOOD' | 'EASY' | null,
) {
  return {
    rating,
    suggestedRating,
    nextDueAt: '2026-07-03T00:00:00Z',
    intervalDays: 1,
    repetitions: 2,
    aiSuggested,
  };
}
