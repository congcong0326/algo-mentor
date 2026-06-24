import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LearningPlanDraftPanel from './LearningPlanDraftPanel';
import type { LearningPlanDraftResponse } from '../types/api';

afterEach(cleanup);

describe('LearningPlanDraftPanel', () => {
  const draftPlan: NonNullable<LearningPlanDraftResponse['draftPlan']> = {
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
  };

  it('submits clarification answers for collecting drafts', () => {
    const onSendFollowUp = vi.fn(() => Promise.resolve(true));

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
        onReturnToWizard={vi.fn()}
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
      assistantMessage: '已生成训练方案草案。',
      missingFields: [],
      draftPlan,
    };

    render(
      <LearningPlanDraftPanel
        draft={draft}
        loading={false}
        onConfirm={onConfirm}
        onReturnToWizard={vi.fn()}
        onSendFollowUp={vi.fn(() => Promise.resolve(true))}
      />,
    );

    expect(screen.getByRole('heading', { name: '训练方案' })).toBeInTheDocument();
    expect(screen.getByText('基础题型恢复')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '保存方案' }));

    expect(onConfirm).toHaveBeenCalled();
  });

  it('allows editing the goal summary and asks for regeneration', () => {
    const onRegenerate = vi.fn();
    const draft: LearningPlanDraftResponse = {
      draftId: 100,
      status: 'GENERATED',
      assistantMessage: '已生成训练方案草案。',
      missingFields: [],
      draftPlan,
    };

    render(
      <LearningPlanDraftPanel
        draft={draft}
        loading={false}
        onConfirm={vi.fn()}
        onRegenerateGoal={onRegenerate}
        onReturnToWizard={vi.fn()}
        onSendFollowUp={vi.fn(() => Promise.resolve(true))}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '编辑目标摘要' }));
    fireEvent.change(screen.getByRole('textbox', { name: '目标摘要' }), {
      target: { value: '改成动态规划冲刺目标。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '按新目标重新生成' }));

    expect(onRegenerate).toHaveBeenCalledWith('改成动态规划冲刺目标。');
  });

  it('resets goal editing state when a regenerated draft arrives', () => {
    const onRegenerate = vi.fn();
    const draft: LearningPlanDraftResponse = {
      draftId: 100,
      status: 'GENERATED',
      assistantMessage: '已生成训练方案草案。',
      missingFields: [],
      draftPlan,
    };
    const { rerender } = render(
      <LearningPlanDraftPanel
        draft={draft}
        loading={false}
        onConfirm={vi.fn()}
        onRegenerateGoal={onRegenerate}
        onReturnToWizard={vi.fn()}
        onSendFollowUp={vi.fn(() => Promise.resolve(true))}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '编辑目标摘要' }));
    fireEvent.change(screen.getByRole('textbox', { name: '目标摘要' }), {
      target: { value: '仍然停留在旧输入里的目标。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '按新目标重新生成' }));

    rerender(
      <LearningPlanDraftPanel
        draft={{
          ...draft,
          draftId: 101,
          draftPlan: {
            ...draftPlan,
            goal: '新的动态规划冲刺目标',
            title: '动态规划冲刺计划',
          },
        }}
        loading={false}
        onConfirm={vi.fn()}
        onRegenerateGoal={onRegenerate}
        onReturnToWizard={vi.fn()}
        onSendFollowUp={vi.fn(() => Promise.resolve(true))}
      />,
    );

    expect(screen.getByText('新的动态规划冲刺目标')).toBeInTheDocument();
    expect(screen.queryByRole('textbox', { name: '目标摘要' })).not.toBeInTheDocument();
    expect(screen.queryByDisplayValue('仍然停留在旧输入里的目标。')).not.toBeInTheDocument();
  });

  it('does not allow confirmation for failed drafts even when a stale plan exists', () => {
    render(
      <LearningPlanDraftPanel
        draft={{
          draftId: 100,
          status: 'GENERATION_FAILED',
          assistantMessage: '生成失败。',
          missingFields: [],
          draftPlan,
        }}
        loading={false}
        onConfirm={vi.fn()}
        onReturnToWizard={vi.fn()}
        onSendFollowUp={vi.fn(() => Promise.resolve(true))}
      />,
    );

    expect(screen.getByText('草案生成失败或已过期，请重新填写问卷后生成。')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '训练方案' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '保存方案' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重新填写问卷' })).toBeInTheDocument();
  });

  it('does not allow confirmation for expired drafts and requests a clean retry', () => {
    const onRetryCreate = vi.fn();

    render(
      <LearningPlanDraftPanel
        draft={{
          draftId: 100,
          status: 'EXPIRED',
          assistantMessage: '草案已过期。',
          missingFields: [],
          draftPlan,
        }}
        loading={false}
        onConfirm={vi.fn()}
        onRetryCreate={onRetryCreate}
        onSendFollowUp={vi.fn(() => Promise.resolve(true))}
      />,
    );

    expect(screen.getByText('草案生成失败或已过期，请重新填写问卷后生成。')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '训练方案' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '保存方案' })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '重新填写问卷' }));

    expect(onRetryCreate).toHaveBeenCalled();
  });
});
