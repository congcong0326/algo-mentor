import type { LearningPlanCreationPolicyContent } from '../../types/api';

export const LEARNING_PLAN_CREATION_POLICY_TYPE = 'learning-plan.creation.v1';
export const DEFAULT_MAX_SAVED_PLANS = 30;
export const DEFAULT_DAILY_DRAFT_CREATION_LIMIT = 5;
export const DEFAULT_DRAFT_RETENTION_DAYS = 14;
export const MAX_LEARNING_PLAN_POLICY_LIMIT = 1_000;
export const MAX_DRAFT_RETENTION_DAYS = 365;

export function defaultLearningPlanCreationPolicyContent(): LearningPlanCreationPolicyContent {
  return {
    maxSavedPlans: DEFAULT_MAX_SAVED_PLANS,
    dailyDraftCreationLimit: DEFAULT_DAILY_DRAFT_CREATION_LIMIT,
    draftRetentionDays: DEFAULT_DRAFT_RETENTION_DAYS,
  };
}

export function isPolicyIntegerInRange(value: string, minimum: number, maximum: number): boolean {
  const parsed = Number(value);
  return Number.isSafeInteger(parsed) && parsed >= minimum && parsed <= maximum;
}
