import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import LearningPlanCreatePage from './LearningPlanCreatePage';
import {
  confirmLearningPlanDraft,
  createLearningPlanDraftFromTemplate,
  getLearningPlanTemplate,
  getLearningPlanTemplates,
  sendLearningPlanDraftMessage,
  streamLearningPlanDraft,
  streamLearningPlanDraftRevision,
} from '../services/api';
import type {
  ApiResponse,
  LearningPlanDraftResponse,
  LearningPlanTemplateDetailResponse,
  LearningPlanTemplateSummaryResponse,
} from '../types/api';

vi.mock('../services/api', () => ({
  confirmLearningPlanDraft: vi.fn(),
  createLearningPlanDraftFromTemplate: vi.fn(),
  getLearningPlanTemplate: vi.fn(),
  getLearningPlanTemplates: vi.fn(),
  requireApiData: <T,>(response: ApiResponse<T>, fallbackMessage: string): T => {
    if (response.success && response.data !== undefined) {
      return response.data;
    }
    throw new Error(response.error?.message ?? fallbackMessage);
  },
  sendLearningPlanDraftMessage: vi.fn(),
  streamLearningPlanDraft: vi.fn(),
  streamLearningPlanDraftRevision: vi.fn(),
}));

const getLearningPlanTemplatesMock = vi.mocked(getLearningPlanTemplates);
const getLearningPlanTemplateMock = vi.mocked(getLearningPlanTemplate);
const createLearningPlanDraftFromTemplateMock = vi.mocked(createLearningPlanDraftFromTemplate);
const streamLearningPlanDraftMock = vi.mocked(streamLearningPlanDraft);
const streamLearningPlanDraftRevisionMock = vi.mocked(streamLearningPlanDraftRevision);
const sendLearningPlanDraftMessageMock = vi.mocked(sendLearningPlanDraftMessage);
const confirmLearningPlanDraftMock = vi.mocked(confirmLearningPlanDraft);

beforeEach(() => {
  getLearningPlanTemplatesMock.mockResolvedValue(apiResponse(templateSummaries()));
  getLearningPlanTemplateMock.mockImplementation((templateId) => (
    Promise.resolve(apiResponse(templateDetail({ templateId })))
  ));
  createLearningPlanDraftFromTemplateMock.mockResolvedValue(apiResponse(generatedDraft()));
  streamLearningPlanDraftMock.mockImplementation(async (_request, options) => {
    options.onEvent({
      eventName: 'draft_ready',
      data: {
        draftId: 100,
        status: 'COLLECTING',
        assistantMessage: '请补充目标主题。',
        missingFields: ['topicPreferences'],
        draftPlan: null,
      },
    });
  });
  streamLearningPlanDraftRevisionMock.mockImplementation(async (_draftId, _request, options) => {
    options.onEvent({
      eventName: 'draft_revision_ready',
      data: {
        proposalGroupId: 1,
        proposalId: 2,
        draftId: 101,
        revisionNo: 1,
        status: 'READY',
        supersededProposalIds: [],
        draft: generatedDraft({
          assistantMessage: '已按要求调整训练方案。',
          draftPlan: learningPlanDraftPlan({
            title: '三周动态规划面试计划',
            goal: '三周内集中突破动态规划面试题',
            phases: [{
              ...learningPlanDraftPlan().phases[0],
              title: '动态规划基础强化',
              focus: '动态规划',
              recommendedTags: ['Dynamic Programming'],
              problems: [{
                ...learningPlanDraftPlan().phases[0].problems[0],
                slug: 'climbing-stairs',
                frontendId: 70,
                title: 'Climbing Stairs',
                titleCn: '爬楼梯',
                tags: ['Dynamic Programming'],
                reason: '建立状态转移手感。',
              }],
            }],
            weeklyBuckets: [{
              weekIndex: 1,
              title: '动态规划基础强化',
              plannedProblemCount: 1,
              plannedLoadPoints: 2.5,
              problemSlugs: ['climbing-stairs'],
              reviewAdvice: '复盘状态转移方程。',
            }],
          }),
        }),
      },
    });
  });
  sendLearningPlanDraftMessageMock.mockResolvedValue(apiResponse(generatedDraft({ draftId: 100 })));
  confirmLearningPlanDraftMock.mockResolvedValue(apiResponse({
    planId: 900,
    title: '四周 Java 算法面试冲刺计划',
    status: 'ACTIVE',
  }));
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('LearningPlanCreatePage', () => {
  it('keeps the AI questionnaire as the default creation path', async () => {
    render(<LearningPlanCreatePage onBackToPlans={vi.fn()} onSaved={vi.fn()} />);

    expect(screen.getByRole('heading', { name: '新建方案' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '返回方案页' })).toHaveClass('icon-button', 'learning-create-back');
    expect(screen.getByRole('button', { name: 'AI 个性化生成' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'AI 个性化生成' }).querySelector('svg')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '从模板创建' }).querySelector('svg')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '动态规划' }));
    fireEvent.click(screen.getByRole('button', { name: '生成训练方案' }));

    await screen.findByText('请补充目标主题。');
    expect(streamLearningPlanDraftMock).toHaveBeenCalledWith(
      expect.objectContaining({
        intent: 'INTERVIEW_SPRINT',
        topicPreferences: ['Dynamic Programming'],
      }),
      expect.objectContaining({ onEvent: expect.any(Function) }),
    );
    expect(getLearningPlanTemplatesMock).not.toHaveBeenCalled();
  });

  it('creates a generated draft from a selected template and can revise it from preview', async () => {
    render(<LearningPlanCreatePage onBackToPlans={vi.fn()} onSaved={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: '从模板创建' }));

    expect(await screen.findByText('Blind 75')).toBeInTheDocument();
    expect(screen.getByText('NeetCode 150')).toBeInTheDocument();
    expect(await screen.findByText('缺失的 6 道题不会进入草稿推荐题。')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByRole('combobox', { name: '编程语言' })).toHaveValue('Java'));
    expect(screen.queryByRole('spinbutton', { name: '训练周期' })).not.toBeInTheDocument();
    expect(screen.queryByRole('spinbutton', { name: '每周投入' })).not.toBeInTheDocument();

    expect(screen.getByText('标准方案')).toBeInTheDocument();
    expect(screen.getByText('每天 4 题 · 每周 5 天 · 推荐 4 周')).toBeInTheDocument();
    expect(screen.getByText('你当前选择：每天 4 题 · 每周 5 天，完成全部 69 题大约需要 4 周')).toBeInTheDocument();
    fireEvent.change(screen.getByRole('spinbutton', { name: '每天题目数' }), { target: { value: '3' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: '每周训练天数' }), { target: { value: '4' } });
    expect(screen.getByText('你当前选择：每天 3 题 · 每周 4 天，完成全部 69 题大约需要 6 周')).toBeInTheDocument();
    fireEvent.change(screen.getByRole('combobox', { name: '编程语言' }), { target: { value: 'Python3' } });
    fireEvent.click(screen.getByRole('button', { name: '按模板生成草案' }));

    await screen.findByRole('heading', { name: '训练方案' });
    expect(createLearningPlanDraftFromTemplateMock).toHaveBeenCalledWith({
      templateId: 'neetcode_blind_75_interview_core',
      dailyProblemCount: 3,
      trainingDaysPerWeek: 4,
      programmingLanguage: 'Python3',
    });
    expect(screen.getByRole('heading', { name: '基础题型恢复' })).toBeInTheDocument();
    expect(screen.getAllByText('两数之和').length).toBeGreaterThan(0);

    fireEvent.change(screen.getByRole('textbox', { name: '对当前计划不满意？输入调整要求' }), {
      target: { value: '三周内集中突破动态规划面试题' },
    });
    fireEvent.click(screen.getByRole('button', { name: '按要求调整计划' }));

    await waitFor(() => expect(streamLearningPlanDraftRevisionMock).toHaveBeenCalledWith(
      101,
      { instruction: '三周内集中突破动态规划面试题' },
      expect.objectContaining({ onEvent: expect.any(Function) }),
    ));
    expect(await screen.findByText('动态规划基础强化')).toBeInTheDocument();
    expect(screen.getAllByText('爬楼梯').length).toBeGreaterThan(0);
  });

  it('submits custom rhythm settings for template drafts', async () => {
    render(<LearningPlanCreatePage onBackToPlans={vi.fn()} onSaved={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: '从模板创建' }));
    await screen.findByText('Blind 75');

    fireEvent.change(screen.getByRole('spinbutton', { name: '每天题目数' }), { target: { value: '5' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: '每周训练天数' }), { target: { value: '6' } });
    fireEvent.click(screen.getByRole('button', { name: '按模板生成草案' }));

    await waitFor(() => expect(createLearningPlanDraftFromTemplateMock).toHaveBeenCalledWith({
      templateId: 'neetcode_blind_75_interview_core',
      dailyProblemCount: 5,
      trainingDaysPerWeek: 6,
      programmingLanguage: 'Java',
    }));
  });

  it('shows the standard rhythm anchor for NeetCode 150 template creation', async () => {
    render(<LearningPlanCreatePage onBackToPlans={vi.fn()} onSaved={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: '从模板创建' }));
    fireEvent.click(await screen.findByRole('button', { name: /NeetCode 150/ }));

    expect(await screen.findByText('每天 3 题 · 每周 5 天 · 推荐 12 周')).toBeInTheDocument();
    expect(screen.getByText(
      '按 150 题 / 推荐 12 周 / 每周 5 天反算，适合作为稳定推进的起点；周内训练，周末留给复盘或缓冲。',
    )).toBeInTheDocument();
  });
});

function apiResponse<T>(data: T): ApiResponse<T> {
  return {
    success: true,
    data,
    timestamp: '2026-06-22T00:00:00Z',
  };
}

function templateSummaries(): LearningPlanTemplateSummaryResponse[] {
  return [
    {
      templateId: 'neetcode_blind_75_interview_core',
      title: 'Blind 75',
      summary: '面试高频基础模板',
      intent: 'INTERVIEW_SPRINT',
      defaultDurationWeeks: 4,
      level: 'INTERMEDIATE',
      defaultWeeklyHours: 8,
      difficultyPreference: 'MEDIUM',
      interviewOriented: true,
      topicPreferences: ['Array', 'Hash Table'],
      targetAudience: '准备算法面试的学习者',
      difficultyMix: {},
      expectedOutcome: '掌握核心题型',
      sourceName: 'neetcode-gh/leetcode',
      sourceCommit: '9907b7fed441fa55083c0751e208b7197101dbba',
      problemCount: 75,
      matchedProblemCount: 69,
      missingProblemCount: 6,
      defaultLoadSummary: loadSummary(69, 82, 32, 'OVERLOADED'),
      defaultRhythmSettings: rhythmSettings(4, 5, 69),
    },
    {
      templateId: 'neetcode_150_interview_full',
      title: 'NeetCode 150',
      summary: '覆盖更多专题的面试模板',
      intent: 'INTERVIEW_SPRINT',
      defaultDurationWeeks: 12,
      level: 'INTERMEDIATE',
      defaultWeeklyHours: 10,
      difficultyPreference: 'MIXED',
      interviewOriented: true,
      topicPreferences: ['Array', 'Dynamic Programming'],
      targetAudience: '需要系统覆盖题型的学习者',
      difficultyMix: {},
      expectedOutcome: '完成系统面试题型覆盖',
      sourceName: 'neetcode-gh/leetcode',
      sourceCommit: '9907b7fed441fa55083c0751e208b7197101dbba',
      problemCount: 150,
      matchedProblemCount: 150,
      missingProblemCount: 0,
      defaultLoadSummary: loadSummary(150, 180, 120, 'OVERLOADED'),
      defaultRhythmSettings: rhythmSettings(3, 5, 150),
    },
  ];
}

function loadSummary(
  plannedProblemCount: number,
  plannedLoadPoints: number,
  totalCapacityPoints: number,
  intensity: 'RELAXED' | 'RECOMMENDED' | 'TIGHT' | 'OVERLOADED',
) {
  return {
    durationWeeks: 4,
    weeklyHours: 8,
    weeklyCapacityPoints: 8,
    totalCapacityPoints,
    plannedLoadPoints,
    loadRatio: totalCapacityPoints === 0 ? 0 : plannedLoadPoints / totalCapacityPoints,
    plannedProblemCount,
    averageProblemsPerWeek: plannedProblemCount / 4,
    intensity,
    reviewBufferIncluded: false,
    suggestions: ['当前节奏过载，建议延长周期、增加每周投入或减少题量。'],
  };
}

function rhythmSettings(dailyProblemCount: number, trainingDaysPerWeek: number, totalProblemCount: number) {
  return {
    dailyProblemCount,
    trainingDaysPerWeek,
    totalProblemCount,
    completedProblemCount: 0,
    skippedProblemCount: 0,
    remainingProblemCount: totalProblemCount,
    estimatedRemainingWeeks: Math.ceil(totalProblemCount / Math.max(1, dailyProblemCount * trainingDaysPerWeek)),
  };
}

function templateDetail(overrides: Partial<LearningPlanTemplateDetailResponse> = {}): LearningPlanTemplateDetailResponse {
  const summary = templateSummaries().find((template) => template.templateId === overrides.templateId)
    ?? templateSummaries()[0];

  return {
    ...summary,
    goal: '准备 Java 后端算法面试',
    programmingLanguage: 'Java',
    prerequisites: ['基础语法'],
    recommendedFor: ['算法面试'],
    notRecommendedFor: ['零基础'],
    sourceUrl: 'https://github.com/neetcode-gh/leetcode',
    sourceDataPath: '.problemSiteData.json',
    sourceDescription: 'source',
    curationNotes: 'notes',
    licenseNotice: 'MIT metadata only',
    metadata: {},
    phases: templatePhases(),
    ...overrides,
  };
}

function templatePhases(count = 1): LearningPlanTemplateDetailResponse['phases'] {
  return Array.from({ length: count }, (_, index) => ({
    phaseIndex: index + 1,
    title: index === 0 ? '数组与哈希' : `阶段 ${index + 1}`,
    durationWeeks: 1,
    focus: 'Array',
    objectives: ['恢复基础题型手感'],
    recommendedTags: ['Array'],
    acceptanceCriteria: ['能说明哈希表查找边界'],
    reviewAdvice: '整理错误原因。',
    problemRefs: index === 0 ? [{
      phaseIndex: 1,
      sortOrder: 1,
      sourceOrder: 1,
      problemSlug: 'two-sum',
      sourceTitle: 'Two Sum',
      sourceDifficulty: 'Easy',
      pattern: 'Arrays & Hashing',
      sourceUrl: 'https://neetcode.io/problems/two-sum',
      matchedProblem: true,
      metadata: {},
    }] : [],
  }));
}

function generatedDraft(overrides: Partial<LearningPlanDraftResponse> = {}): LearningPlanDraftResponse {
  return {
    draftId: 101,
    status: 'GENERATED',
    assistantMessage: '已根据模板生成学习计划草案。',
    missingFields: [],
    draftPlan: learningPlanDraftPlan(),
    ...overrides,
  };
}

function learningPlanDraftPlan(overrides: Partial<NonNullable<LearningPlanDraftResponse['draftPlan']>> = {}) {
  return {
    title: '四周 Java 算法面试冲刺计划',
    summary: '围绕数组和哈希表建立高频题型能力。',
    intent: 'INTERVIEW_SPRINT',
    goal: '准备 Java 后端算法面试',
    durationWeeks: 4,
    level: 'INTERMEDIATE',
    weeklyHours: 8,
    programmingLanguage: 'Java',
    difficultyPreference: 'MEDIUM',
    interviewOriented: true,
    topicPreferences: ['Array', 'Hash Table'],
    profileSummary: '中级，每周 8 小时。',
    phases: [{
      phaseIndex: 1,
      title: '基础题型恢复',
      durationWeeks: 1,
      focus: '数组和哈希表',
      objectives: ['恢复基础题型手感'],
      recommendedTags: ['Array', 'Hash Table'],
      acceptanceCriteria: ['能说明哈希表查找边界'],
      reviewAdvice: '整理错误原因。',
      problems: [{
        slug: 'two-sum',
        frontendId: 1,
        title: 'Two Sum',
        titleCn: '两数之和',
        difficulty: 'EASY',
        tags: ['Array', 'Hash Table'],
        reason: '恢复哈希表查找。',
        sortOrder: 1,
      }],
    }],
    metadata: {},
    loadSummary: loadSummary(1, 2.5, 32, 'RELAXED'),
    weeklyBuckets: [{
      weekIndex: 1,
      title: '基础题型恢复',
      plannedProblemCount: 1,
      plannedLoadPoints: 2.5,
      problemSlugs: ['two-sum'],
      reviewAdvice: '整理错误原因。',
    }],
    nextTrainingPackage: {
      weekIndex: 1,
      newProblemCount: 1,
      reviewTask: '整理错误原因。',
      estimatedMinutes: 96,
      priorityProblemSlugs: ['two-sum'],
    },
    ...overrides,
  } satisfies NonNullable<LearningPlanDraftResponse['draftPlan']>;
}
