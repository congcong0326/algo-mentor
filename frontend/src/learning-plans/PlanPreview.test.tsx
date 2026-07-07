import { cleanup, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { LearningPlanDetailResponse, LearningPlanDraftPlan } from '../types/api';
import PlanPreview from './PlanPreview';

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

  it('renders route summary, next training package, and weekly buckets from metadata', () => {
    render(<PlanPreview plan={draftPlan} />);

    expect(screen.getByText('接下来 2 周，每周训练 5 天，每天约 60 分钟')).toBeInTheDocument();
    expect(screen.getAllByText('1 题 · 2 周 · 每周 5h · 强度舒缓').length).toBeGreaterThan(0);
    expect(screen.getByRole('heading', { name: '下一次训练包' })).toBeInTheDocument();
    expect(screen.getByText('新题 1 道 · 预计 60 分钟')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '阶段详情' })).toBeInTheDocument();

    const weeklyPlan = screen.getByLabelText('按周执行计划');
    expect(within(weeklyPlan).getByRole('heading', { name: '按周执行计划' })).toBeInTheDocument();
    expect(within(weeklyPlan).getByRole('heading', { name: '第 1 周：基础阶段' })).toBeInTheDocument();
    expect(within(weeklyPlan).getByText('1 题')).toBeInTheDocument();
    expect(within(weeklyPlan).getByText('复盘建议：复盘边界条件')).toBeInTheDocument();
    expect(within(weeklyPlan).getByText('两数之和')).toBeInTheDocument();
  });

  it('renders weekly review buffer and falls back to unknown problem slugs', () => {
    render(<PlanPreview plan={weeklyEdgePlan} />);

    const weeklyPlan = screen.getByLabelText('按周执行计划');
    expect(within(weeklyPlan).getByText('这周保留为复盘/缓冲，不安排新题。')).toBeInTheDocument();
    expect(within(weeklyPlan).getByText('unknown-slug')).toBeInTheDocument();
    expect(within(weeklyPlan).getByText('模板题目暂未匹配，先按 slug 记录。')).toBeInTheDocument();
  });
});

const draftPlan: LearningPlanDraftPlan = {
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
    weeklyBuckets: [
      {
        weekIndex: 1,
        title: '复盘缓冲',
        plannedProblemCount: 0,
        plannedLoadPoints: 0,
        problemSlugs: [],
      },
      {
        weekIndex: 2,
        title: '补充匹配',
        plannedProblemCount: 1,
        plannedLoadPoints: 1,
        problemSlugs: ['unknown-slug'],
        reviewAdvice: '确认模板题目是否已入库',
      },
    ],
  },
};
