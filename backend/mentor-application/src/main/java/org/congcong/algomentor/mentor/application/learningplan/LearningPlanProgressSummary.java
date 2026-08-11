package org.congcong.algomentor.mentor.application.learningplan;

import java.util.HashSet;
import java.util.Set;

public record LearningPlanProgressSummary(
    int totalProblemCount,
    int completedProblemCount,
    double progressPercent
) {

  public LearningPlanProgressSummary {
    if (totalProblemCount < 0 || completedProblemCount < 0 || completedProblemCount > totalProblemCount) {
      throw new IllegalArgumentException("learning plan progress counts are invalid");
    }
    if (Double.isNaN(progressPercent) || Double.isInfinite(progressPercent)
        || progressPercent < 0D || progressPercent > 100D) {
      throw new IllegalArgumentException("learning plan progress percent must be between 0 and 100");
    }
  }

  public static LearningPlanProgressSummary fromCounts(int totalProblemCount, int completedProblemCount) {
    double percent = totalProblemCount == 0
        ? 0D
        : Math.round((double) completedProblemCount * 1000D / totalProblemCount) / 10D;
    return new LearningPlanProgressSummary(totalProblemCount, completedProblemCount, percent);
  }

  public static LearningPlanProgressSummary notStarted(LearningPlanDraftPlan plan) {
    if (plan == null) {
      return fromCounts(0, 0);
    }
    Set<ProblemKey> problems = new HashSet<>();
    for (LearningPlanPhaseDraft phase : plan.phases()) {
      for (LearningPlanProblemDraft problem : phase.problems()) {
        problems.add(new ProblemKey(phase.phaseIndex(), problem.slug()));
      }
    }
    return fromCounts(problems.size(), 0);
  }

  private record ProblemKey(int phaseIndex, String problemSlug) {
  }
}
