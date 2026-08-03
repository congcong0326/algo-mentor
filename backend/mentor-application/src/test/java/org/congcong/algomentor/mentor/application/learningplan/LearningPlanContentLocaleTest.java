package org.congcong.algomentor.mentor.application.learningplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class LearningPlanContentLocaleTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void briefAndNewPlanWithoutContentLocaleDefaultToChinese() throws Exception {
    LearningPlanBrief brief = objectMapper.readValue("""
        {
          "intent": "INTERVIEW_SPRINT",
          "objective": "准备算法面试",
          "durationWeeks": 4,
          "level": "INTERMEDIATE",
          "weeklyHours": 6,
          "difficultyDistribution": {
            "easyPercent": 35,
            "mediumPercent": 55,
            "hardPercent": 10
          },
          "interviewOriented": true,
          "topicPreferences": ["Array"]
        }
        """, LearningPlanBrief.class);
    LearningPlanDraftPlan plan = objectMapper.readValue("""
        {
          "title": "旧计划",
          "summary": "旧摘要",
          "intent": "INTERVIEW_SPRINT",
          "objective": "准备算法面试",
          "durationWeeks": 4,
          "level": "INTERMEDIATE",
          "weeklyHours": 6,
          "difficultyDistribution": {
            "easyPercent": 35,
            "mediumPercent": 55,
            "hardPercent": 10
          },
          "interviewOriented": true,
          "topicPreferences": ["Array"],
          "additionalConstraints": null,
          "phases": [],
          "metadata": {}
        }
        """, LearningPlanDraftPlan.class);

    assertThat(brief.contentLocale()).isEqualTo(LearningPlanContentLocale.ZH_CN);
    assertThat(plan.contentLocale()).isEqualTo(LearningPlanContentLocale.ZH_CN);
  }

  @Test
  void acceptLanguageOnlySelectsTheTwoSupportedLocales() {
    assertThat(LearningPlanContentLocale.fromAcceptLanguage("en-US,en;q=0.9,zh-CN;q=0.8"))
        .isEqualTo(LearningPlanContentLocale.EN_US);
    assertThat(LearningPlanContentLocale.fromAcceptLanguage("en-GB"))
        .isEqualTo(LearningPlanContentLocale.ZH_CN);
    assertThat(LearningPlanContentLocale.fromAcceptLanguage("fr-FR,es;q=0.8"))
        .isEqualTo(LearningPlanContentLocale.ZH_CN);
    assertThat(LearningPlanContentLocale.fromAcceptLanguage("invalid;q=oops"))
        .isEqualTo(LearningPlanContentLocale.ZH_CN);
  }
}
