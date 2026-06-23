import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { LearningPlanPageResponse } from '../types/api';
import LearningPlanListCard from './LearningPlanListCard';

afterEach(cleanup);

describe('LearningPlanListCard', () => {
  const page: LearningPlanPageResponse = {
    items: [{
      id: 900,
      title: '四周 Java 算法面试冲刺计划',
      intent: 'INTERVIEW_SPRINT',
      goal: '准备 Java 后端算法面试',
      durationWeeks: 4,
      level: 'INTERMEDIATE',
      weeklyHours: 6,
      status: 'ACTIVE',
      createdAt: '2026-06-22T00:00:00Z',
    }],
    total: 12,
    page: 1,
    pageSize: 10,
    activeCount: 8,
    archivedCount: 4,
    latestCreatedAt: '2026-06-22T00:00:00Z',
  };

  it('renders plan content on the left and actions on the right', () => {
    const onSelect = vi.fn();
    const onDelete = vi.fn();

    render(
      <LearningPlanListCard
        deletingPlanId={undefined}
        onDelete={onDelete}
        onPageChange={vi.fn()}
        onSelect={onSelect}
        page={page}
        selectedPlanId={900}
      />,
    );

    expect(screen.getByText('四周 Java 算法面试冲刺计划')).toBeInTheDocument();
    expect(screen.getByText('准备 Java 后端算法面试')).toBeInTheDocument();
    expect(screen.getByText('4 周 · 6 小时/周 · INTERVIEW_SPRINT · INTERMEDIATE')).toBeInTheDocument();
    expect(screen.getByText('ACTIVE')).toBeInTheDocument();

    const selectedRow = screen.getByTestId('learning-plan-row-900');
    expect(selectedRow).toHaveClass('selected');

    fireEvent.click(screen.getByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    expect(onSelect).toHaveBeenCalledWith(900);

    fireEvent.click(screen.getByRole('button', { name: '删除 四周 Java 算法面试冲刺计划' }));
    expect(onDelete).toHaveBeenCalledWith(900);
  });

  it('renders an empty state when the page has no plans', () => {
    render(
      <LearningPlanListCard
        deletingPlanId={undefined}
        onDelete={vi.fn()}
        onPageChange={vi.fn()}
        onSelect={vi.fn()}
        page={{ ...page, items: [], total: 0 }}
        selectedPlanId={undefined}
      />,
    );

    expect(screen.getByText('暂无正式计划，先新建一个学习计划。')).toBeInTheDocument();
  });

  it('moves between pages and disables unavailable pagination actions', () => {
    const onPageChange = vi.fn();
    const { rerender } = render(
      <LearningPlanListCard
        deletingPlanId={undefined}
        onDelete={vi.fn()}
        onPageChange={onPageChange}
        onSelect={vi.fn()}
        page={page}
        selectedPlanId={undefined}
      />,
    );

    expect(screen.getByRole('button', { name: '上一页' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '下一页' }));
    expect(onPageChange).toHaveBeenCalledWith(2);

    rerender(
      <LearningPlanListCard
        deletingPlanId={undefined}
        onDelete={vi.fn()}
        onPageChange={onPageChange}
        onSelect={vi.fn()}
        page={{ ...page, page: 2 }}
        selectedPlanId={undefined}
      />,
    );

    expect(screen.getByRole('button', { name: '下一页' })).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: '上一页' }));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it('disables the delete button while that plan is deleting', () => {
    render(
      <LearningPlanListCard
        deletingPlanId={900}
        onDelete={vi.fn()}
        onPageChange={vi.fn()}
        onSelect={vi.fn()}
        page={page}
        selectedPlanId={undefined}
      />,
    );

    expect(screen.getByRole('button', { name: '删除 四周 Java 算法面试冲刺计划' })).toBeDisabled();
    expect(screen.getByText('删除中')).toBeInTheDocument();
  });
});
