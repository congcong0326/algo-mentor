import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { LearningPlanPageResponse } from '../types/api';
import LearningPlanListCard from './LearningPlanListCard';

afterEach(cleanup);

describe('LearningPlanListCard', () => {
  const page: LearningPlanPageResponse = {
    items: [{
      id: 900,
      contentLocale: 'zh-CN',
      title: '四周 Java 算法面试冲刺计划',
      intent: 'INTERVIEW_SPRINT',
      objective: '准备 Java 后端算法面试',
      durationWeeks: 4,
      level: 'INTERMEDIATE',
      programmingLanguage: 'Java',
      weeklyHours: 6,
      progressSummary: {
        totalProblemCount: 75,
        completedProblemCount: 36,
        progressPercent: 48,
      },
      status: 'ACTIVE',
      createdAt: '2026-06-22T00:00:00Z',
    }],
    total: 12,
    page: 1,
    pageSize: 10,
    activeCount: 8,
    archivedCount: 4,
    latestCreatedAt: '2026-06-22T00:00:00Z',
    activePlanId: 900,
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
    expect(screen.queryByText('准备 Java 后端算法面试')).not.toBeInTheDocument();
    expect(screen.queryByText('4 周 · 6 小时/周 · INTERVIEW_SPRINT · INTERMEDIATE')).not.toBeInTheDocument();
    expect(screen.getByText('Java')).toBeInTheDocument();
    expect(screen.getByText('中级')).toBeInTheDocument();
    expect(screen.getByText('面试冲刺')).toBeInTheDocument();
    expect(screen.getByText('4 周')).toBeInTheDocument();
    expect(screen.getByText('6h/周')).toBeInTheDocument();
    expect(screen.getByText('共 12 个方案')).toBeInTheDocument();
    expect(screen.getByText('方案总数')).toBeInTheDocument();
    expect(screen.getByText('36 / 75 题')).toBeInTheDocument();
    expect(screen.getByText('48%')).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: '计划完成进度：已完成 36/75 题，48%' }))
      .toHaveAttribute('aria-valuenow', '48');
    expect(screen.queryByRole('heading', { name: '当前节奏' })).not.toBeInTheDocument();
    expect(screen.queryByText('今日题包')).not.toBeInTheDocument();
    expect(screen.queryByText('已设置')).not.toBeInTheDocument();
    expect(screen.queryByText('已归档')).not.toBeInTheDocument();
    expect(screen.getByText('1-10')).toBeInTheDocument();
    expect(screen.getByText('第 1 / 2 页')).toBeInTheDocument();

    const selectedRow = screen.getByTestId('learning-plan-row-900');
    expect(selectedRow).not.toHaveTextContent('进行中');
    expect(selectedRow).toHaveTextContent('当前采用');
    expect(selectedRow).toHaveClass('selected');
    expect(selectedRow).toHaveAttribute('aria-current', 'true');

    fireEvent.click(screen.getByRole('button', { name: '新建方案' }));
    expect(onCreate).toHaveBeenCalledTimes(1);

    fireEvent.click(screen.getByRole('button', { name: '查看 四周 Java 算法面试冲刺计划' }));
    expect(onSelect).toHaveBeenCalledWith(900);

    fireEvent.click(screen.getByRole('button', { name: '删除 四周 Java 算法面试冲刺计划' }));
    expect(onDelete).toHaveBeenCalledWith(900);
  });

  it.each([
    [0, '#B8BEC7', '#B8BEC7'],
    [12, '#F59E0B', '#F1F3F5'],
    [52, '#06B6D4', '#F1F3F5'],
    [83, '#10B981', '#F1F3F5'],
    [100, '#059669', '#F1F3F5'],
  ])('uses the training-stage ring color for %s%% progress', (progressPercent, color, track) => {
    render(
      <LearningPlanListCard
        onCreate={vi.fn()}
        onDelete={vi.fn()}
        onPageChange={vi.fn()}
        page={{
          ...page,
          items: [{
            ...page.items[0],
            progressSummary: {
              ...page.items[0].progressSummary,
              progressPercent,
            },
          }],
        }}
      />,
    );

    expect(screen.getByRole('progressbar')).toHaveStyle({
      '--plan-progress-color': color,
      '--plan-progress-track': track,
    });
  });

  it('shows a status badge only for a non-default plan status', () => {
    render(
      <LearningPlanListCard
        onCreate={vi.fn()}
        onDelete={vi.fn()}
        onPageChange={vi.fn()}
        page={{
          ...page,
          activePlanId: null,
          items: [{ ...page.items[0], status: 'ARCHIVED' }],
        }}
      />,
    );

    expect(screen.getByText('已归档')).toBeInTheDocument();
    expect(screen.queryByText('进行中')).not.toBeInTheDocument();
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
        onActivate={vi.fn()}
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

  it('opens the today pack from the active plan row only', () => {
    const onOpenTodayPack = vi.fn();
    render(
      <LearningPlanListCard
        deletingPlanId={undefined}
        onActivate={vi.fn()}
        onCreate={vi.fn()}
        onDelete={vi.fn()}
        onOpenTodayPack={onOpenTodayPack}
        onPageChange={vi.fn()}
        onSelect={vi.fn()}
        page={{
          ...page,
          items: [
            ...page.items,
            {
              id: 901,
              contentLocale: 'zh-CN',
              title: '备用动态规划计划',
              intent: 'TOPIC_BREAKTHROUGH',
              objective: '补动态规划',
              durationWeeks: 3,
              level: 'INTERMEDIATE',
              programmingLanguage: 'Java',
              weeklyHours: 4,
              progressSummary: {
                totalProblemCount: 20,
                completedProblemCount: 0,
                progressPercent: 0,
              },
              status: 'ACTIVE',
              createdAt: '2026-06-23T00:00:00Z',
            },
          ],
          total: 2,
        }}
        selectedPlanId={undefined}
      />,
    );

    expect(within(screen.getByTestId('learning-plan-row-900')).getByRole('button', { name: '今日题包' }))
      .toHaveClass('icon-button', 'plan-middle-action');
    expect(within(screen.getByTestId('learning-plan-row-900')).getByRole('button', { name: '今日题包' }))
      .not.toHaveTextContent('今日题包');
    expect(within(screen.getByTestId('learning-plan-row-900')).getByRole('tooltip', { name: '今日题包' }))
      .toHaveClass('toolbar-tooltip', 'plan-row-action-tooltip');
    expect(within(screen.getByTestId('learning-plan-row-901')).queryByRole('button', { name: '今日题包' }))
      .not.toBeInTheDocument();
    expect(within(screen.getByTestId('learning-plan-row-901')).getByRole('button', { name: '采用' }))
      .toHaveClass('plan-middle-action');

    const activePlanButtons = within(screen.getByTestId('learning-plan-row-900')).getAllByRole('button');
    expect(activePlanButtons.map((button) => button.getAttribute('aria-label') ?? button.textContent)).toEqual([
      '查看 四周 Java 算法面试冲刺计划',
      '今日题包',
      '删除 四周 Java 算法面试冲刺计划',
    ]);

    fireEvent.click(screen.getByRole('button', { name: '今日题包' }));
    expect(onOpenTodayPack).toHaveBeenCalledWith(900);
  });
});
