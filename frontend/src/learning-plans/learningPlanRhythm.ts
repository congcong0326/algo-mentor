import type { LearningPlanRhythmSettings } from '../types/api';

export const STANDARD_TRAINING_DAYS_PER_WEEK = 5;

export interface StandardRhythmReference {
  dailyProblemCount: number;
  trainingDaysPerWeek: number;
  totalProblemCount: number;
  recommendedWeeks: number;
}

export function buildStandardRhythmReference({
  recommendedWeeks,
  settings,
  totalProblemCount,
}: {
  recommendedWeeks: number;
  settings?: LearningPlanRhythmSettings;
  totalProblemCount: number;
}): StandardRhythmReference {
  const normalizedTotalProblemCount = Math.max(0, Math.trunc(settings?.totalProblemCount ?? totalProblemCount));
  const normalizedRecommendedWeeks = Math.max(1, Math.trunc(recommendedWeeks));
  return {
    dailyProblemCount: settings?.dailyProblemCount
      ?? estimateDailyProblemCount(normalizedTotalProblemCount, normalizedRecommendedWeeks),
    trainingDaysPerWeek: STANDARD_TRAINING_DAYS_PER_WEEK,
    totalProblemCount: normalizedTotalProblemCount,
    recommendedWeeks: normalizedRecommendedWeeks,
  };
}

export function estimateDailyProblemCount(totalProblemCount: number, recommendedWeeks: number) {
  if (totalProblemCount <= 0) {
    return 1;
  }
  return Math.max(1, Math.ceil(totalProblemCount / Math.max(1, recommendedWeeks * STANDARD_TRAINING_DAYS_PER_WEEK)));
}

export function estimateRhythmWeeks(totalProblemCount: number, dailyProblemCount: number, trainingDaysPerWeek: number) {
  if (totalProblemCount <= 0) {
    return 0;
  }
  return Math.ceil(totalProblemCount / Math.max(1, dailyProblemCount * trainingDaysPerWeek));
}

export function compareRhythmWeeks(adjustedWeeks: number, standardWeeks: number) {
  return Math.trunc(adjustedWeeks) - Math.trunc(standardWeeks);
}
