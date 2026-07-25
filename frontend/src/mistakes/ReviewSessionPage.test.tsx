import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import {
  ApiRequestError,
  getProblemNote,
  getReviewCardContext,
  getReviewQueue,
  submitReviewAttempt,
  upsertProblemNote,
} from '../services/api';
import type {
  ApiResponse,
  ReviewAttempt,
  ReviewCard,
  ReviewCardContext,
  UserProblemNote,
} from '../types/api';
import { emptyProblemSolutionOutline } from '../problem-notes/problemNoteOptions';
import ReviewSessionPage from './ReviewSessionPage';

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api');
  return {
    ...actual,
    getProblemNote: vi.fn(),
    getReviewCardContext: vi.fn(),
    getReviewQueue: vi.fn(),
    submitReviewAttempt: vi.fn(),
    upsertProblemNote: vi.fn(),
  };
});

beforeEach(() => {
  vi.mocked(getReviewQueue).mockResolvedValue(apiResponse({ items: [reviewCard()], dueCount: 1 }));
  vi.mocked(getReviewCardContext).mockResolvedValue(apiResponse(reviewContext()));
  vi.mocked(getProblemNote).mockResolvedValue(apiResponse(problemNote()));
  vi.mocked(upsertProblemNote).mockResolvedValue(apiResponse(problemNote({ revision: 2 })));
  vi.mocked(submitReviewAttempt).mockResolvedValue(apiResponse(reviewAttempt()));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
  vi.restoreAllMocks();
});

describe('ReviewSessionPage', () => {
  it('shows the complete statement and all ratings without requiring note input', async () => {
    render(<ReviewSessionPage onNavigate={vi.fn()} />);

    expect(await screen.findByRole('heading', { name: '完整题面' })).toBeInTheDocument();
    expect(screen.getByText('给定整数数组和目标值，返回两个数的下标。')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /^重来，/ })).toBeEnabled();
    expect(screen.getByRole('button', { name: /^困难，/ })).toBeEnabled();
    expect(screen.getByRole('button', { name: /^良好，/ })).toBeEnabled();
    expect(screen.getByRole('button', { name: /^简单，/ })).toBeEnabled();

    const noteDisclosure = await screen.findByRole('button', { name: /我的题目笔记/ });
    expect(noteDisclosure).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByText('这段私有笔记不能出现在折叠标题中。')).not.toBeInTheDocument();
  });

  it('keeps unsaved note edits while collapsed and guards navigation', async () => {
    const onNavigate = vi.fn();
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false);
    render(<ReviewSessionPage onNavigate={onNavigate} />);

    const noteDisclosure = await screen.findByRole('button', { name: /我的题目笔记/ });
    fireEvent.click(noteDisclosure);
    const freeNoteDisclosure = await screen.findByRole('button', { name: /自由笔记/ });
    expect(freeNoteDisclosure).toHaveAttribute('aria-expanded', 'false');
    expect(screen.queryByLabelText('自由笔记内容')).not.toBeInTheDocument();
    fireEvent.click(freeNoteDisclosure);
    const noteField = await screen.findByLabelText('自由笔记内容');
    fireEvent.change(noteField, { target: { value: '尚未保存的新内容' } });
    await waitFor(() => expect(noteDisclosure).toHaveTextContent('有未保存修改'));

    fireEvent.click(noteDisclosure);
    expect(screen.queryByLabelText('自由笔记内容')).not.toBeInTheDocument();
    fireEvent.click(noteDisclosure);
    expect(screen.getByLabelText('自由笔记内容')).toHaveValue('尚未保存的新内容');

    fireEvent.click(screen.getByRole('button', { name: '返回复习中心' }));
    expect(confirmSpy).toHaveBeenCalledTimes(1);
    expect(onNavigate).not.toHaveBeenCalled();

    confirmSpy.mockReturnValue(true);
    fireEvent.click(screen.getByRole('button', { name: '返回复习中心' }));
    expect(onNavigate).toHaveBeenCalledWith('/mistakes');
  });

  it('shows a revision conflict and preserves the local draft', async () => {
    vi.mocked(upsertProblemNote).mockRejectedValue(new ApiRequestError(
      409,
      'revision conflict',
      'PROBLEM_NOTE_REVISION_CONFLICT',
    ));
    render(<ReviewSessionPage onNavigate={vi.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: /我的题目笔记/ }));
    fireEvent.click(await screen.findByRole('button', { name: /自由笔记/ }));
    const noteField = await screen.findByLabelText('自由笔记内容');
    fireEvent.change(noteField, { target: { value: '本地冲突草稿' } });
    fireEvent.click(screen.getByRole('button', { name: '保存笔记' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('笔记已在其他页面更新，请重新加载后再编辑。');
    expect(screen.getByLabelText('自由笔记内容')).toHaveValue('本地冲突草稿');
    expect(screen.getByRole('button', { name: '重新加载' })).toBeInTheDocument();
  });

  it('shows category notes after selection and includes them in the saved outline', async () => {
    render(<ReviewSessionPage onNavigate={vi.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: /我的题目笔记/ }));
    expect(await screen.findByLabelText('数据结构说明')).toBeInTheDocument();
    expect(screen.queryByLabelText('算法说明')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('checkbox', { name: '双指针' }));
    fireEvent.change(screen.getByLabelText('算法说明'), {
      target: { value: '左右指针向中间收缩。' },
    });
    fireEvent.change(screen.getByLabelText('数据结构说明'), {
      target: { value: '哈希表保存已经访问的元素。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '保存笔记' }));

    await waitFor(() => expect(upsertProblemNote).toHaveBeenCalledWith(
      'two-sum',
      expect.objectContaining({
        outline: expect.objectContaining({
          algorithms: ['TWO_POINTERS'],
          algorithmNotes: '左右指针向中间收缩。',
          dataStructureNotes: '哈希表保存已经访问的元素。',
        }),
      }),
    ));
  });

  it('submits one UUID-backed rating while a request is in flight', async () => {
    let resolveAttempt!: (response: ApiResponse<ReviewAttempt>) => void;
    vi.mocked(submitReviewAttempt).mockReturnValue(new Promise((resolve) => {
      resolveAttempt = resolve;
    }));
    render(<ReviewSessionPage onNavigate={vi.fn()} />);

    const goodButton = await screen.findByRole('button', { name: /^良好，/ });
    fireEvent.click(goodButton);
    fireEvent.click(goodButton);
    fireEvent.click(screen.getByRole('button', { name: /^简单，/ }));

    expect(submitReviewAttempt).toHaveBeenCalledTimes(1);
    expect(submitReviewAttempt).toHaveBeenCalledWith(88, expect.any(String), 'GOOD');
    const clientAttemptId = vi.mocked(submitReviewAttempt).mock.calls[0][1];
    expect(clientAttemptId).toMatch(
      /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i,
    );

    resolveAttempt(apiResponse(reviewAttempt()));
    expect(await screen.findByRole('region', { name: '复习确认结果' })).toHaveTextContent('3 天后复习');
    expect(screen.getByRole('button', { name: '下一题' })).toBeEnabled();
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
    fsrsStability: null,
    fsrsDifficulty: null,
    dueAt: '2026-07-24T00:00:00Z',
    lapses: 0,
    lastReviewedAt: null,
    lastRating: null,
    archived: false,
    createdAt: '2026-07-23T00:00:00Z',
    updatedAt: '2026-07-24T00:00:00Z',
    ...overrides,
  };
}

function problemNote(overrides: Partial<UserProblemNote> = {}): UserProblemNote {
  return {
    id: 9,
    problemSlug: 'two-sum',
    outline: {
      ...emptyProblemSolutionOutline(),
      coreIdea: '使用哈希表保存已访问元素。',
      dataStructures: ['HASH_MAP'],
    },
    noteMarkdown: '这段私有笔记不能出现在折叠标题中。',
    revision: 1,
    exists: true,
    hasContent: true,
    createdAt: '2026-07-23T00:00:00Z',
    updatedAt: '2026-07-24T00:00:00Z',
    ...overrides,
  };
}

function reviewContext(): ReviewCardContext {
  return {
    card: reviewCard(),
    problem: {
      slug: 'two-sum',
      titleCn: '两数之和',
      difficulty: 'EASY',
      contentMarkdown: '# 完整题面\n\n给定整数数组和目标值，返回两个数的下标。',
    },
    note: problemNote(),
    recentAttempts: [],
    intervalPreviews: [
      { rating: 'AGAIN', dueAt: '2026-07-24T00:10:00Z', intervalDays: 0 },
      { rating: 'HARD', dueAt: '2026-07-25T00:00:00Z', intervalDays: 1 },
      { rating: 'GOOD', dueAt: '2026-07-27T00:00:00Z', intervalDays: 3 },
      { rating: 'EASY', dueAt: '2026-07-31T00:00:00Z', intervalDays: 7 },
    ],
  };
}

function reviewAttempt(): ReviewAttempt {
  return {
    id: 101,
    reviewCardId: 88,
    clientAttemptId: 'd42b6f40-5535-4fc4-bc07-6004bd758b25',
    rating: 'GOOD',
    schedulingBefore: {
      repetitions: 1,
      intervalDays: 1,
      lapses: 0,
      fsrsState: 'LEARNING',
      fsrsStep: 0,
      dueAt: '2026-07-24T00:00:00Z',
    },
    schedulingAfter: {
      repetitions: 2,
      intervalDays: 3,
      lapses: 0,
      fsrsState: 'REVIEW',
      fsrsStep: null,
      fsrsStability: 3,
      fsrsDifficulty: 5,
      dueAt: '2026-07-27T00:00:00Z',
      lastReviewedAt: '2026-07-24T00:00:00Z',
      lastRating: 'GOOD',
    },
    reviewedAt: '2026-07-24T00:00:00Z',
    duplicate: false,
  };
}
