import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import {
  archiveMistake,
  getReviewCard,
  getReviewProblemStatement,
  listMistakeNotes,
} from '../services/api';
import type { ApiResponse, MistakeNote, ReviewCard } from '../types/api';
import MistakeNotebookPage from './MistakeNotebookPage';

vi.mock('../services/api', () => ({
  archiveMistake: vi.fn(),
  getReviewCard: vi.fn(),
  getReviewProblemStatement: vi.fn(),
  listMistakeNotes: vi.fn(),
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

describe('MistakeNotebookPage', () => {
  it('shows localized problem titles and opens review card details from the eye button', async () => {
    vi.mocked(listMistakeNotes).mockResolvedValue(apiResponse([mistakeNote()]));
    vi.mocked(getReviewCard).mockResolvedValue(apiResponse(reviewCard()));
    vi.mocked(getReviewProblemStatement).mockResolvedValue(apiResponse({
      slug: 'two-sum',
      titleCn: '两数之和',
      difficulty: 'EASY',
      contentMarkdown: '完整题面：给定整数数组 nums 和目标值 target。',
    }));

    render(<MistakeNotebookPage onNavigate={vi.fn()} />);

    expect(await screen.findByRole('heading', { name: '两数之和' })).toBeInTheDocument();
    expect(screen.getByText(/two-sum/)).toBeInTheDocument();
    expect(screen.queryByPlaceholderText('problem-slug')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '加入复习队列' })).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '查看复习卡详情 两数之和' }));

    expect(screen.queryByText('说明为什么哈希表可以一次扫描找到答案。')).not.toBeInTheDocument();
    expect(await screen.findByText('复习这道题的一次扫描不变量。')).toBeInTheDocument();
    expect(screen.getByText('写出关键不变量')).toBeInTheDocument();
    expect(screen.getByText('总是忘记 complement 要先查再写入。')).toBeInTheDocument();
    expect(screen.getByText('先判断 target - nums[i] 是否已经在 map 中。')).toBeInTheDocument();
    expect(screen.getByText('本次备注：边界是重复数字。')).toBeInTheDocument();

    fireEvent.click(screen.getByText('查看题面'));

    await waitFor(() => expect(getReviewProblemStatement).toHaveBeenCalledWith(88));
    expect(await screen.findByText('完整题面：给定整数数组 nums 和目标值 target。')).toBeInTheDocument();
  });

  it('archives without opening the detail panel', async () => {
    vi.mocked(listMistakeNotes).mockResolvedValue(apiResponse([mistakeNote()]));
    vi.mocked(archiveMistake).mockResolvedValue(apiResponse({ ...mistakeNote(), archived: true }));

    render(<MistakeNotebookPage onNavigate={vi.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '移出复习' }));

    await waitFor(() => expect(archiveMistake).toHaveBeenCalledWith(88, true));
    expect(getReviewCard).not.toHaveBeenCalled();
    expect(await screen.findByRole('button', { name: '恢复复习' })).toBeInTheDocument();
  });

  it.each([
    { archived: false, buttonName: '移出复习', nextArchived: true },
    { archived: true, buttonName: '恢复复习', nextArchived: false },
  ])('shows an action error when $buttonName fails', async ({ archived, buttonName, nextArchived }) => {
    vi.mocked(listMistakeNotes).mockResolvedValue(apiResponse([{ ...mistakeNote(), archived }]));
    vi.mocked(archiveMistake).mockRejectedValue(new Error('更新失败，请稍后再试'));

    render(<MistakeNotebookPage onNavigate={vi.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: buttonName }));

    await waitFor(() => expect(archiveMistake).toHaveBeenCalledWith(88, nextArchived));
    expect(await screen.findByRole('alert')).toHaveTextContent('更新失败，请稍后再试');
  });

  it('shows an empty state when the review card has no recall history', async () => {
    vi.mocked(listMistakeNotes).mockResolvedValue(apiResponse([mistakeNote()]));
    vi.mocked(getReviewCard).mockResolvedValue(apiResponse({
      ...reviewCard(),
      userNotePersistent: null,
      recentRecallHistory: [],
    }));

    render(<MistakeNotebookPage onNavigate={vi.fn()} />);

    fireEvent.click(await screen.findByRole('button', { name: '查看复习卡详情 两数之和' }));

    expect(await screen.findByText('暂无长期备注。')).toBeInTheDocument();
    expect(screen.getByText('暂无历史回答。')).toBeInTheDocument();
  });
});

function apiResponse<T>(data: T): ApiResponse<T> {
  return { success: true, data, timestamp: '2026-07-02T00:00:00Z' };
}

function mistakeNote(): MistakeNote {
  return {
    id: 88,
    problemSlug: 'two-sum',
    problemTitle: '两数之和',
    problemLocale: 'zh-CN',
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
    contextSummary: '复习这道题的一次扫描不变量。',
    prompts: [{
      key: 'invariant',
      label: '写出关键不变量',
      hint: '解释 map 中保存的内容。',
    }],
    scaffold: {
      templateMarkdown: '- 不变量：\n- 边界：',
      maxInputChars: 400,
    },
    revealPolicy: 'HIDE_PREVIOUS_CODE_AND_SOLUTION',
    expectedEffort: 'LIGHT',
    userNotePersistent: '总是忘记 complement 要先查再写入。',
    recentRecallHistory: [{
      id: 101,
      grade: 'MASTERED',
      userRecallText: '先判断 target - nums[i] 是否已经在 map 中。',
      userNoteTransient: '边界是重复数字。',
      reviewedAt: '2026-07-02T08:00:00Z',
      intervalAfter: 3,
    }],
  };
}
