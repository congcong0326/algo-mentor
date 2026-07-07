import type {
  LearningPlanDraftPlan,
  LearningPlanLoadSummary,
  LearningPlanRhythmMode,
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

export function getPlanRhythmMode(plan: LearningPlanDraftPlan): LearningPlanRhythmMode {
  const value = plan.metadata?.rhythmMode;
  return value === 'RELAXED' || value === 'SPRINT' || value === 'RECOMMENDED' ? value : 'RECOMMENDED';
}

export function trainingDaysPerWeek(mode: LearningPlanRhythmMode): [number, number] {
  if (mode === 'RELAXED') {
    return [4, 4];
  }
  if (mode === 'SPRINT') {
    return [6, 7];
  }
  return [5, 5];
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
