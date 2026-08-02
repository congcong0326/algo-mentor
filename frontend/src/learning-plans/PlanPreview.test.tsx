import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import { I18nProvider } from '../i18n/I18nProvider';
import LanguageSelector from '../i18n/LanguageSelector';
import type { LearningPlanDetailResponse, LearningPlanDraftPlan } from '../types/api';
import PlanPreview, { PlanPhaseDetails } from './PlanPreview';

afterEach(cleanup);

describe('PlanPreview', () => {
  it('renders progress status badges for confirmed plan details', () => {
    render(<PlanPreview plan={detailPlan} />);

    expect(screen.getAllByText('已完成').length).toBeGreaterThan(0);
    expect(screen.getByText('进行中')).toBeInTheDocument();
    expect(screen.getByText('已跳过')).toBeInTheDocument();
    expect(screen.getByText('未开始')).toBeInTheDocument();
  });

  it('does not render a default progress badge for draft previews', () => {
    render(<PlanPreview plan={draftPlan} />);

    expect(screen.queryByText('未开始')).not.toBeInTheDocument();
  });

  it('renders rhythm settings without intensity summary cards', () => {
    render(<PlanPreview plan={draftPlan} />);

    expect(screen.getByText('每天 1 题 · 每周 5 天')).toBeInTheDocument();
    expect(screen.getByText('共 1 题')).toBeInTheDocument();
    expect(screen.getAllByText('还需约 1 周').length).toBeGreaterThan(0);
    expect(screen.queryByText('强度评估')).not.toBeInTheDocument();
    expect(screen.queryByText('1 题 · 2 周 · 每周 5h · 强度舒缓')).not.toBeInTheDocument();
    expect(screen.queryByText('当前节奏有复盘缓冲，可以稳定推进。')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '下一次训练包' })).toBeInTheDocument();
    expect(screen.getByText('新题 1 道 · 预计 60 分钟')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '阶段详情' })).toBeInTheDocument();
  });

  it('falls back to unknown problem slugs in the next package', () => {
    render(<PlanPreview plan={weeklyEdgePlan} />);

    expect(screen.getByText('unknown-slug')).toBeInTheDocument();
    expect(screen.getByText('模板题目暂未匹配，先按 slug 记录。')).toBeInTheDocument();
  });

  it('renders phase details without the package overview when used alone', () => {
    render(<PlanPhaseDetails plan={draftPlan} />);

    expect(screen.getByRole('heading', { name: '阶段详情' })).toBeInTheDocument();
    expect(screen.getByText('两数之和')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '下一次训练包' })).not.toBeInTheDocument();
    expect(screen.queryByText('新题 1 道 · 预计 60 分钟')).not.toBeInTheDocument();
  });

  it('keeps confirmed plan body in its frozen content locale when the UI locale changes', async () => {
    render(
      <I18nProvider>
        <LanguageSelector />
        <PlanPreview plan={englishDetailPlan} />
      </I18nProvider>,
    );

    expect(screen.getByText('Foundation Phase')).toBeInTheDocument();
    expect(screen.getAllByText('Two Sum').length).toBeGreaterThan(0);
    expect(screen.getAllByText('数组').length).toBeGreaterThan(0);
    expect(screen.getAllByText('简单').length).toBeGreaterThan(0);
    expect(screen.queryByText('两数之和')).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('combobox', { name: '语言' }));
    fireEvent.click(screen.getByRole('option', { name: 'English' }));

    expect(await screen.findByRole('heading', { name: 'Phase Details' })).toBeInTheDocument();
    expect(screen.getByText('Foundation Phase')).toBeInTheDocument();
    expect(screen.getAllByText('Two Sum').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Array').length).toBeGreaterThan(0);
    expect(screen.getAllByText('Easy').length).toBeGreaterThan(0);
    expect(screen.queryByText('两数之和')).not.toBeInTheDocument();
  });
});

const draftPlan: LearningPlanDraftPlan = {
  contentLocale: 'zh-CN',
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
  metadata: {
    loadSummary: {
      durationWeeks: 2,
      weeklyHours: 5,
      weeklyCapacityPoints: 5,
      totalCapacityPoints: 10,
      plannedLoadPoints: 2.5,
      loadRatio: 0.25,
      plannedProblemCount: 1,
      averageProblemsPerWeek: 0.5,
      intensity: 'RELAXED',
      reviewBufferIncluded: true,
      suggestions: ['当前节奏有复盘缓冲，可以稳定推进。'],
    },
    weeklyBuckets: [{
      weekIndex: 1,
      title: '基础阶段',
      plannedProblemCount: 1,
      plannedLoadPoints: 2.5,
      problemSlugs: ['two-sum'],
      reviewAdvice: '复盘边界条件',
    }],
    nextTrainingPackage: {
      weekIndex: 1,
      newProblemCount: 1,
      reviewTask: '复盘边界条件',
      estimatedMinutes: 60,
      priorityProblemSlugs: ['two-sum'],
    },
    dailyProblemCount: 1,
    trainingDaysPerWeek: 5,
  },
  phases: [{
    phaseIndex: 1,
    title: '基础阶段',
    durationWeeks: 1,
    focus: '数组基础',
    objectives: ['理解哈希表'],
    recommendedTags: ['Array'],
    acceptanceCriteria: ['完成 Two Sum'],
    reviewAdvice: '复盘边界条件',
    problems: [{
      slug: 'two-sum',
      frontendId: 1,
      title: 'Two Sum',
      titleCn: '两数之和',
      difficulty: 'EASY',
      tags: ['Array', 'Hash Table'],
      reason: '基础题',
      sortOrder: 1,
    }],
  }],
};

const detailPlan: LearningPlanDetailResponse = {
  ...draftPlan,
  id: 88,
  status: 'ACTIVE',
  active: true,
  createdAt: '2026-06-25T00:00:00Z',
  updatedAt: '2026-06-25T00:00:00Z',
  phases: [{
    ...draftPlan.phases[0],
    problems: [
      { ...draftPlan.phases[0].problems[0], progressStatus: 'COMPLETED' },
      {
        slug: 'valid-palindrome',
        frontendId: 125,
        title: 'Valid Palindrome',
        titleCn: '验证回文串',
        difficulty: 'EASY',
        tags: ['Two Pointers'],
        reason: '双指针基础题',
        sortOrder: 2,
        progressStatus: 'IN_PROGRESS',
      },
      {
        slug: 'number-of-islands',
        frontendId: 200,
        title: 'Number of Islands',
        titleCn: '岛屿数量',
        difficulty: 'MEDIUM',
        tags: ['Graph'],
        reason: '图遍历基础题',
        sortOrder: 3,
        progressStatus: 'SKIPPED',
      },
      {
        slug: 'merge-intervals',
        frontendId: 56,
        title: 'Merge Intervals',
        titleCn: '合并区间',
        difficulty: 'MEDIUM',
        tags: ['Array'],
        reason: '区间基础题',
        sortOrder: 4,
        progressStatus: 'NOT_STARTED',
      },
    ],
  }],
};

const weeklyEdgePlan: LearningPlanDraftPlan = {
  ...draftPlan,
  metadata: {
    ...draftPlan.metadata,
    nextTrainingPackage: {
      weekIndex: 2,
      newProblemCount: 1,
      reviewTask: '确认模板题目是否已入库',
      estimatedMinutes: 45,
      priorityProblemSlugs: ['unknown-slug'],
    },
  },
};

const englishDetailPlan: LearningPlanDetailResponse = {
  ...detailPlan,
  contentLocale: 'en-US',
  title: 'Array Practice',
  summary: 'Practice arrays and hash tables.',
  goal: 'Build reliable array problem-solving skills',
  profileSummary: 'Beginner, 5 hours per week.',
  phases: [{
    ...detailPlan.phases[0],
    title: 'Foundation Phase',
    focus: 'Array fundamentals',
    objectives: ['Understand hash-table lookup'],
    acceptanceCriteria: ['Complete Two Sum'],
    reviewAdvice: 'Review boundary cases.',
    problems: [{
      ...detailPlan.phases[0].problems[0],
      reason: 'Build a reliable lookup pattern.',
    }],
  }],
};
