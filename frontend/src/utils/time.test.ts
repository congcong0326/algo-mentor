import { describe, expect, it } from 'vitest';
import { formatUpcomingReviewTime } from './time';

describe('formatUpcomingReviewTime', () => {
  it('shows the remaining minutes for a review due soon', () => {
    const now = Date.parse('2026-07-24T08:00:00Z');

    expect(formatUpcomingReviewTime('2026-07-24T08:10:00Z', now)).toBe('10 分钟后');
  });

  it('falls back safely when no next review time is available', () => {
    expect(formatUpcomingReviewTime(undefined)).toBe('稍后');
  });

  it('formats review times in English', () => {
    const now = Date.parse('2026-07-24T08:00:00Z');

    expect(formatUpcomingReviewTime('2026-07-24T08:10:00Z', now, 'en-US')).toBe('in 10 minutes');
    expect(formatUpcomingReviewTime(undefined, now, 'en-US')).toBe('later');
  });
});
