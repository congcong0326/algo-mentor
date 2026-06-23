import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import LearningPlanSummaryCard from './LearningPlanSummaryCard';

const OriginalDateTimeFormat = Intl.DateTimeFormat;

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe('LearningPlanSummaryCard', () => {
  it('shows aggregate counts, latest created date, and the create action', () => {
    const onCreate = vi.fn();

    render(
      <LearningPlanSummaryCard
        activeCount={8}
        archivedCount={4}
        latestCreatedAt="2026-06-22T00:00:00Z"
        onCreate={onCreate}
        total={12}
      />,
    );

    expect(screen.getByText('当前共有 12 个计划')).toBeInTheDocument();
    expect(screen.getByText('8')).toBeInTheDocument();
    expect(screen.getByText('进行中')).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();
    expect(screen.getByText('已归档')).toBeInTheDocument();
    expect(screen.getByText('2026-06-22')).toBeInTheDocument();
    expect(screen.getByText('最近创建')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '新建计划' }));

    expect(onCreate).toHaveBeenCalledTimes(1);
  });

  it('formats latest-created date with the local calendar date', () => {
    function MockDateTimeFormat() {
      return {
        formatToParts: () => [
          { type: 'year', value: '2026' },
          { type: 'literal', value: '/' },
          { type: 'month', value: '06' },
          { type: 'literal', value: '/' },
          { type: 'day', value: '23' },
        ],
      };
    }

    vi.stubGlobal('Intl', {
      ...Intl,
      DateTimeFormat: MockDateTimeFormat as unknown as typeof OriginalDateTimeFormat,
    });

    render(
      <LearningPlanSummaryCard
        activeCount={1}
        archivedCount={0}
        latestCreatedAt="2026-06-22T23:30:00Z"
        onCreate={vi.fn()}
        total={1}
      />,
    );

    expect(screen.getByText('2026-06-23')).toBeInTheDocument();
  });

  it('shows the empty latest-created state when there are no plans', () => {
    render(
      <LearningPlanSummaryCard
        activeCount={0}
        archivedCount={0}
        latestCreatedAt={null}
        onCreate={vi.fn()}
        total={0}
      />,
    );

    expect(screen.getByText('当前共有 0 个计划')).toBeInTheDocument();
    expect(screen.getByText('暂无计划')).toBeInTheDocument();
  });
});
