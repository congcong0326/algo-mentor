import type {
  LearningPlanDifficultyPreference,
  LearningPlanIntent,
  LearningPlanLevel,
} from '../types/api';
import type { LocaleResources } from '../i18n/locales';

export const intentOptionValues: LearningPlanIntent[] = [
  'INTERVIEW_SPRINT',
  'PRACTICE_GOAL',
  'TOPIC_BREAKTHROUGH',
  'LONG_TERM_LEARNING',
  'ABILITY_DIAGNOSIS',
  'MISTAKE_REVIEW',
];

export const levelOptionValues: LearningPlanLevel[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];

export const difficultyOptionValues: LearningPlanDifficultyPreference[] = ['EASY', 'MEDIUM', 'HARD', 'MIXED'];

export const planScenarioOptions = [
  { labelKey: 'INTERVIEW_SPRINT', value: 'INTERVIEW_SPRINT', interviewOriented: true },
  { labelKey: 'TOPIC_BREAKTHROUGH', value: 'TOPIC_BREAKTHROUGH', interviewOriented: false },
  { labelKey: 'PRACTICE_GOAL', value: 'PRACTICE_GOAL', interviewOriented: false },
  { labelKey: 'MISTAKE_REVIEW', value: 'MISTAKE_REVIEW', interviewOriented: false },
  { labelKey: 'LONG_TERM_LEARNING', value: 'LONG_TERM_LEARNING', interviewOriented: false },
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
  labelKey: keyof LocaleResources['labels']['difficultyDistribution'];
  value: number;
  preference: LearningPlanDifficultyPreference;
  easyPercent: number;
  mediumPercent: number;
  hardPercent: number;
}> = [
  { labelKey: 'beginner', value: 0, preference: 'EASY', easyPercent: 60, mediumPercent: 35, hardPercent: 5 },
  { labelKey: 'balanced', value: 50, preference: 'MIXED', easyPercent: 25, mediumPercent: 55, hardPercent: 20 },
  { labelKey: 'sprint', value: 100, preference: 'HARD', easyPercent: 10, mediumPercent: 55, hardPercent: 35 },
] as const;

export interface DifficultyDistribution {
  labelKey: keyof LocaleResources['labels']['difficultyDistribution'];
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
    labelKey: closest.labelKey,
    value: clampedValue,
    preference: closest.preference,
    easyPercent,
    mediumPercent,
    hardPercent,
  };
}

export const topicOptions = [
  { value: 'Array' },
  { value: 'Hash Table' },
  { value: 'String' },
  { value: 'Two Pointers' },
  { value: 'Sliding Window' },
  { value: 'Stack' },
  { value: 'Queue' },
  { value: 'Linked List' },
  { value: 'Binary Tree' },
  { value: 'Graph' },
  { value: 'Depth-First Search' },
  { value: 'Binary Search' },
  { value: 'Dynamic Programming' },
  { value: 'Greedy' },
  { value: 'Heap' },
  { value: 'Backtracking' },
  { value: 'Bit Manipulation' },
] as const;

export interface BuildGoalInput {
  resources: LocaleResources;
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
  const resources = input.resources.learningPlans;

  return [
    resources.goalIntent(input.intentLabel),
    resources.goalDuration(input.durationWeeks),
    resources.goalWeeklyHours(input.weeklyHours),
    resources.goalLevel(input.levelLabel),
    resources.goalLanguage(input.programmingLanguage),
    resources.goalDifficulty(input.difficultyLabel, input.easyPercent, input.mediumPercent, input.hardPercent),
    topics.length > 0 ? resources.goalTopics(topics.join(', ')) : resources.goalTopicsAuto,
    input.additionalThoughts.trim() ? resources.goalAdditionalThoughts(input.additionalThoughts.trim()) : undefined,
  ].filter(Boolean).join('\n');
}
