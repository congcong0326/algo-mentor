package org.congcong.algomentor.api.learningplan.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanTargetSize;

public record LearningPlanCreateDraftRequest(
    LearningPlanIntent intent,
    String objective,
    Integer targetProblemCount,
    LearningPlanLevel level,
    String programmingLanguage,
    LearningPlanDifficultyDistribution difficultyDistribution,
    List<String> topicPreferences,
    String additionalConstraints,
    Boolean personalizationEnabled
) {

  public LearningPlanBrief toBrief() {
    return toBrief(LearningPlanContentLocale.ZH_CN);
  }

  public LearningPlanBrief toBrief(LearningPlanContentLocale contentLocale) {
    return new LearningPlanBrief(
        intent,
        objective,
        targetProblemCount,
        LearningPlanTargetSize.durationWeeksFor(targetProblemCount),
        level,
        LearningPlanTargetSize.LEGACY_WEEKLY_HOURS,
        programmingLanguage,
        difficultyDistribution,
        topicPreferences,
        additionalConstraints,
        personalizationEnabled == null || personalizationEnabled,
        contentLocale);
  }

  /** 在全局 mapper 保持宽松时，严格拒绝未定义的 AI 创建字段。 */
  @JsonAnySetter
  public void rejectUnknownRequestField(String fieldName, Object ignoredValue) {
    throw new IllegalArgumentException("Learning plan create request does not accept field: " + fieldName);
  }
}
