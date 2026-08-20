import type { ActivityContributionResponse } from '../types/api';

const DAY_IN_MILLISECONDS = 24 * 60 * 60 * 1000;

export type ActivityLevel = 0 | 1 | 2 | 3 | 4;

export interface ActivityDay {
  date: string;
  count: number;
  level: ActivityLevel;
}

export interface ActivityCalendarData {
  days: ActivityDay[];
  totalCount: number;
  activeDays: number;
  currentStreak: number;
  longestStreak: number;
}

/** 将后端按日统计转换成热力图使用的稳定数据结构。 */
export function toActivityCalendarData(response: ActivityContributionResponse): ActivityCalendarData {
  return {
    days: response.days.map((day) => ({
      date: day.date,
      count: day.count,
      level: responseLevel(day.level, day.count),
    })),
    totalCount: response.totalCount,
    activeDays: response.activeDays,
    currentStreak: response.currentStreak,
    longestStreak: response.longestStreak,
  };
}

function responseLevel(value: number, count: number): ActivityLevel {
  return Number.isInteger(value) && value >= 0 && value <= 4
    ? value as ActivityLevel
    : activityLevel(count);
}

export interface ActivityMonthLabel {
  column: number;
  label: string;
}

export function activityLevel(count: number): ActivityLevel {
  if (count <= 0) return 0;
  if (count <= 2) return 1;
  if (count <= 4) return 2;
  if (count <= 6) return 3;
  return 4;
}

// Keep a deterministic fixture for component tests; production renders API data only.
export function buildActivityPreview(today = new Date()): ActivityCalendarData {
  const endDate = localDate(today);
  const firstVisibleDate = addDays(endDate, -364);
  const gridStartDate = addDays(firstVisibleDate, -firstVisibleDate.getDay());
  const days: ActivityDay[] = [];

  for (let index = 0; index < 371; index += 1) {
    const date = addDays(gridStartDate, index);
    const isVisible = date >= firstVisibleDate && date <= endDate;
    const count = isVisible ? previewCount(date, endDate) : 0;
    days.push({
      date: toDateKey(date),
      count,
      level: activityLevel(count),
    });
  }

  const visibleDays = days.filter((day) => day.date >= toDateKey(firstVisibleDate) && day.date <= toDateKey(endDate));
  const totalCount = visibleDays.reduce((total, day) => total + day.count, 0);
  const activeDays = visibleDays.filter((day) => day.count > 0).length;
  const currentStreak = streakEndingAt(visibleDays, visibleDays.length - 1);
  const longestStreak = longestActivityStreak(visibleDays);

  return { days, totalCount, activeDays, currentStreak, longestStreak };
}

export function activityMonthLabels(days: ActivityDay[], locale: string): ActivityMonthLabel[] {
  const labels: ActivityMonthLabel[] = [];
  let previousMonth = '';

  for (let column = 0; column < Math.ceil(days.length / 7); column += 1) {
    const day = days[column * 7];
    if (!day) continue;
    const date = parseDateKey(day.date);
    const monthKey = `${date.getFullYear()}-${date.getMonth()}`;
    if (column === 0 || monthKey !== previousMonth) {
      const month = date.getMonth() + 1;
      const label = locale.toLowerCase().startsWith('zh')
        ? `${month}月`
        : new Intl.DateTimeFormat('en-US', { month: 'short' }).format(date);
      labels.push({ column: column + 1, label });
      previousMonth = monthKey;
    }
  }

  return labels;
}

function previewCount(date: Date, endDate: Date): number {
  const age = differenceInDays(endDate, date);
  if (age >= 0 && age < 6) {
    return age === 0 ? 2 : (age % 3) + 1;
  }

  const dayNumber = Math.floor(date.getTime() / DAY_IN_MILLISECONDS);
  const signal = Math.abs((dayNumber * 17 + 11) % 31);
  if (signal < 16) return 0;
  if (signal < 22) return 1;
  if (signal < 27) return 2;
  if (signal < 30) return 3;
  return 5;
}

function longestActivityStreak(days: ActivityDay[]): number {
  let longest = 0;
  let current = 0;
  days.forEach((day) => {
    current = day.count > 0 ? current + 1 : 0;
    longest = Math.max(longest, current);
  });
  return longest;
}

function streakEndingAt(days: ActivityDay[], endIndex: number): number {
  let streak = 0;
  for (let index = endIndex; index >= 0 && days[index]?.count > 0; index -= 1) {
    streak += 1;
  }
  return streak;
}

function differenceInDays(later: Date, earlier: Date): number {
  return Math.round((later.getTime() - earlier.getTime()) / DAY_IN_MILLISECONDS);
}

function addDays(date: Date, amount: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + amount);
}

function localDate(date: Date): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

function toDateKey(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function parseDateKey(value: string): Date {
  const [year, month, day] = value.split('-').map(Number);
  return new Date(year, month - 1, day);
}
