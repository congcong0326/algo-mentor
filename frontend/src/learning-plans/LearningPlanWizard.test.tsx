import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import LearningPlanWizard from './LearningPlanWizard';

describe('LearningPlanWizard', () => {
  it('walks through steps and submits a structured draft request', () => {
    const onSubmit = vi.fn();

    render(<LearningPlanWizard loading={false} onCancel={vi.fn()} onSubmit={onSubmit} />);

    expect(screen.getByRole('heading', { name: '目标' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '下一步' })).toBeDisabled();

    fireEvent.change(screen.getByRole('textbox', { name: '学习目标' }), {
      target: { value: '6 周准备 Java 后端算法面试' },
    });
    fireEvent.change(screen.getByRole('combobox', { name: '计划意图' }), {
      target: { value: 'INTERVIEW_SPRINT' },
    });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    expect(screen.getByRole('heading', { name: '时间与水平' })).toBeInTheDocument();
    fireEvent.change(screen.getByRole('spinbutton', { name: '计划周期' }), {
      target: { value: '6' },
    });
    fireEvent.change(screen.getByRole('spinbutton', { name: '每周小时' }), {
      target: { value: '8' },
    });
    fireEvent.change(screen.getByRole('combobox', { name: '当前水平' }), {
      target: { value: 'INTERMEDIATE' },
    });
    fireEvent.change(screen.getByRole('textbox', { name: '编程语言' }), {
      target: { value: 'Java' },
    });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    expect(screen.getByRole('heading', { name: '主题偏好' })).toBeInTheDocument();
    fireEvent.change(screen.getByRole('textbox', { name: '添加主题' }), {
      target: { value: 'Array, Hash Table' },
    });
    fireEvent.keyDown(screen.getByRole('textbox', { name: '添加主题' }), { key: 'Enter' });
    expect(screen.getByText('Array')).toBeInTheDocument();
    expect(screen.getByText('Hash Table')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));

    expect(screen.getByRole('heading', { name: '生成与确认' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '生成草案' }));

    expect(onSubmit).toHaveBeenCalledWith({
      intent: 'INTERVIEW_SPRINT',
      goal: '6 周准备 Java 后端算法面试',
      durationWeeks: 6,
      level: 'INTERMEDIATE',
      weeklyHours: 8,
      programmingLanguage: 'Java',
      difficultyPreference: 'MEDIUM',
      interviewOriented: true,
      topicPreferences: ['Array', 'Hash Table'],
    });
  });

  it('prevents moving past invalid numeric values', () => {
    render(<LearningPlanWizard loading={false} onCancel={vi.fn()} onSubmit={vi.fn()} />);

    fireEvent.change(screen.getByRole('textbox', { name: '学习目标' }), {
      target: { value: '准备面试' },
    });
    fireEvent.click(screen.getByRole('button', { name: '下一步' }));
    fireEvent.change(screen.getByRole('spinbutton', { name: '计划周期' }), {
      target: { value: '0' },
    });

    expect(screen.getByRole('button', { name: '下一步' })).toBeDisabled();
    expect(screen.getByText('周期和每周小时数必须大于 0。')).toBeInTheDocument();
  });
});
