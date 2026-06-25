import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nProvider';
import * as api from '../services/api';
import type {
  ApiResponse,
  LearningPlanDetailResponse,
  PracticeCodeReviewDetail,
  PracticeCodeReviewHistoryResponse,
  PracticeSessionResponse,
} from '../types/api';
import PracticeChatWorkbench from './PracticeChatWorkbench';

vi.mock('../services/api', async () => {
  const actual = await vi.importActual<typeof import('../services/api')>('../services/api');
  return {
    ...actual,
    createOrReusePracticeSession: vi.fn(),
    getPracticeSessionActiveRun: vi.fn(),
    getPracticeSessionMessages: vi.fn(),
    getPracticeSessionReviewDetail: vi.fn(),
    getPracticeSessionReviews: vi.fn(),
    streamPracticeMessage: vi.fn(),
    updatePracticeProgressStatus: vi.fn(),
  };
});

const createOrReusePracticeSession = vi.mocked(api.createOrReusePracticeSession);
const getPracticeSessionActiveRun = vi.mocked(api.getPracticeSessionActiveRun);
const getPracticeSessionReviewDetail = vi.mocked(api.getPracticeSessionReviewDetail);
const getPracticeSessionReviews = vi.mocked(api.getPracticeSessionReviews);
const updatePracticeProgressStatus = vi.mocked(api.updatePracticeProgressStatus);

describe('PracticeChatWorkbench review contracts', () => {
  beforeEach(() => {
    createOrReusePracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: {
        canComplete: false,
        reasonCode: 'NO_REVIEW',
        message: '提交一份可评审代码后才能标记完成。',
        latestScore: null,
        passScore: 80,
      },
      latestReview: null,
    })));
    getPracticeSessionActiveRun.mockResolvedValue(apiResponse(null));
    getPracticeSessionReviews.mockResolvedValue(apiResponse(historyFixture({ reviews: [] })));
    getPracticeSessionReviewDetail.mockResolvedValue(apiResponse(reviewDetailFixture()));
    updatePracticeProgressStatus.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '已通过 Review，可以标记完成。',
        latestScore: 92,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
    })));
  });

  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('renders no-review gate and disables completion', async () => {
    renderWorkbench();

    expect(await screen.findByText('提交一份可评审代码后才能标记完成。')).toBeInTheDocument();
    expect(screen.getByText('暂无 Review')).toBeInTheDocument();
    expect(screen.getByText('通过分 80')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '标记完成' })).not.toBeInTheDocument();
    expect(updatePracticeProgressStatus).not.toHaveBeenCalled();
  });

  it('shows review drawer empty state', async () => {
    renderWorkbench();

    fireEvent.click(await screen.findByRole('button', { name: 'Review 记录' }));

    const drawer = await screen.findByRole('complementary', { name: 'Review 记录' });
    expect(within(drawer).getByText('暂无代码 Review')).toBeInTheDocument();
    expect(within(drawer).getByText('提交包含完整代码的练习消息后，系统会在这里展示 Review 版本。')).toBeInTheDocument();
    expect(getPracticeSessionReviews).toHaveBeenCalledWith(101, expect.any(AbortSignal));
  });

  it('shows review versions and loads selected detail', async () => {
    createOrReusePracticeSession.mockResolvedValue(apiResponse(sessionFixture({
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '最新 Review 已通过。',
        latestScore: 92,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
    })));
    getPracticeSessionReviews.mockResolvedValue(apiResponse(historyFixture({
      reviews: [
        reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
        reviewSummaryFixture({ id: 41, versionNo: 1, totalScore: 68, passed: false }),
      ],
    })));
    getPracticeSessionReviewDetail.mockResolvedValueOnce(apiResponse(reviewDetailFixture({
      id: 42,
      versionNo: 2,
      reviewMarkdown: '## 整体评价\n通过了边界条件。',
      submittedCode: 'class Solution { version2(); }',
      passed: true,
      scores: scoreFixture({ total: 92 }),
    }))).mockResolvedValueOnce(apiResponse(reviewDetailFixture({
      id: 41,
      versionNo: 1,
      reviewMarkdown: '## 整体评价\n还需要修复空数组。',
      submittedCode: 'class Solution { version1(); }',
      passed: false,
      scores: scoreFixture({ total: 68 }),
    })));
    renderWorkbench();

    fireEvent.click(await screen.findByRole('button', { name: 'Review 记录' }));

    const drawer = await screen.findByRole('complementary', { name: 'Review 记录' });
    expect(await within(drawer).findByRole('button', { name: /V2/ })).toBeInTheDocument();
    expect(within(drawer).getByRole('button', { name: /V1/ })).toBeInTheDocument();
    expect(await within(drawer).findByText('通过了边界条件。')).toBeInTheDocument();
    expect(within(drawer).getByText('class Solution { version2(); }')).toBeInTheDocument();

    fireEvent.click(within(drawer).getByRole('button', { name: /V1/ }));

    expect(await within(drawer).findByText('还需要修复空数组。')).toBeInTheDocument();
    expect(within(drawer).getByText('class Solution { version1(); }')).toBeInTheDocument();
    expect(getPracticeSessionReviewDetail).toHaveBeenCalledWith(101, 42, expect.any(AbortSignal));
    expect(getPracticeSessionReviewDetail).toHaveBeenCalledWith(101, 41, expect.any(AbortSignal));
  });

  it('clears stale review versions when session changes', async () => {
    createOrReusePracticeSession.mockResolvedValueOnce(apiResponse(sessionFixture({
      completionGate: {
        canComplete: true,
        reasonCode: 'PASSED',
        message: '最新 Review 已通过。',
        latestScore: 92,
        passScore: 80,
      },
      latestReview: reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
    }))).mockResolvedValueOnce(apiResponse(sessionFixture({
      session: {
        id: 202,
        planId: 7,
        phaseIndex: 1,
        problemSlug: 'valid-palindrome',
        progressStatus: 'IN_PROGRESS',
        agentTaskId: 502,
        createdAt: '2026-06-25T00:00:00Z',
        updatedAt: '2026-06-25T00:00:00Z',
      },
      problem: {
        slug: 'valid-palindrome',
        frontendId: 125,
        title: 'Valid Palindrome',
        titleCn: '验证回文串',
        difficulty: 'EASY',
        tags: ['Two Pointers'],
        leetcodeUrl: 'https://leetcode.cn/problems/valid-palindrome/',
      },
      messages: [],
      latestReview: null,
      completionGate: {
        canComplete: false,
        reasonCode: 'NO_REVIEW',
        message: '新题还没有 Review。',
        latestScore: null,
        passScore: 80,
      },
    })));
    getPracticeSessionReviews
      .mockResolvedValueOnce(apiResponse(historyFixture({
        reviews: [
          reviewSummaryFixture({ id: 42, versionNo: 2, totalScore: 92, passed: true }),
        ],
      })))
      .mockImplementationOnce(() => new Promise(() => undefined));

    const { rerender } = renderWorkbench();

    fireEvent.click(await screen.findByRole('button', { name: 'Review 记录' }));
    const drawer = await screen.findByRole('complementary', { name: 'Review 记录' });
    expect(await within(drawer).findByRole('button', { name: /V2/ })).toBeInTheDocument();

    rerender(
      <I18nProvider>
        <PracticeChatWorkbench
          onBack={vi.fn()}
          phaseIndex={1}
          plan={planFixtureWithSecondProblem}
          problemSlug="valid-palindrome"
        />
      </I18nProvider>,
    );

    expect(await screen.findByText('新题还没有 Review。')).toBeInTheDocument();
    expect(within(drawer).queryByRole('button', { name: /V2/ })).not.toBeInTheDocument();
  });
});

function renderWorkbench() {
  return render(
    <I18nProvider>
      <PracticeChatWorkbench
        onBack={vi.fn()}
        phaseIndex={1}
        plan={planFixture}
        problemSlug="two-sum"
      />
    </I18nProvider>,
  );
}

function apiResponse<T>(data: T): ApiResponse<T> {
  return {
    success: true,
    data,
    timestamp: '2026-06-25T00:00:00Z',
  };
}

function sessionFixture(overrides: Partial<PracticeSessionResponse> = {}): PracticeSessionResponse {
  return {
    session: {
      id: 101,
      planId: 7,
      phaseIndex: 1,
      problemSlug: 'two-sum',
      progressStatus: 'IN_PROGRESS',
      agentTaskId: 501,
      createdAt: '2026-06-25T00:00:00Z',
      updatedAt: '2026-06-25T00:00:00Z',
    },
    problem: {
      slug: 'two-sum',
      frontendId: 1,
      title: 'Two Sum',
      titleCn: '两数之和',
      difficulty: 'EASY',
      tags: ['Array', 'Hash Table'],
      leetcodeUrl: 'https://leetcode.cn/problems/two-sum/',
    },
    messages: [
      {
        id: 1,
        role: 'ASSISTANT',
        messageType: 'PROBLEM_STATEMENT',
        contentMarkdown: '给定整数数组 nums 和目标值 target。',
        createdAt: '2026-06-25T00:00:00Z',
      },
    ],
    activeRun: null,
    latestReview: null,
    completionGate: {
      canComplete: false,
      reasonCode: 'NO_REVIEW',
      message: '提交一份可评审代码后才能标记完成。',
      latestScore: null,
      passScore: 80,
    },
    ...overrides,
  };
}

function reviewSummaryFixture(overrides: Partial<PracticeCodeReviewHistoryResponse['reviews'][number]> = {}) {
  return {
    id: 42,
    versionNo: 2,
    language: 'java',
    totalScore: 92,
    passed: true,
    createdAt: '2026-06-25T00:10:00Z',
    ...overrides,
  };
}

function historyFixture(overrides: Partial<PracticeCodeReviewHistoryResponse> = {}): PracticeCodeReviewHistoryResponse {
  return {
    latestReview: null,
    reviews: [],
    completionGate: {
      canComplete: false,
      reasonCode: 'NO_REVIEW',
      message: '提交一份可评审代码后才能标记完成。',
      latestScore: null,
      passScore: 80,
    },
    ...overrides,
  };
}

function reviewDetailFixture(overrides: Partial<PracticeCodeReviewDetail> = {}): PracticeCodeReviewDetail {
  return {
    id: 42,
    sessionId: 101,
    versionNo: 2,
    language: 'java',
    submittedCode: 'class Solution { version2(); }',
    reviewMarkdown: '## 整体评价\n通过了边界条件。',
    passed: true,
    scores: scoreFixture({ total: 92 }),
    evidence: [
      { type: '边界条件', value: '覆盖空数组和重复值。' },
    ],
    deductionReasons: ['变量命名还可以更明确。'],
    improvementSuggestions: ['补充 target 不存在时的说明。'],
    contextSummary: '用户提交了 Java 解法并说明已在 LeetCode 通过。',
    createdAt: '2026-06-25T00:10:00Z',
    ...overrides,
  };
}

function scoreFixture(overrides: Partial<PracticeCodeReviewDetail['scores']> = {}): PracticeCodeReviewDetail['scores'] {
  return {
    correctness: 4,
    complexity: 2,
    edgeCases: 2,
    codeQuality: 1,
    problemFit: 1,
    total: 10,
    ...overrides,
  };
}

const planFixture: LearningPlanDetailResponse = {
  id: 7,
  status: 'ACTIVE',
  createdAt: '2026-06-25T00:00:00Z',
  updatedAt: '2026-06-25T00:00:00Z',
  title: '数组训练',
  summary: '练习数组和哈希表。',
  intent: 'PRACTICE_GOAL',
  goal: '系统训练数组题',
  durationWeeks: 2,
  level: 'BEGINNER',
  weeklyHours: 5,
  programmingLanguage: 'Java',
  difficultyPreference: 'MIXED',
  interviewOriented: false,
  topicPreferences: ['Array'],
  profileSummary: '初学者',
  metadata: {},
  phases: [
    {
      phaseIndex: 1,
      title: '基础阶段',
      durationWeeks: 1,
      focus: '数组基础',
      objectives: ['理解哈希表'],
      recommendedTags: ['Array'],
      acceptanceCriteria: ['完成 Two Sum'],
      reviewAdvice: '复盘边界条件',
      problems: [
        {
          slug: 'two-sum',
          frontendId: 1,
          title: 'Two Sum',
          titleCn: '两数之和',
          difficulty: 'EASY',
          tags: ['Array', 'Hash Table'],
          reason: '基础题',
          sortOrder: 1,
        },
      ],
    },
  ],
};

const planFixtureWithSecondProblem: LearningPlanDetailResponse = {
  ...planFixture,
  phases: [
    {
      ...planFixture.phases[0],
      problems: [
        ...planFixture.phases[0].problems,
        {
          slug: 'valid-palindrome',
          frontendId: 125,
          title: 'Valid Palindrome',
          titleCn: '验证回文串',
          difficulty: 'EASY',
          tags: ['Two Pointers'],
          reason: '双指针基础题',
          sortOrder: 2,
        },
      ],
    },
  ],
};
