import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  archiveReviewCard,
  getProblemNote,
  getReviewCardContext,
  getReviewSummary,
  listReviewCards,
} from '../services/api';
import type {
  ApiResponse,
  ReviewCard,
  ReviewCardContext,
  ReviewCardOverview,
  ReviewCardOverviewPage,
  UserProblemNote,
} from '../types/api';
import { emptyProblemSolutionOutline } from '../problem-notes/problemNoteOptions';
import MistakeNotebookPage from './MistakeNotebookPage';

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api');
  return {
    ...actual,
    archiveReviewCard: vi.fn(),
    getProblemNote: vi.fn(),
    getReviewCardContext: vi.fn(),
    getReviewSummary: vi.fn(),
    listReviewCards: vi.fn(),
  };
});

beforeEach(() => {
  vi.mocked(getReviewSummary).mockResolvedValue(apiResponse({
    dueCount: 1,
    remainingTodayCount: 1,
    nextDueAt: null,
  }));
  vi.mocked(listReviewCards).mockResolvedValue(apiResponse(reviewCardPage()));
  vi.mocked(getReviewCardContext).mockResolvedValue(apiResponse(reviewContext()));
  vi.mocked(getProblemNote).mockResolvedValue(apiResponse(problemNote()));
  vi.mocked(archiveReviewCard).mockResolvedValue(apiResponse(reviewCard({ archived: true })));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('MistakeNotebookPage', () => {
  it('uses the review summary as the primary action count', async () => {
    const onNavigate = vi.fn();
    vi.mocked(getReviewSummary).mockResolvedValue(apiResponse({
      dueCount: 3,
      remainingTodayCount: 3,
      nextDueAt: null,
    }));
    render(<MistakeNotebookPage onNavigate={onNavigate} />);

    const reviewButton = await screen.findByRole('button', { name: '开始今日复习 3 题' });
    fireEvent.click(reviewButton);

    expect(onNavigate).toHaveBeenCalledWith('/mistakes/review');
    expect(screen.getByText('两数之和')).toBeInTheDocument();
    expect(screen.queryByText('详情关闭前不应泄露的笔记内容。')).not.toBeInTheDocument();
  });

  it('shows disabled pagination controls when all cards fit on one page', async () => {
    render(<MistakeNotebookPage onNavigate={vi.fn()} />);

    expect(await screen.findByText('第 1 / 1 页')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '下一页' })).toBeDisabled();
  });

  it('opens complete card context with a collapsed problem note and attempt history', async () => {
    render(<MistakeNotebookPage onNavigate={vi.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '查看复习卡详情 两数之和' }));

    const dialog = await screen.findByRole('dialog', { name: '两数之和' });
    expect(within(dialog).getAllByRole('heading', { name: '两数之和' })).toHaveLength(1);
    expect(within(dialog).getByText('完整题面正文。')).toBeInTheDocument();
    expect(within(dialog).getByText('良好')).toBeInTheDocument();
    expect(within(dialog).getByText('间隔 1 天 → 3 天')).toBeInTheDocument();
    const noteDisclosure = await within(dialog).findByRole('button', { name: /我的题目笔记/ });
    expect(noteDisclosure).toHaveAttribute('aria-expanded', 'false');
    expect(within(dialog).queryByText('详情关闭前不应泄露的笔记内容。')).not.toBeInTheDocument();
  });

  it('archives a review card without deleting its problem note', async () => {
    vi.mocked(listReviewCards)
      .mockResolvedValueOnce(apiResponse(reviewCardPage()))
      .mockResolvedValueOnce(apiResponse(reviewCardPage({
        items: [reviewCardOverview({ card: reviewCard({ archived: true }) })],
        activeCount: 0,
        mistakeCount: 0,
      })));
    render(<MistakeNotebookPage onNavigate={vi.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '移出复习' }));

    await waitFor(() => expect(archiveReviewCard).toHaveBeenCalledWith(88, true));
    expect(await screen.findByRole('button', { name: '恢复复习' })).toBeInTheDocument();
    expect(getProblemNote).not.toHaveBeenCalled();
  });

  it('sends keyword and mistake-only filters through the new card API', async () => {
    render(<MistakeNotebookPage onNavigate={vi.fn()} />);
    await screen.findByText('两数之和');

    fireEvent.change(screen.getByPlaceholderText('搜索题目或笔记'), { target: { value: 'two-sum' } });
    fireEvent.click(screen.getByRole('checkbox', { name: '仅看错题' }));

    await waitFor(() => expect(listReviewCards).toHaveBeenLastCalledWith(
      { keyword: 'two-sum', mistakeOnly: true, page: 1 },
      expect.any(AbortSignal),
    ));
  });

  it('loads 10-card pages and moves to the next page', async () => {
    vi.mocked(listReviewCards).mockImplementation(async (query) => apiResponse(reviewCardPage({
      page: query?.page ?? 1,
      total: 21,
    })));
    render(<MistakeNotebookPage onNavigate={vi.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '下一页' }));

    await waitFor(() => expect(listReviewCards).toHaveBeenLastCalledWith(
      { keyword: '', mistakeOnly: false, page: 2 },
      expect.any(AbortSignal),
    ));
  });
});

function apiResponse<T>(data: T): ApiResponse<T> {
  return { success: true, data, timestamp: '2026-07-24T00:00:00Z' };
}

function reviewCard(overrides: Partial<ReviewCard> = {}): ReviewCard {
  return {
    id: 88,
    problemSlug: 'two-sum',
    problemTitle: '两数之和',
    problemDifficulty: 'EASY',
    source: 'REVIEW_FAILED',
    sourceDetail: {},
    repetitions: 1,
    intervalDays: 1,
    fsrsState: 'LEARNING',
    fsrsStep: 0,
    dueAt: new Date().toISOString(),
    lapses: 1,
    lastReviewedAt: '2026-07-23T00:00:00Z',
    lastRating: 'GOOD',
    archived: false,
    createdAt: '2026-07-22T00:00:00Z',
    updatedAt: '2026-07-23T00:00:00Z',
    ...overrides,
  };
}

function reviewCardOverview(overrides: Partial<ReviewCardOverview> = {}): ReviewCardOverview {
  return {
    card: reviewCard(),
    recentCodeReviews: [],
    ...overrides,
  };
}

function reviewCardPage(overrides: Partial<ReviewCardOverviewPage> = {}): ReviewCardOverviewPage {
  return {
    items: [reviewCardOverview()],
    total: 1,
    activeCount: 1,
    mistakeCount: 1,
    page: 1,
    pageSize: 10,
    ...overrides,
  };
}

function problemNote(): UserProblemNote {
  return {
    id: 9,
    problemSlug: 'two-sum',
    outline: emptyProblemSolutionOutline(),
    noteMarkdown: '详情关闭前不应泄露的笔记内容。',
    revision: 1,
    coachSummaryRevision: 1,
    exists: true,
    hasContent: true,
    createdAt: '2026-07-22T00:00:00Z',
    updatedAt: '2026-07-23T00:00:00Z',
  };
}

function reviewContext(): ReviewCardContext {
  const card = reviewCard();
  return {
    card,
    problem: {
      slug: 'two-sum',
      title: '两数之和',
      difficulty: 'EASY',
      contentMarkdown: '# 两数之和\n\n完整题面正文。',
    },
    note: problemNote(),
    recentAttempts: [{
      id: 101,
      reviewCardId: card.id,
      clientAttemptId: 'd42b6f40-5535-4fc4-bc07-6004bd758b25',
      rating: 'GOOD',
      schedulingBefore: {
        repetitions: 1,
        intervalDays: 1,
        lapses: 0,
        fsrsState: 'LEARNING',
        dueAt: '2026-07-22T00:00:00Z',
      },
      schedulingAfter: {
        repetitions: 2,
        intervalDays: 3,
        lapses: 0,
        fsrsState: 'REVIEW',
        dueAt: '2026-07-25T00:00:00Z',
      },
      reviewedAt: '2026-07-22T00:00:00Z',
      duplicate: false,
    }],
    intervalPreviews: [],
  };
}
