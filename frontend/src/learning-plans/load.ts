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
  return plan.weeklyBuckets ?? [];
}

export function getPlanNextTrainingPackage(plan: LearningPlanDraftPlan): LearningPlanTrainingPackage | undefined {
  return plan.nextTrainingPackage;
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

export function getPlanTargetProblemCount(plan: LearningPlanDraftPlan): number | undefined {
  const value = plan.metadata?.targetProblemCount;
  return typeof value === 'number' && Number.isInteger(value) && value > 0 ? value : undefined;
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

function numberFromMetadata(value: unknown, fallback: number) {
  return typeof value === 'number' && Number.isFinite(value) ? value : fallback;
}
