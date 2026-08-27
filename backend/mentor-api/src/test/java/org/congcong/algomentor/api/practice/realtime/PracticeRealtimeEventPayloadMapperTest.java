package org.congcong.algomentor.api.practice.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.llm.core.metadata.LlmMetadataKeys;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.junit.jupiter.api.Test;

class PracticeRealtimeEventPayloadMapperTest {

  private final PracticeRealtimeEventPayloadMapper mapper = new PracticeRealtimeEventPayloadMapper();

  @Test
  void filtersInternalLifecycleEventsAndMetadata() {
    assertThat(mapper.map(new AgentStreamEvent.AgentRunStart(
        "run-1", "practice", 4, Map.of("userId", 42, "aiAdmission", "locked"))))
        .isEmpty();
    assertThat(mapper.map(new AgentStreamEvent.Llm(new LlmStreamEvent.MessageEnd(
        LlmFinishReason.STOP, Map.of("promptSnapshot", "secret")))))
        .isEmpty();
  }

  @Test
  void projectsReviewResultToTheExplicitWhitelist() {
    ObjectNode result = JsonNodeFactory.instance.objectNode()
        .put("type", "practice_code_review_submitted")
        .put("status", "SAVED")
        .put("totalScore", 8.0)
        .put("passed", true)
        .put("failureCode", "NONE")
        .put("reviewId", 99)
        .put("userId", 42);

    PracticeRealtimeEventPayload payload = mapper.map(new AgentStreamEvent.AgentToolEnd(
        "run-1", 1, "call-1", "submit_practice_code_review", result)).orElseThrow();

    assertThat(payload.eventName()).isEqualTo("agent_tool_end");
    assertThat(payload.data().path("result").fieldNames()).toIterable()
        .containsExactlyInAnyOrder("type", "status", "totalScore", "passed", "failureCode");
    assertThat(payload.data().toString()).doesNotContain("reviewId", "userId");
  }

  @Test
  void hidesUnknownToolsAndProjectsTheActualErrorReason() {
    ObjectNode result = JsonNodeFactory.instance.objectNode().put("source", "raw").put("secret", "value");
    assertThat(mapper.map(new AgentStreamEvent.AgentToolEnd(
        "run-1", 1, "call-1", "read_practice_submission_detail", result))).isEmpty();

    PracticeRealtimeEventPayload error = mapper.map(new AgentStreamEvent.AgentError(
        "run-1", new AgentException(AgentErrorCode.LLM_STREAM_FAILED,
        "Our servers are currently overloaded. Please try again later.", true,
        Map.of(LlmMetadataKeys.ERROR_CODE, "server_is_overloaded", "cause", "sensitive"), null))).orElseThrow();
    assertThat(error.data().path("code").asText()).isEqualTo("server_is_overloaded");
    assertThat(error.data().path("message").asText())
        .isEqualTo("Our servers are currently overloaded. Please try again later.");
    assertThat(error.data().path("retryable").asBoolean()).isTrue();
    assertThat(error.data().toString()).doesNotContain("sensitive", "cause");
  }

  @Test
  void projectsOnlyTheDeclaredScalarTypesForEachPublicToolResult() {
    ObjectNode review = JsonNodeFactory.instance.objectNode()
        .put("type", "practice_code_review_submitted")
        .put("status", "SAVED")
        .put("totalScore", "not-a-number")
        .put("passed", "true")
        .put("failureCode", 400);
    ObjectNode coachSummary = JsonNodeFactory.instance.objectNode()
        .put("type", "current_problem_coach_summary_proposed")
        .put("status", "PROPOSED")
        .put("proposalId", 42);
    coachSummary.putArray("summaryMarkdown").add("internal");
    coachSummary.put("operation", true);
    ObjectNode learnerProfile = JsonNodeFactory.instance.objectNode();
    learnerProfile.putObject("type").put("internal", "value");
    learnerProfile.put("status", 1);

    assertThat(toolResult("submit_practice_code_review", review).fieldNames()).toIterable()
        .containsExactlyInAnyOrder("type", "status");
    assertThat(toolResult("propose_current_problem_coach_summary", coachSummary).fieldNames()).toIterable()
        .containsExactlyInAnyOrder("type", "status");
    assertThat(toolResult("update_learner_declared_profile", learnerProfile).fieldNames()).toIterable().isEmpty();
  }

  private ObjectNode toolResult(String toolName, ObjectNode result) {
    return (ObjectNode) mapper.map(new AgentStreamEvent.AgentToolEnd(
        "run-1", 1, "call-1", toolName, result)).orElseThrow().data().path("result");
  }
}
