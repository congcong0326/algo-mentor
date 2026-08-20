package org.congcong.algomentor.api.learningplan.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionGenerationEvent;
import org.junit.jupiter.api.Test;

class LearningPlanDraftRevisionRealtimeProtocolTest {

  private final LearningPlanDraftRevisionRealtimeEventPayloadMapper mapper =
      new LearningPlanDraftRevisionRealtimeEventPayloadMapper();

  @Test
  void projectsOnlyMinimalRevisionCompletionPayload() {
    LearningPlanDraftRevisionRealtimeEventPayloadMapper.Payload payload = mapper.map(
        102L, 203L, new LearningPlanDraftRevisionGenerationEvent.Completed()).orElseThrow();

    assertThat(payload.eventName()).isEqualTo(LearningPlanDraftRevisionRealtimeProtocol.REVISION_COMPLETED);
    assertThat(payload.data().fieldNames()).toIterable()
        .containsExactlyInAnyOrder("draftId", "revisionId");
    assertThat(payload.data().toString()).doesNotContain(
        "instruction", "plan", "artifact", "runId", "prompt", "metadata", "userId");
  }

  @Test
  void projectsOnlyWhitelistedFailuresAndFixedProgress() {
    assertThat(mapper.map(102L, 203L, new LearningPlanDraftRevisionGenerationEvent.WorkStarted()))
        .map(LearningPlanDraftRevisionRealtimeEventPayloadMapper.Payload::eventName)
        .contains(LearningPlanDraftRevisionRealtimeProtocol.WORK_START);
    assertThat(mapper.map(102L, 203L, new LearningPlanDraftRevisionGenerationEvent.Failed(
        "untrusted_provider_error"))).isEmpty();
    assertThat(mapper.map(0L, 203L, new LearningPlanDraftRevisionGenerationEvent.Completed())).isEmpty();
  }
}
