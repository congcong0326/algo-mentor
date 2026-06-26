package org.congcong.algomentor.mentor.application.learningplan;

import java.util.ArrayList;
import java.util.List;

public class LearningPlanDraftValidator {

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
    int expectedPhaseCount = expectedPhaseCount(plan.durationWeeks());
    int actualPhaseCount = plan.phases().size();
    if (Math.abs(expectedPhaseCount - actualPhaseCount) > 1) {
      throw new LearningPlanException(
          "LEARNING_PLAN_DRAFT_INVALID",
          "api.error.LEARNING_PLAN_DRAFT_INVALID.phase_count",
          "学习计划阶段数与周期不匹配。");
    }
    int totalWeeks = plan.phases().stream().mapToInt(LearningPlanPhaseDraft::durationWeeks).sum();
    if (totalWeeks != plan.durationWeeks()) {
      throw new LearningPlanException(
          "LEARNING_PLAN_DRAFT_INVALID",
          "api.error.LEARNING_PLAN_DRAFT_INVALID.duration_sum",
          "学习计划阶段周数之和必须等于总周期。");
    }
    for (LearningPlanPhaseDraft phase : plan.phases()) {
      if (phase.problems().size() > 5) {
        throw new LearningPlanException(
            "LEARNING_PLAN_DRAFT_INVALID",
            "api.error.LEARNING_PLAN_DRAFT_INVALID.too_many_problems",
            "每个阶段最多推荐 5 道题。");
      }
    }
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
