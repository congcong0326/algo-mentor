import type {
  LearningPlanDraftPlan,
  LearningPlanLoadSummary,
  LearningPlanRhythmSettings,
  LearningPlanTrainingPackage,
  LearningPlanWeeklyBucket,
} from '../types/api';

export function getPlanLoadSummary(plan: LearningPlanDraftPlan): LearningPlanLoadSummary | undefined {
  if (plan.loadSummary) {
    return plan.loadSummary;
  }
  const value = plan.metadata?.loadSummary;
  return isLoadSummary(value) ? value : undefined;
}

export function getPlanWeeklyBuckets(plan: LearningPlanDraftPlan): LearningPlanWeeklyBucket[] {
  if (plan.weeklyBuckets?.length) {
    return plan.weeklyBuckets;
  }
  const value = plan.metadata?.weeklyBuckets;
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter(isWeeklyBucket);
}

export function getPlanNextTrainingPackage(plan: LearningPlanDraftPlan): LearningPlanTrainingPackage | undefined {
  if (plan.nextTrainingPackage) {
    return plan.nextTrainingPackage;
  }
  const value = plan.metadata?.nextTrainingPackage;
  return isTrainingPackage(value) ? value : undefined;
}

export function getPlanRhythmSettings(plan: LearningPlanDraftPlan): LearningPlanRhythmSettings {
  if (plan.rhythmSettings) {
    return plan.rhythmSettings;
  }
  const dailyProblemCount = numberFromMetadata(plan.metadata?.dailyProblemCount, 1);
  const trainingDaysPerWeek = numberFromMetadata(plan.metadata?.trainingDaysPerWeek, 5);
  const totalProblemCount = plan.phases.reduce((sum, phase) => sum + phase.problems.length, 0);
  const remainingProblemCount = totalProblemCount;
  const estimatedRemainingWeeks = remainingProblemCount === 0
    ? 0
    : Math.ceil(remainingProblemCount / Math.max(1, dailyProblemCount * trainingDaysPerWeek));
  return {
    dailyProblemCount,
    trainingDaysPerWeek,
    totalProblemCount,
    completedProblemCount: 0,
    skippedProblemCount: 0,
    remainingProblemCount,
    estimatedRemainingWeeks,
  };
}

function isLoadSummary(value: unknown): value is LearningPlanLoadSummary {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const item = value as Partial<LearningPlanLoadSummary>;
  return typeof item.plannedProblemCount === 'number'
    && typeof item.plannedLoadPoints === 'number'
    && typeof item.totalCapacityPoints === 'number'
    && typeof item.intensity === 'string';
}

function isWeeklyBucket(value: unknown): value is LearningPlanWeeklyBucket {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const item = value as Partial<LearningPlanWeeklyBucket>;
  return typeof item.weekIndex === 'number'
    && typeof item.plannedProblemCount === 'number'
    && typeof item.plannedLoadPoints === 'number'
    && Array.isArray(item.problemSlugs);
}

function isTrainingPackage(value: unknown): value is LearningPlanTrainingPackage {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const item = value as Partial<LearningPlanTrainingPackage>;
  return typeof item.weekIndex === 'number'
    && typeof item.newProblemCount === 'number'
    && typeof item.estimatedMinutes === 'number'
    && typeof item.reviewTask === 'string'
    && Array.isArray(item.priorityProblemSlugs);
}

function numberFromMetadata(value: unknown, fallback: number) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}
