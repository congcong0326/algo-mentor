package org.congcong.algomentor.api.learningplan.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationEvent;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftGenerationConstants;
import org.junit.jupiter.api.Test;

class LearningPlanGenerationRealtimeProtocolTest {

  private final LearningPlanGenerationRealtimeEventPayloadMapper mapper =
      new LearningPlanGenerationRealtimeEventPayloadMapper();

  @Test
  void projectsOnlyMinimalWhitelistedFields() {
    LearningPlanGenerationRealtimePayload progress = mapper.map(102L,
        new LearningPlanDraftGenerationEvent.WorkProgress("正在规划学习计划")).orElseThrow();
    LearningPlanGenerationRealtimePayload failed = mapper.map(102L,
        new LearningPlanDraftGenerationEvent.Failed("LEARNING_PLAN_GENERATION_FAILED")).orElseThrow();

    assertThat(progress.eventName()).isEqualTo(LearningPlanGenerationRealtimeProtocol.WORK_PROGRESS);
    assertThat(progress.data().fieldNames()).toIterable().containsExactlyInAnyOrder("draftId", "message");
    assertThat(failed.eventName()).isEqualTo(LearningPlanGenerationRealtimeProtocol.DRAFT_FAILED);
    assertThat(failed.data().fieldNames()).toIterable().containsExactlyInAnyOrder("draftId", "code");
    assertThat(progress.data().toString()).doesNotContain("metadata", "prompt", "provider", "userId");
  }

  @Test
  void acceptsOnlyContinuousPublicStreamIds() {
    assertThat(LearningPlanGenerationRealtimeCursor.normalizeAfter(null)).isEqualTo("0-0");
    assertThat(LearningPlanGenerationRealtimeCursor.normalizeAfter("12-0")).isEqualTo("12-0");
    assertThatThrownBy(() -> LearningPlanGenerationRealtimeCursor.normalizeAfter("12-1"))
        .isInstanceOf(LearningPlanGenerationRealtimeCursorInvalidException.class);
    assertThatThrownBy(() -> LearningPlanGenerationRealtimeCursor.normalizeAfter("12-$"))
        .isInstanceOf(LearningPlanGenerationRealtimeCursorInvalidException.class);
  }

  @Test
  void dropsUnsafeDomainEventsBeforeTheyCanReachTheRedisEnvelope() {
    assertThat(mapper.map(102L,
        new LearningPlanDraftGenerationEvent.WorkProgress("用户的完整输入不应写入 Redis"))).isEmpty();
    assertThat(mapper.map(102L,
        new LearningPlanDraftGenerationEvent.WorkToolStarted("unapproved_internal_tool"))).isEmpty();
    assertThat(mapper.map(102L,
        new LearningPlanDraftGenerationEvent.Failed("provider_exception_with_sensitive_detail"))).isEmpty();
    assertThat(mapper.map(0L, new LearningPlanDraftGenerationEvent.Failed(
        LearningPlanDraftGenerationConstants.GENERATION_FAILED_CODE))).isEmpty();
  }
}
