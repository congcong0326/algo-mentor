import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import LearningPlanDraftPanel from './LearningPlanDraftPanel';
import type { LearningPlanDraftResponse } from '../types/api';

describe('LearningPlanDraftPanel', () => {
  it('submits clarification answers for collecting drafts', () => {
    const onSendFollowUp = vi.fn();

    render(
      <LearningPlanDraftPanel
        draft={{
          draftId: 100,
          status: 'COLLECTING',
          assistantMessage: '请补充目标主题。',
          missingFields: ['topicPreferences'],
          draftPlan: null,
        }}
        loading={false}
        onConfirm={vi.fn()}
        onSendFollowUp={onSendFollowUp}
      />,
    );

    expect(screen.getByRole('heading', { name: 'Agent 追问' })).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '补充回答' }), {
      target: { value: '数组和哈希表' },
    });
    fireEvent.click(screen.getByRole('button', { name: '发送补充' }));

    expect(onSendFollowUp).toHaveBeenCalledWith('数组和哈希表');
  });

  it('shows generated draft preview and confirms it', () => {
    const onConfirm = vi.fn();
    const draft: LearningPlanDraftResponse = {
      draftId: 100,
      status: 'GENERATED',
      assistantMessage: '已生成学习计划草案。',
      missingFields: [],
      draftPlan: {
        title: '四周 Java 算法面试冲刺计划',
        summary: '围绕数组和哈希表建立高频题型能力。',
        intent: 'INTERVIEW_SPRINT',
        goal: '准备 Java 后端算法面试',
        durationWeeks: 4,
        level: 'INTERMEDIATE',
        weeklyHours: 6,
        programmingLanguage: 'Java',
        difficultyPreference: 'MEDIUM',
        interviewOriented: true,
        topicPreferences: ['Array', 'Hash Table'],
        profileSummary: '中级，每周 6 小时。',
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
      },
    };

    render(
      <LearningPlanDraftPanel
        draft={draft}
        loading={false}
        onConfirm={onConfirm}
        onSendFollowUp={vi.fn()}
      />,
    );

    expect(screen.getByRole('heading', { name: '草案预览' })).toBeInTheDocument();
    expect(screen.getByText('基础题型恢复')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '确认保存' }));

    expect(onConfirm).toHaveBeenCalled();
  });
});
