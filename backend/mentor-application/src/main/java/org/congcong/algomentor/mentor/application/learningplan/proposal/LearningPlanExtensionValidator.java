package org.congcong.algomentor.mentor.application.learningplan.proposal;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;

public class LearningPlanExtensionValidator {

  private final LearningPlanProblemCatalog problemCatalog;

  public LearningPlanExtensionValidator(LearningPlanProblemCatalog problemCatalog) {
    this.problemCatalog = Objects.requireNonNull(problemCatalog, "problemCatalog");
  }

  public void validate(
      LearningPlanExtensionDraft extension,
      LearningPlan currentPlan,
      List<PracticeProgress> progress) {
    if (extension == null || extension.newPhases().isEmpty()) {
      throw invalid("扩展提案至少需要一个新增阶段。");
    }

    int currentMaxPhaseIndex = maxPhaseIndex(currentPlan);
    int previousPhaseIndex = currentMaxPhaseIndex;
    Set<Integer> extensionPhaseIndexes = new HashSet<>();
    Set<String> currentProblemSlugs = problemSlugs(currentPlan);
    Set<String> extensionProblemSlugs = new HashSet<>();

    for (LearningPlanPhaseDraft phase : extension.newPhases()) {
      if (!extensionPhaseIndexes.add(phase.phaseIndex())) {
        throw invalid("扩展阶段编号不能重复。");
      }
      if (phase.phaseIndex() <= currentMaxPhaseIndex || phase.phaseIndex() <= previousPhaseIndex) {
        throw invalid("扩展阶段编号必须追加在当前计划之后并递增。");
      }
      previousPhaseIndex = phase.phaseIndex();

      if (phase.problems().size() > 5) {
        throw invalid("每个扩展阶段最多推荐 5 道题。");
      }
      for (LearningPlanProblemDraft problem : phase.problems()) {
        String slug = problem.slug();
        if (currentProblemSlugs.contains(slug)) {
          throw invalid("扩展提案不能重复推荐已有计划题目。");
        }
        if (!extensionProblemSlugs.add(slug)) {
          throw invalid("扩展提案内部不能重复推荐同一道题。");
        }
        if (problemCatalog.findBySlug(slug).isEmpty()) {
          throw invalid("扩展提案包含本地题库不存在的题目。");
        }
      }
    }
  }

  private static int maxPhaseIndex(LearningPlan plan) {
    if (plan == null || plan.plan() == null) {
      return 0;
    }
    return plan.plan().phases().stream()
        .mapToInt(LearningPlanPhaseDraft::phaseIndex)
        .max()
        .orElse(0);
  }

  private static Set<String> problemSlugs(LearningPlan plan) {
    Set<String> slugs = new HashSet<>();
    if (plan == null || plan.plan() == null) {
      return slugs;
    }
    for (LearningPlanPhaseDraft phase : plan.plan().phases()) {
      for (LearningPlanProblemDraft problem : phase.problems()) {
        slugs.add(problem.slug());
      }
    }
    return slugs;
  }

  private static LearningPlanException invalid(String message) {
    return new LearningPlanException("LEARNING_PLAN_EXTENSION_INVALID", message);
  }
}
