package org.congcong.algomentor.mentor.application.learningplan.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanContentLocale;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDifficultyDistribution;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanIntent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLevel;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContext;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSource;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSourceOutcome;
import org.junit.jupiter.api.Test;

class LearningPlanDraftPromptBuilderTest {

  @Test
  void endsWithTheModelPlanningInputAsAUserMessage() throws Exception {
    LearningPlanDraftPromptBuilder builder = new LearningPlanDraftPromptBuilder(new LearningPlanLoadService());
    LearningPlanBrief brief = new LearningPlanBrief(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "准备 Java 后端算法面试",
        15,
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array"),
        "每周预留一次复盘。",
        true,
        LearningPlanContentLocale.EN_US);

    List<LlmMessage> messages = builder.build(
        brief,
        builder.snapshot(1L),
        LearningPlanPersonalizationSnapshot.disabled(Instant.EPOCH));

    assertThat(messages).extracting(LlmMessage::role)
        .containsExactly(LlmMessage.Role.SYSTEM, LlmMessage.Role.USER);
    var input = new ObjectMapper().readTree(messages.get(1).text());
    assertThat(input.path("targetProblemCount").asInt()).isEqualTo(15);
    assertThat(input.path("objective").asText()).isEqualTo(brief.objective());
    assertThat(input.has("durationWeeks")).isFalse();
    assertThat(input.has("weeklyHours")).isFalse();
    assertThat(input.has("personalizationEnabled")).isFalse();
  }

  @Test
  void insertsEnabledPersonalizationBeforeTheFinalBriefUserMessage() throws Exception {
    LearningPlanDraftPromptBuilder builder = new LearningPlanDraftPromptBuilder(new LearningPlanLoadService());
    LearningPlanBrief brief = new LearningPlanBrief(
        LearningPlanIntent.INTERVIEW_SPRINT,
        "本次目标必须覆盖历史参考",
        15,
        4,
        LearningPlanLevel.INTERMEDIATE,
        6,
        "Java",
        new LearningPlanDifficultyDistribution(35, 55, 10),
        List.of("Array"),
        null,
        true,
        LearningPlanContentLocale.EN_US);
    LearningPlanPersonalizationSnapshot personalization = new LearningPlanPersonalizationSnapshot(
        true,
        new LearningPlanPersonalizationContext(
            List.of("历史参考目标"), List.of(), List.of(), List.of(), null, null, Instant.EPOCH),
        "untrusted personalization reference",
        8,
        false,
        java.util.Map.of(
            LearningPlanPersonalizationSource.ACTIVE_CLAIMS, LearningPlanPersonalizationSourceOutcome.SUCCESS,
            LearningPlanPersonalizationSource.ABILITY_TAGS, LearningPlanPersonalizationSourceOutcome.EMPTY,
            LearningPlanPersonalizationSource.ACTIVE_PLAN, LearningPlanPersonalizationSourceOutcome.EMPTY,
            LearningPlanPersonalizationSource.REVIEW_LOAD, LearningPlanPersonalizationSourceOutcome.EMPTY));

    List<LlmMessage> messages = builder.build(brief, builder.snapshot(1L), personalization);

    assertThat(messages).extracting(LlmMessage::role)
        .containsExactly(LlmMessage.Role.SYSTEM, LlmMessage.Role.SYSTEM, LlmMessage.Role.USER);
    assertThat(messages.get(1).text()).isEqualTo("untrusted personalization reference");
    var input = new ObjectMapper().readTree(messages.get(2).text());
    assertThat(input.path("targetProblemCount").asInt()).isEqualTo(15);
    assertThat(input.has("durationWeeks")).isFalse();
    assertThat(input.has("weeklyHours")).isFalse();
  }
}
