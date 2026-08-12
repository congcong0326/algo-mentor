import type { LearningPlanAiRevisionPolicyContent } from '../../types/api';

export const LEARNING_PLAN_AI_REVISION_POLICY_TYPE = 'learning-plan.ai-revision-access.v1';

export function defaultLearningPlanAiRevisionPolicyContent(): LearningPlanAiRevisionPolicyContent {
  return {
    templateDraftRevisionEnabled: false,
    savedPlanRevisionEnabled: false,
    personalizedDraftRevisionEnabled: true,
  };
}
