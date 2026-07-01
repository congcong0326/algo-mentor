import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { LearningPlanExtensionReadyEvent } from '../types/api';
import LearningPlanExtensionPanel from './LearningPlanExtensionPanel';

afterEach(cleanup);

describe('LearningPlanExtensionPanel', () => {
  const extension: LearningPlanExtensionReadyEvent = {
    proposalGroupId: 30,
    proposalId: 31,
    planId: 88,
    revisionNo: 1,
    status: 'READY',
    supersededProposalIds: [],
    summary: '增加动态规划强化',
    extensionDraft: {
      summary: '追加动态规划和背包专题。',
      metadata: { source: 'ai' },
      newPhases: [{
        phaseIndex: 3,
        title: '动态规划强化',
        durationWeeks: 2,
        focus: '线性 DP 和背包模型',
        objectives: ['掌握状态定义'],
        recommendedTags: ['Dynamic Programming'],
        acceptanceCriteria: ['能解释转移方程'],
        reviewAdvice: '复盘状态压缩。',
        problems: [{
          slug: 'climbing-stairs',
          frontendId: 70,
          title: 'Climbing Stairs',
          titleCn: '爬楼梯',
          difficulty: 'EASY',
          tags: ['Dynamic Programming'],
          reason: '用低门槛题目恢复状态转移。',
          sortOrder: 1,
        }],
      }],
    },
  };

  it('calls onGenerate with trimmed text', () => {
    const onGenerate = vi.fn(() => Promise.resolve(false));

    render(
      <LearningPlanExtensionPanel
        loading={false}
        onApply={vi.fn(() => Promise.resolve())}
        onDiscard={vi.fn(() => Promise.resolve())}
        onGenerate={onGenerate}
        onRevise={vi.fn(() => Promise.resolve(false))}
      />,
    );

    fireEvent.change(screen.getByRole('textbox', { name: '想继续学习？描述接下来的目标' }), {
      target: { value: '  继续练动态规划  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: '生成扩展建议' }));

    expect(onGenerate).toHaveBeenCalledWith('继续练动态规划');
  });

  it('renders pending extension phases and problems', () => {
    render(
      <LearningPlanExtensionPanel
        extension={extension}
        loading={false}
        onApply={vi.fn(() => Promise.resolve())}
        onDiscard={vi.fn(() => Promise.resolve())}
        onGenerate={vi.fn(() => Promise.resolve(false))}
        onRevise={vi.fn(() => Promise.resolve(false))}
      />,
    );

    expect(screen.getByRole('heading', { name: '待追加内容' })).toBeInTheDocument();
    expect(screen.getByText('动态规划强化')).toBeInTheDocument();
    expect(screen.getByText('爬楼梯')).toBeInTheDocument();
    expect(screen.getByText('用低门槛题目恢复状态转移。')).toBeInTheDocument();
  });

  it('calls onRevise with proposalGroupId and trimmed instruction', () => {
    const onRevise = vi.fn(() => Promise.resolve(false));

    render(
      <LearningPlanExtensionPanel
        extension={extension}
        loading={false}
        onApply={vi.fn(() => Promise.resolve())}
        onDiscard={vi.fn(() => Promise.resolve())}
        onGenerate={vi.fn(() => Promise.resolve(false))}
        onRevise={onRevise}
      />,
    );

    fireEvent.change(screen.getByRole('textbox', { name: '对扩展建议不满意？输入调整要求' }), {
      target: { value: '  提高难度并加入背包  ' },
    });
    fireEvent.click(screen.getByRole('button', { name: '按要求调整扩展' }));

    expect(onRevise).toHaveBeenCalledWith(30, '提高难度并加入背包');
  });

  it('calls onDiscard with proposalGroupId', () => {
    const onDiscard = vi.fn(() => Promise.resolve());

    render(
      <LearningPlanExtensionPanel
        extension={extension}
        loading={false}
        onApply={vi.fn(() => Promise.resolve())}
        onDiscard={onDiscard}
        onGenerate={vi.fn(() => Promise.resolve(false))}
        onRevise={vi.fn(() => Promise.resolve(false))}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '放弃' }));

    expect(onDiscard).toHaveBeenCalledWith(30);
  });

  it('calls onApply with proposalGroupId', () => {
    const onApply = vi.fn(() => Promise.resolve());

    render(
      <LearningPlanExtensionPanel
        extension={extension}
        loading={false}
        onApply={onApply}
        onDiscard={vi.fn(() => Promise.resolve())}
        onGenerate={vi.fn(() => Promise.resolve(false))}
        onRevise={vi.fn(() => Promise.resolve(false))}
      />,
    );

    fireEvent.click(screen.getByRole('button', { name: '应用扩展' }));

    expect(onApply).toHaveBeenCalledWith(30);
  });

  it('clears generate instruction after successful generation', async () => {
    render(
      <LearningPlanExtensionPanel
        loading={false}
        onApply={vi.fn(() => Promise.resolve())}
        onDiscard={vi.fn(() => Promise.resolve())}
        onGenerate={vi.fn(() => Promise.resolve(true))}
        onRevise={vi.fn(() => Promise.resolve(false))}
      />,
    );

    const textarea = screen.getByRole('textbox', { name: '想继续学习？描述接下来的目标' });
    fireEvent.change(textarea, {
      target: { value: '继续练图论' },
    });
    fireEvent.click(screen.getByRole('button', { name: '生成扩展建议' }));

    await waitFor(() => expect(textarea).toHaveValue(''));
  });

  it('keeps revision instruction when revision rejects', async () => {
    const onRevise = vi.fn(() => Promise.reject(new Error('revision failed')));

    render(
      <LearningPlanExtensionPanel
        extension={extension}
        loading={false}
        onApply={vi.fn(() => Promise.resolve())}
        onDiscard={vi.fn(() => Promise.resolve())}
        onGenerate={vi.fn(() => Promise.resolve(false))}
        onRevise={onRevise}
      />,
    );

    const textarea = screen.getByRole('textbox', { name: '对扩展建议不满意？输入调整要求' });
    fireEvent.change(textarea, {
      target: { value: '降低难度' },
    });
    fireEvent.click(screen.getByRole('button', { name: '按要求调整扩展' }));

    await waitFor(() => expect(onRevise).toHaveBeenCalledWith(30, '降低难度'));
    expect(textarea).toHaveValue('降低难度');
  });
});
