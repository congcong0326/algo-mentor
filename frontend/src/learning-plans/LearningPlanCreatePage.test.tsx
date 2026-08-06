import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nProvider';
import LanguageSelector from '../i18n/LanguageSelector';
import LearningPlanCreatePage from './LearningPlanCreatePage';
import {
  confirmLearningPlanDraft,
  createLearningPlanDraftFromTemplate,
  getLearningPlanTemplate,
  getLearningPlanTemplates,
  sendLearningPlanDraftMessage,
  setApiLocale,
  streamLearningPlanDraft,
  streamLearningPlanDraftRevision,
} from '../services/api';
import type {
  ApiResponse,
  LearningPlanDraftResponse,
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
  setApiLocale: vi.fn(),
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
const setApiLocaleMock = vi.mocked(setApiLocale);

beforeEach(() => {
  getLearningPlanTemplatesMock.mockResolvedValue(apiResponse(templateSummaries()));
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
            objective: '三周内集中突破动态规划面试题',
            phases: [{
              ...learningPlanDraftPlan().phases[0],
              title: '动态规划基础强化',
              focus: '动态规划',
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
  it('uses template creation as the default path and submits the default AI request contract', async () => {
    render(<LearningPlanCreatePage onBackToPlans={vi.fn()} onSaved={vi.fn()} />);

    expect(screen.getByRole('heading', { name: '新建方案' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '返回方案页' })).toHaveClass('icon-button', 'learning-create-back');
    expect(screen.getByRole('button', { name: '返回方案页' }).closest('.learning-create-content'))
      .not.toHaveClass('learning-create-content--preview');
    expect(Array.from(document.querySelectorAll('.create-mode-switch > button')).map((button) => button.textContent))
      .toEqual(['从模板创建', 'AI 个性化生成']);
    expect(screen.getByRole('button', { name: '从模板创建' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('button', { name: 'AI 个性化生成' }).querySelector('svg')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '从模板创建' }).querySelector('svg')).toBeInTheDocument();

    await screen.findByText('LeetCode 75');
    expect(getLearningPlanTemplatesMock).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: 'AI 个性化生成' }));
    expect(screen.getByRole('button', { name: 'AI 个性化生成' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('button', { name: '动态规划' }));
    fireEvent.click(screen.getByRole('button', { name: '生成训练方案' }));

    await screen.findByText('请补充目标主题。');
    expect(streamLearningPlanDraftMock).toHaveBeenCalledWith(
      {
        intent: 'INTERVIEW_SPRINT',
        objective: undefined,
        durationWeeks: 4,
        level: 'INTERMEDIATE',
        weeklyHours: 6,
        programmingLanguage: 'Java',
        difficultyDistribution: {
          easyPercent: 25,
          mediumPercent: 55,
          hardPercent: 20,
        },
        topicPreferences: ['Dynamic Programming'],
        additionalConstraints: undefined,
        personalizationEnabled: true,
      },
      expect.objectContaining({ onEvent: expect.any(Function) }),
    );
  });

  it('keeps objective and constraints separate and sends personalizationEnabled false', async () => {
    render(<LearningPlanCreatePage onBackToPlans={vi.fn()} onSaved={vi.fn()} />);

    await screen.findByText('LeetCode 75');
    fireEvent.click(screen.getByRole('button', { name: 'AI 个性化生成' }));
    fireEvent.change(screen.getByRole('textbox', { name: '具体目标（可选）' }), {
      target: { value: '准备 Java 后端算法面试' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: '其他限制（可选）' }), {
      target: { value: '每周留一天复盘' },
    });
    fireEvent.click(screen.getByRole('checkbox', { name: '参考我的学习数据' }));
    fireEvent.click(screen.getByRole('button', { name: '生成训练方案' }));

    await screen.findByText('请补充目标主题。');
    expect(streamLearningPlanDraftMock).toHaveBeenCalledWith(
      expect.objectContaining({
        objective: '准备 Java 后端算法面试',
        additionalConstraints: '每周留一天复盘',
        personalizationEnabled: false,
      }),
      expect.any(Object),
    );
  });

  it('creates a generated draft from a selected template and can revise it from preview', async () => {
    render(<LearningPlanCreatePage onBackToPlans={vi.fn()} onSaved={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: '从模板创建' }));

    expect(await screen.findByText('LeetCode 75')).toBeInTheDocument();
    expect(screen.getByText('NeetCode 150')).toBeInTheDocument();
    expect(getLearningPlanTemplateMock).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getByRole('combobox', { name: '编程语言' })).toHaveValue('Java'));
    const leetCodeTemplateCard = screen.getByRole('button', { name: /LeetCode 75/ });
    expect(leetCodeTemplateCard).toHaveAttribute(
      'aria-describedby',
      'template-preview-leetcode_75_core_sprint',
    );
    expect(document.getElementById('template-preview-leetcode_75_core_sprint')).toHaveTextContent(
      '适合人群准备算法面试的学习者完成目标掌握核心题型',
    );
    expect(screen.queryByRole('region', { name: '当前模板' })).not.toBeInTheDocument();
    expect(document.querySelector('.template-detail-strip')).not.toBeInTheDocument();
    expect(screen.queryByRole('spinbutton', { name: '训练周期' })).not.toBeInTheDocument();
    expect(screen.queryByRole('spinbutton', { name: '每周投入' })).not.toBeInTheDocument();

    expect(screen.getByText('标准方案')).toBeInTheDocument();
    expect(screen.getByText('每天 4 题 · 每周 5 天 · 推荐 4 周')).toBeInTheDocument();
    expect(screen.getByText('你当前选择：每天 4 题 · 每周 5 天，完成全部 75 题大约需要 4 周')).toBeInTheDocument();
    fireEvent.change(screen.getByRole('spinbutton', { name: '每天题目数' }), { target: { value: '3' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: '每周训练天数' }), { target: { value: '4' } });
    expect(screen.getByText('你当前选择：每天 3 题 · 每周 4 天，完成全部 75 题大约需要 7 周')).toBeInTheDocument();
    fireEvent.change(screen.getByRole('combobox', { name: '编程语言' }), { target: { value: 'Python3' } });
    fireEvent.click(screen.getByRole('button', { name: '按模板生成草案' }));

    await screen.findByRole('heading', { name: '训练方案' });
    expect(screen.getByRole('button', { name: '返回方案页' }).closest('.learning-create-content'))
      .toHaveClass('learning-create-content--preview');
    expect(createLearningPlanDraftFromTemplateMock).toHaveBeenCalledWith({
      templateId: 'leetcode_75_core_sprint',
      contentLocale: 'zh-CN',
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
    await screen.findByText('LeetCode 75');

    fireEvent.change(screen.getByRole('spinbutton', { name: '每天题目数' }), { target: { value: '5' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: '每周训练天数' }), { target: { value: '6' } });
    fireEvent.click(screen.getByRole('button', { name: '按模板生成草案' }));

    await waitFor(() => expect(createLearningPlanDraftFromTemplateMock).toHaveBeenCalledWith({
      templateId: 'leetcode_75_core_sprint',
      contentLocale: 'zh-CN',
      dailyProblemCount: 5,
      trainingDaysPerWeek: 6,
      programmingLanguage: 'Java',
    }));
  });

  it('shows the standard rhythm anchor for NeetCode 150 template creation', async () => {
    render(<LearningPlanCreatePage onBackToPlans={vi.fn()} onSaved={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: '从模板创建' }));
    fireEvent.click(await screen.findByRole('button', { name: /NeetCode 150/ }));

    expect(screen.queryByRole('region', { name: '当前模板' })).not.toBeInTheDocument();
    expect(await screen.findByText('每天 3 题 · 每周 5 天 · 推荐 12 周')).toBeInTheDocument();
    expect(screen.getByText(
      '按 150 题 / 推荐 12 周 / 每周 5 天反算，适合作为稳定推进的起点；周内训练，周末留给复盘或缓冲。',
    )).toBeInTheDocument();
  });

  it('filters the catalog with accessible first-level tabs and preserves the recommended default', async () => {
    const { container } = render(<LearningPlanCreatePage onBackToPlans={vi.fn()} onSaved={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: '从模板创建' }));
    await screen.findByText('LeetCode 75');

    expect(screen.getByRole('tab', { name: '推荐' })).toHaveAttribute('aria-selected', 'true');
    expect(container.querySelectorAll('.template-card')).toHaveLength(6);
    expect(container.textContent).not.toContain('来源 commit');
    expect(container.textContent).not.toContain('题目匹配');

    for (const [label, expectedCount] of [
      ['系统学习', 5],
      ['面试备战', 8],
      ['专题突破', 18],
      ['语言与岗位', 4],
    ] as const) {
      fireEvent.click(screen.getByRole('tab', { name: label }));
      await waitFor(() => expect(container.querySelectorAll('.template-card')).toHaveLength(expectedCount));
      expect(screen.getByRole('tab', { name: label })).toHaveAttribute('aria-selected', 'true');
    }

    expect(getLearningPlanTemplateMock).not.toHaveBeenCalled();
  });

  it('reloads localized templates and preserves the selected template across UI locale changes', async () => {
    getLearningPlanTemplatesMock
      .mockResolvedValueOnce(apiResponse(templateSummaries()))
      .mockResolvedValueOnce(apiResponse(templateSummaries('en-US')));

    render(
      <I18nProvider>
        <LanguageSelector />
        <LearningPlanCreatePage onBackToPlans={vi.fn()} onSaved={vi.fn()} />
      </I18nProvider>,
    );

    fireEvent.click(await screen.findByRole('button', { name: /NeetCode 150/ }));
    expect(screen.getByRole('button', { name: /NeetCode 150/ })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.click(screen.getByRole('combobox', { name: '语言' }));
    fireEvent.click(screen.getByRole('option', { name: 'English' }));

    expect(await screen.findByText('LeetCode 75 English')).toBeInTheDocument();
    expect(getLearningPlanTemplatesMock).toHaveBeenCalledTimes(2);
    const englishLocaleCallIndex = setApiLocaleMock.mock.calls.findIndex(([nextLocale]) => nextLocale === 'en-US');
    expect(englishLocaleCallIndex).toBeGreaterThanOrEqual(0);
    expect(setApiLocaleMock.mock.invocationCallOrder[englishLocaleCallIndex])
      .toBeLessThan(getLearningPlanTemplatesMock.mock.invocationCallOrder[1]);
    expect(screen.getByRole('button', { name: /NeetCode 150 English/ })).toHaveAttribute('aria-pressed', 'true');
  });
});

function apiResponse<T>(data: T): ApiResponse<T> {
  return {
    success: true,
    data,
    timestamp: '2026-06-22T00:00:00Z',
  };
}

function templateSummaries(contentLocale: 'zh-CN' | 'en-US' = 'zh-CN'): LearningPlanTemplateSummaryResponse[] {
  const templates: LearningPlanTemplateSummaryResponse[] = [
    {
      templateId: 'leetcode_75_core_sprint',
      contentLocale,
      title: 'LeetCode 75',
      summary: '面试高频基础模板',
      catalogCategory: 'INTERVIEW_PREP',
      recommendedOrder: 1,
      intent: 'INTERVIEW_SPRINT',
      defaultDurationWeeks: 4,
      level: 'INTERMEDIATE',
      defaultWeeklyHours: 8,
      programmingLanguage: 'Java',
      difficultyPreference: 'MEDIUM',
      topicPreferences: ['Array', 'Hash Table'],
      targetAudience: '准备算法面试的学习者',
      expectedOutcome: '掌握核心题型',
      plannedProblemCount: 75,
      defaultLoadSummary: loadSummary(75, 82, 32, 'OVERLOADED'),
      defaultRhythmSettings: rhythmSettings(4, 5, 75),
    },
    {
      templateId: 'neetcode_150_systematic_interview',
      contentLocale,
      title: 'NeetCode 150',
      summary: '覆盖更多专题的面试模板',
      catalogCategory: 'INTERVIEW_PREP',
      recommendedOrder: 4,
      intent: 'INTERVIEW_SPRINT',
      defaultDurationWeeks: 12,
      level: 'INTERMEDIATE',
      defaultWeeklyHours: 10,
      programmingLanguage: 'Java',
      difficultyPreference: 'MIXED',
      topicPreferences: ['Array', 'Dynamic Programming'],
      targetAudience: '需要系统覆盖题型的学习者',
      expectedOutcome: '完成系统面试题型覆盖',
      plannedProblemCount: 150,
      defaultLoadSummary: loadSummary(150, 180, 120, 'OVERLOADED'),
      defaultRhythmSettings: rhythmSettings(3, 5, 150),
    },
    ...additionalTemplateDefinitions.map((definition, index): LearningPlanTemplateSummaryResponse => ({
      templateId: definition.templateId,
      contentLocale,
      title: definition.title,
      summary: `${definition.title}摘要`,
      catalogCategory: definition.catalogCategory,
      recommendedOrder: definition.recommendedOrder,
      intent: definition.catalogCategory === 'TOPIC_BREAKTHROUGH'
        ? 'TOPIC_BREAKTHROUGH'
        : 'LONG_TERM_LEARNING',
      defaultDurationWeeks: 4,
      level: 'INTERMEDIATE',
      defaultWeeklyHours: 8,
      programmingLanguage: definition.catalogCategory === 'LANGUAGE_AND_ROLE' ? 'SQL' : 'Java',
      difficultyPreference: 'MEDIUM',
      topicPreferences: ['Array'],
      targetAudience: '学习者',
      expectedOutcome: '完成训练',
      plannedProblemCount: 20 + index,
      defaultLoadSummary: loadSummary(20 + index, 40, 64, 'RECOMMENDED'),
      defaultRhythmSettings: rhythmSettings(1, 5, 20 + index),
    })),
  ];
  if (contentLocale === 'zh-CN') {
    return templates;
  }
  return templates.map((template) => ({
    ...template,
    title: `${template.title} English`,
    summary: `${template.title} English summary`,
    targetAudience: 'Learners',
    expectedOutcome: 'Complete the route',
  }));
}

const additionalTemplateDefinitions: Array<{
  templateId: string;
  title: string;
  catalogCategory: LearningPlanTemplateSummaryResponse['catalogCategory'];
  recommendedOrder?: number;
}> = [
  { templateId: 'programming_skills_implementation_foundation', title: '编程基础与实现力计划', catalogCategory: 'SYSTEMATIC_LEARNING' },
  { templateId: 'cn_algorithm_foundation_12weeks', title: '中文系统刷题入门计划', catalogCategory: 'SYSTEMATIC_LEARNING', recommendedOrder: 2 },
  { templateId: 'carl_algorithm_roadmap_full', title: '代码随想录完整刷题路线', catalogCategory: 'SYSTEMATIC_LEARNING', recommendedOrder: 3 },
  { templateId: 'leetcode_patterns_beginner_roadmap', title: '算法模式入门训练计划', catalogCategory: 'SYSTEMATIC_LEARNING' },
  { templateId: 'labuladong_algo_thinking', title: 'labuladong 核心算法框架训练计划', catalogCategory: 'SYSTEMATIC_LEARNING', recommendedOrder: 5 },
  { templateId: 'neetcode_blind_75_interview_core', title: 'NeetCode Blind 75 面试核心计划', catalogCategory: 'INTERVIEW_PREP' },
  { templateId: 'tih_best_practice_50_5weeks', title: '5 周面试冲刺计划', catalogCategory: 'INTERVIEW_PREP' },
  { templateId: 'sword_offer_classic', title: '剑指 Offer 经典面试计划', catalogCategory: 'INTERVIEW_PREP' },
  { templateId: 'cracking_coding_interview_classic', title: '程序员面试金典系统训练计划', catalogCategory: 'INTERVIEW_PREP' },
  { templateId: 'leetcode_top_interview_150', title: 'LeetCode 面试经典 150 题计划', catalogCategory: 'INTERVIEW_PREP' },
  { templateId: 'leetcode_top_100_liked_revision', title: 'LeetCode 热题 100', catalogCategory: 'INTERVIEW_PREP' },
  { templateId: 'tih_algorithm_essentials', title: '算法面试核心专题计划', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_dynamic_programming_foundation', title: '动态规划专项突破计划', catalogCategory: 'TOPIC_BREAKTHROUGH', recommendedOrder: 6 },
  { templateId: 'topic_dp_advanced', title: '动态规划进阶专项计划', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_graph_bfs_dfs', title: '图论专项突破计划', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_binary_search_boundaries', title: '二分与边界专项计划', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_sliding_window_two_pointers', title: '滑动窗口与双指针专项计划', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_tree_binary_tree_foundation', title: '树与二叉树专项', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_backtracking_foundation', title: '回溯专项突破', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_heap_priority_queue', title: '堆与优先队列专项', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_greedy_strategies', title: '贪心策略专项', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_stack_monotonic', title: '栈与单调栈专项', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_bit_manipulation', title: '位运算专项', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_linked_list', title: '链表专项', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_union_find_and_advanced_graph', title: '并查集与进阶图论专项', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_prefix_sum_difference', title: '前缀和与差分专项', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_trie_and_string_advanced', title: '字典树与字符串进阶', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_intervals_scheduling', title: '区间与调度专项', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'topic_data_structure_design', title: '数据结构设计专项', catalogCategory: 'TOPIC_BREAKTHROUGH' },
  { templateId: 'leetcode_sql_50', title: 'LeetCode SQL 50 系统训练计划', catalogCategory: 'LANGUAGE_AND_ROLE' },
  { templateId: 'leetcode_javascript_30_days', title: 'LeetCode JavaScript 30 天训练计划', catalogCategory: 'LANGUAGE_AND_ROLE' },
  { templateId: 'leetcode_pandas_introduction', title: 'LeetCode Pandas 入门训练计划', catalogCategory: 'LANGUAGE_AND_ROLE' },
  { templateId: 'leetcode_pandas_30_days', title: 'LeetCode Pandas 30 天进阶计划', catalogCategory: 'LANGUAGE_AND_ROLE' },
];

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
    contentLocale: 'zh-CN',
    title: '四周 Java 算法面试冲刺计划',
    summary: '围绕数组和哈希表建立高频题型能力。',
    intent: 'INTERVIEW_SPRINT',
    objective: '准备 Java 后端算法面试',
    durationWeeks: 4,
    level: 'INTERMEDIATE',
    weeklyHours: 8,
    programmingLanguage: 'Java',
    difficultyDistribution: { easyPercent: 35, mediumPercent: 55, hardPercent: 10 },
    topicPreferences: ['Array', 'Hash Table'],
    additionalConstraints: '每周留一天复盘。',
    phases: [{
      phaseIndex: 1,
      title: '基础题型恢复',
      durationWeeks: 1,
      focus: '数组和哈希表',
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
