import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LearningPlanCreateModal from './LearningPlanCreateModal';

afterEach(() => {
  cleanup();
  vi.restoreAllMocks();
});

describe('LearningPlanCreateModal', () => {
  it('submits objective, constraints, and the exact default difficulty distribution', () => {
    const onSubmit = vi.fn();

    render(<LearningPlanCreateModal loading={false} open onClose={vi.fn()} onSubmit={onSubmit} />);

    expect(screen.getByRole('dialog', { name: '新建训练方案' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '面试冲刺' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('textbox', { name: '具体目标（可选）' })).toHaveAttribute('maxlength', '300');
    expect(screen.getByRole('textbox', { name: '其他限制（可选）' })).toHaveAttribute('maxlength', '1000');
    expect(screen.getByRole('checkbox', { name: '参考我的学习数据' })).toBeChecked();
    fireEvent.change(screen.getByRole('spinbutton', { name: '训练周期' }), { target: { value: '6' } });
    fireEvent.change(screen.getByRole('spinbutton', { name: '每周投入' }), { target: { value: '8' } });
    fireEvent.change(screen.getByRole('combobox', { name: '编程语言' }), { target: { value: 'Python3' } });
    fireEvent.click(screen.getByRole('button', { name: '动态规划' }));
    expect(screen.getByRole('button', { name: '动态规划' })).toHaveAttribute('aria-pressed', 'true');
    fireEvent.change(screen.getByRole('textbox', { name: '具体目标（可选）' }), {
      target: { value: '六周内建立动态规划面试题的稳定解题思路。' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: '其他限制（可选）' }), {
      target: { value: '希望每周留一天复盘。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '生成方案草案' }));

    expect(onSubmit).toHaveBeenCalledWith({
      intent: 'INTERVIEW_SPRINT',
      objective: '六周内建立动态规划面试题的稳定解题思路。',
      durationWeeks: 6,
      level: 'INTERMEDIATE',
      weeklyHours: 8,
      programmingLanguage: 'Python3',
      difficultyDistribution: {
        easyPercent: 25,
        mediumPercent: 55,
        hardPercent: 20,
      },
      topicPreferences: ['Dynamic Programming'],
      additionalConstraints: '希望每周留一天复盘。',
      personalizationEnabled: true,
    });
  });

  it('requires a topic for topic breakthrough scenario', () => {
    const onSubmit = vi.fn();

    render(<LearningPlanCreateModal loading={false} open onClose={vi.fn()} onSubmit={onSubmit} />);

    fireEvent.click(screen.getByRole('button', { name: '专项突破' }));
    fireEvent.click(screen.getByRole('button', { name: '生成方案草案' }));

    expect(screen.getByText('专项突破需要至少选择一个主题。')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('专项突破需要至少选择一个主题。');
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('moves focus to the close button when opened', async () => {
    render(<LearningPlanCreateModal loading={false} open onClose={vi.fn()} onSubmit={vi.fn()} />);

    expect(await screen.findByRole('button', { name: '关闭' })).toHaveFocus();
  });

  it('restores focus to the previously focused element after closing', () => {
    const opener = document.createElement('button');
    opener.textContent = '打开计划弹窗';
    document.body.appendChild(opener);
    opener.focus();

    const onClose = vi.fn();

    render(<LearningPlanCreateModal loading={false} open onClose={onClose} onSubmit={vi.fn()} />);

    fireEvent.click(screen.getByRole('button', { name: '关闭' }));

    expect(onClose).toHaveBeenCalled();
    expect(opener).toHaveFocus();

    opener.remove();
  });

  it('restores focus when the parent closes the modal', () => {
    const opener = document.createElement('button');
    opener.textContent = '打开计划弹窗';
    document.body.appendChild(opener);
    opener.focus();

    const { rerender } = render(
      <LearningPlanCreateModal loading={false} open onClose={vi.fn()} onSubmit={vi.fn()} />,
    );

    expect(screen.getByRole('button', { name: '关闭' })).toHaveFocus();

    rerender(<LearningPlanCreateModal loading={false} open={false} onClose={vi.fn()} onSubmit={vi.fn()} />);

    expect(opener).toHaveFocus();

    opener.remove();
  });

  it('wraps tab focus within the dialog', () => {
    render(<LearningPlanCreateModal loading={false} open onClose={vi.fn()} onSubmit={vi.fn()} />);

    const closeButton = screen.getByRole('button', { name: '关闭' });
    const submitButton = screen.getByRole('button', { name: '生成方案草案' });
    closeButton.focus();

    fireEvent.keyDown(screen.getByRole('dialog', { name: '新建训练方案' }), {
      key: 'Tab',
      shiftKey: true,
    });

    expect(submitButton).toHaveFocus();

    fireEvent.keyDown(screen.getByRole('dialog', { name: '新建训练方案' }), { key: 'Tab' });

    expect(closeButton).toHaveFocus();
  });

  it('closes with Escape when not loading', () => {
    const onClose = vi.fn();

    render(<LearningPlanCreateModal loading={false} open onClose={onClose} onSubmit={vi.fn()} />);

    fireEvent.keyDown(screen.getByRole('dialog', { name: '新建训练方案' }), { key: 'Escape' });

    expect(onClose).toHaveBeenCalled();
  });

  it('does not close with Escape while loading', () => {
    const onClose = vi.fn();

    render(<LearningPlanCreateModal loading open onClose={onClose} onSubmit={vi.fn()} />);

    fireEvent.keyDown(screen.getByRole('dialog', { name: '新建训练方案' }), { key: 'Escape' });

    expect(onClose).not.toHaveBeenCalled();
  });

  it('resets form fields and validation errors when reopened', () => {
    const { rerender } = render(
      <LearningPlanCreateModal loading={false} open onClose={vi.fn()} onSubmit={vi.fn()} />,
    );

    fireEvent.click(screen.getByRole('button', { name: '专项突破' }));
    fireEvent.change(screen.getByRole('combobox', { name: '编程语言' }), { target: { value: 'Python3' } });
    fireEvent.click(screen.getByRole('button', { name: '生成方案草案' }));

    expect(screen.getByText('专项突破需要至少选择一个主题。')).toBeInTheDocument();

    rerender(<LearningPlanCreateModal loading={false} open={false} onClose={vi.fn()} onSubmit={vi.fn()} />);
    rerender(<LearningPlanCreateModal loading={false} open onClose={vi.fn()} onSubmit={vi.fn()} />);

    expect(screen.getByRole('combobox', { name: '编程语言' })).toHaveValue('Java');
    expect(screen.queryByText('专项突破需要至少选择一个主题。')).not.toBeInTheDocument();
  });

  it('confirms before closing after changing an objective', () => {
    const onClose = vi.fn();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);

    render(<LearningPlanCreateModal loading={false} open onClose={onClose} onSubmit={vi.fn()} />);

    fireEvent.change(screen.getByRole('textbox', { name: '具体目标（可选）' }), {
      target: { value: '集中练习图论。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '关闭' }));

    expect(confirm).toHaveBeenCalledWith('放弃当前填写的方案问卷？');
    expect(onClose).not.toHaveBeenCalled();
  });

  it('confirms before closing after changing constraints or personalization', () => {
    const onClose = vi.fn();
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(false);

    render(<LearningPlanCreateModal loading={false} open onClose={onClose} onSubmit={vi.fn()} />);

    fireEvent.change(screen.getByRole('textbox', { name: '其他限制（可选）' }), {
      target: { value: '每周留一天复盘。' },
    });
    fireEvent.click(screen.getByRole('button', { name: '关闭' }));
    expect(confirm).toHaveBeenCalledWith('放弃当前填写的方案问卷？');

    fireEvent.click(screen.getByRole('checkbox', { name: '参考我的学习数据' }));
    fireEvent.click(screen.getByRole('button', { name: '关闭' }));
    expect(confirm).toHaveBeenCalledTimes(2);
    expect(onClose).not.toHaveBeenCalled();
  });
});
