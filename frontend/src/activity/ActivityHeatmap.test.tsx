import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { I18nProvider } from '../i18n/I18nProvider';
import ActivityHeatmap from './ActivityHeatmap';
import {
  activityLevel,
  activityMonthLabels,
  buildActivityPreview,
  toActivityCalendarData,
} from './activityHeatmap';

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('activity heatmap', () => {
  it('maps submission counts to five stable intensity levels', () => {
    expect(activityLevel(0)).toBe(0);
    expect(activityLevel(1)).toBe(1);
    expect(activityLevel(2)).toBe(1);
    expect(activityLevel(3)).toBe(2);
    expect(activityLevel(4)).toBe(2);
    expect(activityLevel(5)).toBe(3);
    expect(activityLevel(6)).toBe(3);
    expect(activityLevel(7)).toBe(4);
  });

  it('expands sparse daily counts into the complete calendar grid', () => {
    const data = toActivityCalendarData({
      timezone: 'Asia/Shanghai',
      from: '2025-08-20',
      to: '2026-08-19',
      totalCount: 8,
      activeDays: 3,
      currentStreak: 3,
      longestStreak: 3,
      dailyCounts: [[362, 1], [363, 2], [364, 5]],
    });

    expect(data.days).toHaveLength(371);
    expect(data.days[0]).toMatchObject({ date: '2025-08-17', count: 0, level: 0 });
    expect(data.days[367]).toMatchObject({ date: '2026-08-19', count: 5, level: 3 });
    expect(data.days[370]).toMatchObject({ date: '2026-08-22', count: 0, level: 0 });
  });

  it('renders a full 53-week calendar with summary statistics', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-19T12:00:00Z'));

    const data = buildActivityPreview(new Date('2026-08-19T12:00:00Z'));
    render(
      <I18nProvider>
        <ActivityHeatmap data={data} />
      </I18nProvider>,
    );

    expect(screen.getByRole('heading', { name: '学习节奏' })).toBeInTheDocument();
    expect(screen.getAllByTestId('activity-heatmap-cell')).toHaveLength(371);
    expect(screen.getByText(String(data.totalCount))).toBeInTheDocument();
    expect(screen.getByText(String(data.currentStreak))).toBeInTheDocument();
    expect(screen.getByText('少')).toBeInTheDocument();
    expect(screen.getByText('多')).toBeInTheDocument();
  });

  it('uses numeric month labels for Chinese and abbreviated month names for English', () => {
    const data = buildActivityPreview(new Date('2026-08-19T12:00:00Z'));

    expect(activityMonthLabels(data.days, 'zh-CN').map((item) => item.label)).toContain('8月');
    expect(activityMonthLabels(data.days, 'zh-CN').map((item) => item.label)).toContain('9月');
    expect(activityMonthLabels(data.days, 'en-US').map((item) => item.label)).toContain('Aug');
    expect(activityMonthLabels(data.days, 'en-US').map((item) => item.label)).toContain('Sep');
  });
});
