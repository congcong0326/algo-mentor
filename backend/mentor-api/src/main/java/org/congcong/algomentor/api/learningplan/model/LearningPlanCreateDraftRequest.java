package org.congcong.algomentor.api.learningplan.model;

import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyPreference;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftCommand;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;

public record LearningPlanCreateDraftRequest(
    LearningPlanIntent intent,
    String goal,
    Integer durationWeeks,
    LearningPlanLevel level,
    Integer weeklyHours,
    String programmingLanguage,
    LearningPlanDifficultyPreference difficultyPreference,
    Boolean interviewOriented,
    List<String> topicPreferences
) {

  public LearningPlanDraftCommand toCommand() {
    return new LearningPlanDraftCommand(
        intent,
        goal,
        durationWeeks,
        level,
        weeklyHours,
        programmingLanguage,
        difficultyPreference,
        interviewOriented,
        topicPreferences);
  }
}
