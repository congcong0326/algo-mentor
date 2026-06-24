import type {
  LearningPlanDifficultyPreference,
  LearningPlanIntent,
  LearningPlanLevel,
} from '../types/api';

export const intentOptions: Array<{ label: string; value: LearningPlanIntent }> = [
  { label: '面试冲刺', value: 'INTERVIEW_SPRINT' },
  { label: '刷题目标', value: 'PRACTICE_GOAL' },
  { label: '专题突破', value: 'TOPIC_BREAKTHROUGH' },
  { label: '长期学习', value: 'LONG_TERM_LEARNING' },
  { label: '能力诊断', value: 'ABILITY_DIAGNOSIS' },
  { label: '错题复盘', value: 'MISTAKE_REVIEW' },
];

export const levelOptions: Array<{ label: string; value: LearningPlanLevel }> = [
  { label: '入门', value: 'BEGINNER' },
  { label: '中级', value: 'INTERMEDIATE' },
  { label: '高级', value: 'ADVANCED' },
];

export const difficultyOptions: Array<{ label: string; value: LearningPlanDifficultyPreference }> = [
  { label: 'Easy', value: 'EASY' },
  { label: 'Medium', value: 'MEDIUM' },
  { label: 'Hard', value: 'HARD' },
  { label: 'Mixed', value: 'MIXED' },
];

export const planScenarioOptions = [
  { label: '面试冲刺', value: 'INTERVIEW_SPRINT', interviewOriented: true },
  { label: '专项突破', value: 'TOPIC_BREAKTHROUGH', interviewOriented: false },
  { label: '基础巩固', value: 'PRACTICE_GOAL', interviewOriented: false },
  { label: '错题复盘', value: 'MISTAKE_REVIEW', interviewOriented: false },
  { label: '长期学习', value: 'LONG_TERM_LEARNING', interviewOriented: false },
] as const;

export const programmingLanguageOptions = [
  'Java',
  'Python3',
  'C++',
  'JavaScript',
  'TypeScript',
  'Go',
  'C#',
  'C',
  'Kotlin',
  'Swift',
  'Rust',
] as const;

export const difficultyDistributionOptions: ReadonlyArray<{
  label: string;
  value: number;
  preference: LearningPlanDifficultyPreference;
  easyPercent: number;
  mediumPercent: number;
  hardPercent: number;
}> = [
  { label: '入门', value: 0, preference: 'EASY', easyPercent: 60, mediumPercent: 35, hardPercent: 5 },
  { label: '均衡', value: 50, preference: 'MIXED', easyPercent: 25, mediumPercent: 55, hardPercent: 20 },
  { label: '冲刺', value: 100, preference: 'HARD', easyPercent: 10, mediumPercent: 55, hardPercent: 35 },
] as const;

export interface DifficultyDistribution {
  label: string;
  value: number;
  preference: LearningPlanDifficultyPreference;
  easyPercent: number;
  mediumPercent: number;
  hardPercent: number;
}

export function clampDifficultyDistributionValue(value: number): number {
  if (!Number.isFinite(value)) {
    return difficultyDistributionOptions[1].value;
  }

  return Math.min(Math.max(Math.round(value), 0), 100);
}

function interpolatePercent(start: number, end: number, ratio: number): number {
  return Math.round(start + (end - start) * ratio);
}

function getDistributionSegment(value: number) {
  const clampedValue = clampDifficultyDistributionValue(value);
  const nextIndex = difficultyDistributionOptions.findIndex((option) => clampedValue <= option.value);
  const endIndex = nextIndex < 0 ? difficultyDistributionOptions.length - 1 : nextIndex;
  const startIndex = Math.max(0, endIndex - 1);
  const start = difficultyDistributionOptions[startIndex];
  const end = difficultyDistributionOptions[endIndex];
  const span = end.value - start.value;
  const ratio = span === 0 ? 0 : (clampedValue - start.value) / span;

  return { clampedValue, end, ratio, start };
}

export function getDifficultyDistribution(value: number): DifficultyDistribution {
  const { clampedValue, end, ratio, start } = getDistributionSegment(value);
  const easyPercent = interpolatePercent(start.easyPercent, end.easyPercent, ratio);
  const hardPercent = interpolatePercent(start.hardPercent, end.hardPercent, ratio);
  const mediumPercent = 100 - easyPercent - hardPercent;
  const closest = difficultyDistributionOptions.reduce((nearest, option) => (
    Math.abs(option.value - clampedValue) < Math.abs(nearest.value - clampedValue) ? option : nearest
  ), difficultyDistributionOptions[0]);

  return {
    label: closest.label,
    value: clampedValue,
    preference: closest.preference,
    easyPercent,
    mediumPercent,
    hardPercent,
  };
}

export const topicOptions = [
  { label: '数组', value: 'Array' },
  { label: '哈希表', value: 'Hash Table' },
  { label: '字符串', value: 'String' },
  { label: '双指针', value: 'Two Pointers' },
  { label: '滑动窗口', value: 'Sliding Window' },
  { label: '栈', value: 'Stack' },
  { label: '队列', value: 'Queue' },
  { label: '链表', value: 'Linked List' },
  { label: '二叉树', value: 'Binary Tree' },
  { label: '图', value: 'Graph' },
  { label: 'DFS/BFS', value: 'Depth-First Search' },
  { label: '二分查找', value: 'Binary Search' },
  { label: '动态规划', value: 'Dynamic Programming' },
  { label: '贪心', value: 'Greedy' },
  { label: '堆', value: 'Heap' },
  { label: '回溯', value: 'Backtracking' },
  { label: '位运算', value: 'Bit Manipulation' },
] as const;

export interface BuildGoalInput {
  intentLabel: string;
  durationWeeks: number;
  weeklyHours: number;
  levelLabel: string;
  programmingLanguage: string;
  difficultyLabel: string;
  easyPercent: number;
  mediumPercent: number;
  hardPercent: number;
  topics: string[];
  additionalThoughts: string;
}

export function buildLearningPlanGoal(input: BuildGoalInput): string {
  const topics = input.topics.map((topic) => topic.trim()).filter(Boolean);

  return [
    `训练场景：${input.intentLabel}`,
    `周期：${input.durationWeeks} 周`,
    `每周投入：${input.weeklyHours} 小时`,
    `当前水平：${input.levelLabel}`,
    `编程语言：${input.programmingLanguage}`,
    `难度分布：${input.difficultyLabel}（简单 ${input.easyPercent}%，中等 ${input.mediumPercent}%，困难 ${input.hardPercent}%）`,
    topics.length > 0 ? `主题偏好：${topics.join(', ')}` : '主题偏好：由系统根据训练场景安排',
    input.additionalThoughts.trim() ? `补充想法：${input.additionalThoughts.trim()}` : undefined,
  ].filter(Boolean).join('\n');
}
