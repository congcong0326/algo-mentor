import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { LearningPlanDetailResponse, LearningPlanDraftPlan } from '../types/api';
import PlanPreview from './PlanPreview';

afterEach(cleanup);

describe('PlanPreview', () => {
  it('renders progress status badges for confirmed plan details', () => {
    render(<PlanPreview plan={detailPlan} />);

    expect(screen.getByText('已完成')).toBeInTheDocument();
    expect(screen.getByText('进行中')).toBeInTheDocument();
    expect(screen.getByText('已跳过')).toBeInTheDocument();
    expect(screen.getByText('未开始')).toBeInTheDocument();
  });

  it('does not render a default progress badge for draft previews', () => {
    render(<PlanPreview plan={draftPlan} />);

    expect(screen.queryByText('未开始')).not.toBeInTheDocument();
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
  metadata: {},
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
