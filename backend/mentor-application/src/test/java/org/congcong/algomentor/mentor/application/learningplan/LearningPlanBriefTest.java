package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LearningPlanBriefTest {

  @Test
  void resolvesFrozenDefaultsForEveryIntentAndLocale() {
    Map<LearningPlanIntent, String> chinese = Map.of(
        LearningPlanIntent.PRACTICE_GOAL, "建立稳定的算法练习节奏",
        LearningPlanIntent.ABILITY_DIAGNOSIS, "识别并改善当前算法能力短板",
        LearningPlanIntent.INTERVIEW_SPRINT, "提升算法面试中的解题稳定性",
        LearningPlanIntent.TOPIC_BREAKTHROUGH, "系统掌握所选算法专题",
        LearningPlanIntent.MISTAKE_REVIEW, "通过错题复盘减少重复错误",
        LearningPlanIntent.LONG_TERM_LEARNING, "持续提升算法与数据结构能力");
    Map<LearningPlanIntent, String> english = Map.of(
        LearningPlanIntent.PRACTICE_GOAL, "Build a consistent algorithm practice routine",
        LearningPlanIntent.ABILITY_DIAGNOSIS, "Identify and improve current algorithm skill gaps",
        LearningPlanIntent.INTERVIEW_SPRINT, "Improve problem-solving consistency for coding interviews",
        LearningPlanIntent.TOPIC_BREAKTHROUGH, "Systematically master the selected algorithm topics",
        LearningPlanIntent.MISTAKE_REVIEW, "Reduce repeated mistakes through focused review",
        LearningPlanIntent.LONG_TERM_LEARNING, "Continuously improve algorithms and data structures");

    chinese.forEach((intent, objective) -> assertThat(brief(intent, null, null, null, LearningPlanContentLocale.ZH_CN).objective())
        .isEqualTo(objective));
    english.forEach((intent, objective) -> assertThat(brief(intent, null, null, null, LearningPlanContentLocale.EN_US).objective())
        .isEqualTo(objective));
  }

  @Test
  void normalizesIndependentFreeTextListsAndBooleanDefaults() {
    LearningPlanBrief brief = brief(
        LearningPlanIntent.TOPIC_BREAKTHROUGH,
        "  掌握动态规划  ",
        "  每周保留一天复盘  ",
        List.of(" Dynamic Programming ", "", "Dynamic Programming", "Graph "),
        null);

    assertThat(brief.objective()).isEqualTo("掌握动态规划");
    assertThat(brief.additionalConstraints()).isEqualTo("每周保留一天复盘");
    assertThat(brief.topicPreferences()).containsExactly("Dynamic Programming", "Graph");
    assertThat(brief.programmingLanguage()).isEqualTo("Java");
    assertThat(brief.personalizationEnabled()).isTrue();
    assertThat(brief.contentLocale()).isEqualTo(LearningPlanContentLocale.ZH_CN);
  }

  @Test
  void treatsBlankObjectiveAsMissingAndUsesDefaultWithoutConcatenatingOtherFields() {
    LearningPlanBrief brief = brief(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "  ",
        "  ",
        List.of(),
        LearningPlanContentLocale.EN_US);

    assertThat(brief.objective()).isEqualTo("Improve problem-solving consistency for coding interviews");
    assertThat(brief.additionalConstraints()).isNull();
  }

  private LearningPlanBrief brief(
      LearningPlanIntent intent,
      String objective,
      String additionalConstraints,
      List<String> topics,
      LearningPlanContentLocale locale
  ) {
    return new LearningPlanBrief(
        intent,
        objective,
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        " Java ",
        new LearningPlanDifficultyDistribution(25, 55, 20),
        topics,
        additionalConstraints,
        true,
        locale);
  }
}
