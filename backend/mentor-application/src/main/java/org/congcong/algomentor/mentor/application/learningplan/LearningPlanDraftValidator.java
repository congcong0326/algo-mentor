package org.congcong.algomentor.mentor.application.learningplan;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LearningPlanDraftValidator {

  private static final int MAX_AI_PROBLEMS_PER_PHASE = 5;

  public List<String> missingRequiredFields(LearningPlanDraftCommand command) {
    List<String> missingFields = new ArrayList<>();
    if (command.intent() == null) {
      missingFields.add("intent");
    }
    if (command.goal() == null) {
      missingFields.add("goal");
    }
    if (command.durationWeeks() == null || command.durationWeeks() < 1) {
      missingFields.add("durationWeeks");
    }
    if (command.level() == null) {
      missingFields.add("level");
    }
    if (command.weeklyHours() == null || command.weeklyHours() < 1) {
      missingFields.add("weeklyHours");
    }
    return missingFields;
  }

  public void validateGeneratedPlan(LearningPlanDraftPlan plan) {
    validateCommonPlanShape(plan);
    validateExpectedAiPhaseCount(plan);
    validateAiProblemLimit(plan);
  }

  public void validateConfirmablePlan(LearningPlanDraftPlan plan) {
    if (isTemplatePlan(plan)) {
      validateTemplatePlan(plan);
      return;
    }
    validateGeneratedPlan(plan);
  }

  public void validateTemplatePlan(LearningPlanDraftPlan plan) {
    validateCommonPlanShape(plan);
    int expectedMatchedProblemCount = templateMatchedProblemCount(plan);
    if (expectedMatchedProblemCount >= 0 && actualProblemCount(plan) != expectedMatchedProblemCount) {
      throw new LearningPlanException(
          "LEARNING_PLAN_DRAFT_INVALID",
          "api.error.LEARNING_PLAN_DRAFT_INVALID.template_problem_count",
          "模板学习计划草案必须包含所有本地匹配题。");
    }
  }

  public boolean isTemplatePlan(LearningPlanDraftPlan plan) {
    if (plan == null || plan.metadata() == null) {
      return false;
    }
    return LearningPlanDraftMetadataKeys.DRAFT_SOURCE_TEMPLATE.equals(
        plan.metadata().get(LearningPlanDraftMetadataKeys.DRAFT_SOURCE));
  }

  private void validateCommonPlanShape(LearningPlanDraftPlan plan) {
    if (plan == null) {
      throw new LearningPlanException(
          "LEARNING_PLAN_DRAFT_INVALID",
          "api.error.LEARNING_PLAN_DRAFT_INVALID.empty",
          "学习计划草案为空。");
    }
    if (plan.phases().isEmpty()) {
      throw new LearningPlanException(
          "LEARNING_PLAN_DRAFT_INVALID",
          "api.error.LEARNING_PLAN_DRAFT_INVALID.no_phases",
          "学习计划至少需要一个阶段。");
    }
    for (int index = 0; index < plan.phases().size(); index++) {
      LearningPlanPhaseDraft phase = plan.phases().get(index);
      if (phase.phaseIndex() != index + 1) {
        throw new LearningPlanException(
            "LEARNING_PLAN_DRAFT_INVALID",
            "api.error.LEARNING_PLAN_DRAFT_INVALID.phase_index",
            "学习计划阶段序号必须连续。");
      }
      if (phase.durationWeeks() < 1) {
        throw new LearningPlanException(
            "LEARNING_PLAN_DRAFT_INVALID",
            "api.error.LEARNING_PLAN_DRAFT_INVALID.phase_duration",
            "学习计划阶段周数必须大于 0。");
      }
    }
    int totalWeeks = plan.phases().stream().mapToInt(LearningPlanPhaseDraft::durationWeeks).sum();
    if (totalWeeks != plan.durationWeeks()) {
      throw new LearningPlanException(
          "LEARNING_PLAN_DRAFT_INVALID",
          "api.error.LEARNING_PLAN_DRAFT_INVALID.duration_sum",
          "学习计划阶段周数之和必须等于总周期。");
    }
  }

  private void validateExpectedAiPhaseCount(LearningPlanDraftPlan plan) {
    int expectedPhaseCount = expectedPhaseCount(plan.durationWeeks());
    int actualPhaseCount = plan.phases().size();
    if (Math.abs(expectedPhaseCount - actualPhaseCount) > 1) {
      throw new LearningPlanException(
          "LEARNING_PLAN_DRAFT_INVALID",
          "api.error.LEARNING_PLAN_DRAFT_INVALID.phase_count",
          "学习计划阶段数与周期不匹配。");
    }
  }

  private void validateAiProblemLimit(LearningPlanDraftPlan plan) {
    for (LearningPlanPhaseDraft phase : plan.phases()) {
      if (phase.problems().size() > MAX_AI_PROBLEMS_PER_PHASE) {
        throw new LearningPlanException(
            "LEARNING_PLAN_DRAFT_INVALID",
            "api.error.LEARNING_PLAN_DRAFT_INVALID.too_many_problems",
            "每个阶段最多推荐 5 道题。");
      }
    }
  }

  private int actualProblemCount(LearningPlanDraftPlan plan) {
    return plan.phases().stream().mapToInt(phase -> phase.problems().size()).sum();
  }

  private int templateMatchedProblemCount(LearningPlanDraftPlan plan) {
    Object templateMetadata = plan.metadata().get(LearningPlanDraftMetadataKeys.TEMPLATE);
    if (!(templateMetadata instanceof Map<?, ?> values)) {
      return -1;
    }
    Object count = values.get(LearningPlanDraftMetadataKeys.MATCHED_PROBLEM_COUNT);
    if (count instanceof Number number) {
      return number.intValue();
    }
    if (count instanceof String text && !text.isBlank()) {
      try {
        return Integer.parseInt(text);
      } catch (NumberFormatException ignored) {
        return -1;
      }
    }
    return -1;
  }

  public int expectedPhaseCount(int durationWeeks) {
    if (durationWeeks <= 1) {
      return 1;
    }
    if (durationWeeks <= 2) {
      return 2;
    }
    if (durationWeeks <= 6) {
      return 3;
    }
    return 4;
  }
}
