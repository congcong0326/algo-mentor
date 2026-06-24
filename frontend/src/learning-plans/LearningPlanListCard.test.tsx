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
      programmingLanguage: 'Java',
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
    const onCreate = vi.fn();

    render(
      <LearningPlanListCard
        deletingPlanId={undefined}
        onCreate={onCreate}
        onDelete={onDelete}
        onPageChange={vi.fn()}
        onSelect={onSelect}
        page={page}
        selectedPlanId={900}
      />,
    );

    expect(screen.getByRole('heading', { name: '训练方案' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '方案库' })).toBeInTheDocument();
    expect(screen.getByText('四周 Java 算法面试冲刺计划')).toBeInTheDocument();
    expect(screen.getByText('准备 Java 后端算法面试')).toBeInTheDocument();
    expect(screen.queryByText('4 周 · 6 小时/周 · INTERVIEW_SPRINT · INTERMEDIATE')).not.toBeInTheDocument();
    expect(screen.getByText('Java')).toBeInTheDocument();
    expect(screen.getByText('中级')).toBeInTheDocument();
    expect(screen.getByText('面试冲刺')).toBeInTheDocument();
    expect(screen.getByText('4 周')).toBeInTheDocument();
    expect(screen.getByText('6h/周')).toBeInTheDocument();
    expect(screen.getByText('共 12 个方案')).toBeInTheDocument();
    expect(screen.getByText('1-10')).toBeInTheDocument();
    expect(screen.getByText('8 个方案正在推进')).toBeInTheDocument();
    expect(screen.getByText('第 1 / 2 页')).toBeInTheDocument();

    const selectedRow = screen.getByTestId('learning-plan-row-900');
    expect(selectedRow).toHaveTextContent('进行中');
    expect(selectedRow).toHaveClass('selected');
    expect(selectedRow).toHaveAttribute('aria-current', 'true');

    fireEvent.click(screen.getByRole('button', { name: '新建方案' }));
    expect(onCreate).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    expect(onSelect).toHaveBeenCalledWith(900);

    fireEvent.click(screen.getByRole('button', { name: '删除 四周 Java 算法面试冲刺计划' }));
    expect(onDelete).toHaveBeenCalledWith(900);
  });

  it('renders an empty state when the page has no plans', () => {
    const onCreate = vi.fn();

    render(
      <LearningPlanListCard
        deletingPlanId={undefined}
        onCreate={onCreate}
        onDelete={vi.fn()}
        onPageChange={vi.fn()}
        onSelect={vi.fn()}
        page={{ ...page, items: [], total: 0 }}
        selectedPlanId={undefined}
      />,
    );

    expect(screen.getByRole('heading', { name: '暂无正式方案' })).toBeInTheDocument();
    expect(screen.getByText('先新建一个训练方案，把目标、周期和题目安排统一起来。')).toBeInTheDocument();
    expect(screen.getByText('0-0')).toBeInTheDocument();

    expect(screen.getAllByRole('button', { name: '新建方案' })).toHaveLength(1);
  });

  it('moves between pages and disables unavailable pagination actions', () => {
    const onPageChange = vi.fn();
    const { rerender } = render(
      <LearningPlanListCard
        deletingPlanId={undefined}
        onCreate={vi.fn()}
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
        onCreate={vi.fn()}
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
        onCreate={vi.fn()}
        onDelete={vi.fn()}
        onPageChange={vi.fn()}
        onSelect={vi.fn()}
        page={page}
        selectedPlanId={undefined}
      />,
    );

    expect(screen.getByRole('button', { name: '删除 四周 Java 算法面试冲刺计划' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '删除 四周 Java 算法面试冲刺计划' })).toHaveAttribute('title', '删除中');
  });
});
