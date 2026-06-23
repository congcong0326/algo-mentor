import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LearningPlanCreateModal from './LearningPlanCreateModal';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('LearningPlanCreateModal', () => {
  it('submits a generated goal without a separate learning goal field', () => {
    const onSubmit = vi.fn();

    render(<LearningPlanCreateModal loading={false} open onClose={vi.fn()} onSubmit={onSubmit} />);

    expect(screen.getByRole('dialog', { name: '新建学习计划' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '面试冲刺' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.queryByRole('textbox', { name: '学习目标' })).not.toBeInTheDocument();
    fireEvent.change(screen.getByRole('spinbutton', { name: '计划周期' }), { target: { value: '6' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: '每周投入' }), { target: { value: '8' } });
    fireEvent.change(screen.getByRole('combobox', { name: '编程语言' }), { target: { value: 'Python3' } });
    fireEvent.click(screen.getByRole('button', { name: '动态规划' }));
    expect(screen.getByRole('button', { name: '动态规划' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.change(screen.getByRole('textbox', { name: '补充想法' }), {
      target: { value: '希望每周留一天复盘。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '生成计划草案' }));

    expect(onSubmit).toHaveBeenCalledWith(expect.objectContaining({
      intent: 'INTERVIEW_SPRINT',
      durationWeeks: 6,
      weeklyHours: 8,
      programmingLanguage: 'Python3',
      difficultyPreference: 'MIXED',
      interviewOriented: true,
      topicPreferences: ['Dynamic Programming'],
    }));
    expect(onSubmit.mock.calls[0][0].goal).toContain('补充想法：希望每周留一天复盘。');
  });

  it('requires a topic for topic breakthrough scenario', () => {
    const onSubmit = vi.fn();

    render(<LearningPlanCreateModal loading={false} open onClose={vi.fn()} onSubmit={onSubmit} />);

    fireEvent.click(screen.getByRole('button', { name: '专项突破' }));
    fireEvent.click(screen.getByRole('button', { name: '生成计划草案' }));

    expect(screen.getByText('专项突破需要至少选择一个主题。')).toBeInTheDocument();
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('resets form fields and validation errors when reopened', () => {
    const { rerender } = render(
      <LearningPlanCreateModal loading={false} open onClose={vi.fn()} onSubmit={vi.fn()} />,
    );

    fireEvent.click(screen.getByRole('button', { name: '专项突破' }));
    fireEvent.change(screen.getByRole('combobox', { name: '编程语言' }), { target: { value: 'Python3' } });
    fireEvent.click(screen.getByRole('button', { name: '生成计划草案' }));

    expect(screen.getByText('专项突破需要至少选择一个主题。')).toBeInTheDocument();

    rerender(<LearningPlanCreateModal loading={false} open={false} onClose={vi.fn()} onSubmit={vi.fn()} />);
    rerender(<LearningPlanCreateModal loading={false} open onClose={vi.fn()} onSubmit={vi.fn()} />);

    expect(screen.getByRole('combobox', { name: '编程语言' })).toHaveValue('Java');
    expect(screen.queryByText('专项突破需要至少选择一个主题。')).not.toBeInTheDocument();
  });

  it('confirms before closing after changing only the programming language', () => {
    const onClose = vi.fn();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);

    render(<LearningPlanCreateModal loading={false} open onClose={onClose} onSubmit={vi.fn()} />);

    fireEvent.change(screen.getByRole('combobox', { name: '编程语言' }), { target: { value: 'Python3' } });
    fireEvent.click(screen.getByRole('button', { name: '关闭' }));

    expect(confirm).toHaveBeenCalledWith('放弃当前填写的计划问卷？');
    expect(onClose).not.toHaveBeenCalled();
  });
});
