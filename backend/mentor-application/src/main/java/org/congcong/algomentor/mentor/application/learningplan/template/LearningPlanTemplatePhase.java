package org.congcong.algomentor.mentor.application.learningplan.template;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;

public record LearningPlanTemplatePhase(
    Long id,
    int phaseIndex,
    String title,
    String titleEn,
    int durationWeeks,
    String focus,
    String focusEn,
    List<String> objectives,
    List<String> objectivesEn,
    List<String> recommendedTags,
    List<String> acceptanceCriteria,
    List<String> acceptanceCriteriaEn,
    String reviewAdvice,
    String reviewAdviceEn,
    List<LearningPlanTemplateProblemRef> problemRefs
) {

  public LearningPlanTemplatePhase {
    objectives = objectives == null ? List.of() : List.copyOf(objectives);
    objectivesEn = objectivesEn == null ? List.of() : List.copyOf(objectivesEn);
    recommendedTags = recommendedTags == null ? List.of() : List.copyOf(recommendedTags);
    acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
    acceptanceCriteriaEn = acceptanceCriteriaEn == null ? List.of() : List.copyOf(acceptanceCriteriaEn);
    problemRefs = problemRefs == null ? List.of() : List.copyOf(problemRefs);
  }

  public LearningPlanTemplatePhase(
      Long id,
      int phaseIndex,
      String title,
      int durationWeeks,
      String focus,
      List<String> objectives,
      List<String> recommendedTags,
      List<String> acceptanceCriteria,
      String reviewAdvice,
      List<LearningPlanTemplateProblemRef> problemRefs
  ) {
    this(
        id,
        phaseIndex,
        title,
        null,
        durationWeeks,
        focus,
        null,
        objectives,
        List.of(),
        recommendedTags,
        acceptanceCriteria,
        List.of(),
        reviewAdvice,
        null,
        problemRefs);
  }

  public String title(LearningPlanContentLocale locale) {
    return locale == LearningPlanContentLocale.EN_US ? titleEn : title;
  }

  public String focus(LearningPlanContentLocale locale) {
    return locale == LearningPlanContentLocale.EN_US ? focusEn : focus;
  }

  public List<String> objectives(LearningPlanContentLocale locale) {
    return locale == LearningPlanContentLocale.EN_US ? objectivesEn : objectives;
  }

  public List<String> acceptanceCriteria(LearningPlanContentLocale locale) {
    return locale == LearningPlanContentLocale.EN_US ? acceptanceCriteriaEn : acceptanceCriteria;
  }

  public String reviewAdvice(LearningPlanContentLocale locale) {
    return locale == LearningPlanContentLocale.EN_US ? reviewAdviceEn : reviewAdvice;
  }

  public LearningPlanTemplatePhase withProblemRefs(List<LearningPlanTemplateProblemRef> nextProblemRefs) {
    return new LearningPlanTemplatePhase(
        id,
        phaseIndex,
        title,
        titleEn,
        durationWeeks,
        focus,
        focusEn,
        objectives,
        objectivesEn,
        recommendedTags,
        acceptanceCriteria,
        acceptanceCriteriaEn,
        reviewAdvice,
        reviewAdviceEn,
        nextProblemRefs);
  }
}
