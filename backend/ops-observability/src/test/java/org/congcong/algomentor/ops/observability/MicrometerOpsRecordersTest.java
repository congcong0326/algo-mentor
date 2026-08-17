package org.congcong.algomentor.ops.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class MicrometerOpsRecordersTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final SseOpsRecorder sse = MicrometerOpsRecorders.sse(registry);
  private final AgentOpsRecorder agent = MicrometerOpsRecorders.agent(registry);
  private final LearningOpsRecorder learning = MicrometerOpsRecorders.learning(registry);
  private final PracticeRealtimeOpsRecorder practiceRealtime = MicrometerOpsRecorders.practiceRealtime(registry);

  @Test
  void recordsSseCountersAndActiveGauge() {
    sse.opened(SseStreamType.PRACTICE_MESSAGE);
    sse.completed(SseStreamType.PRACTICE_MESSAGE);
    sse.failed(SseStreamType.PRACTICE_MESSAGE, SseFailureType.SEND_FAILURE);
    sse.timeout(SseStreamType.PRACTICE_MESSAGE);
    sse.clientDisconnected(SseStreamType.PRACTICE_MESSAGE);

    assertThat(counter(OpsMetricNames.SSE_CONNECTIONS_OPENED,
        "stream_type", "practice_message")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.SSE_CONNECTIONS_COMPLETED,
        "stream_type", "practice_message")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.SSE_CONNECTIONS_FAILED,
        "stream_type", "practice_message",
        "failure_type", "send_failure")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.SSE_CONNECTIONS_TIMEOUT,
        "stream_type", "practice_message")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.SSE_CONNECTIONS_CLIENT_DISCONNECTED,
        "stream_type", "practice_message")).isEqualTo(1.0);
    assertThat(registry.get(OpsMetricNames.SSE_CONNECTIONS_ACTIVE)
        .tag("stream_type", "practice_message")
        .gauge()
        .value()).isEqualTo(0.0);
  }

  @Test
  void activeSseGaugeDropsOnEveryTerminalConnectionOutcome() {
    sse.opened(SseStreamType.PRACTICE_MESSAGE);
    sse.opened(SseStreamType.PRACTICE_MESSAGE);
    sse.completed(SseStreamType.PRACTICE_MESSAGE);
    sse.timeout(SseStreamType.PRACTICE_MESSAGE);
    sse.clientDisconnected(SseStreamType.PRACTICE_MESSAGE);

    assertThat(registry.get(OpsMetricNames.SSE_CONNECTIONS_ACTIVE)
        .tag("stream_type", "practice_message")
        .gauge()
        .value()).isEqualTo(0.0);

    sse.failed(SseStreamType.PRACTICE_MESSAGE, SseFailureType.TIMEOUT);
    sse.failed(SseStreamType.PRACTICE_MESSAGE, SseFailureType.SEND_FAILURE);

    assertThat(registry.get(OpsMetricNames.SSE_CONNECTIONS_ACTIVE)
        .tag("stream_type", "practice_message")
        .gauge()
        .value()).isEqualTo(0.0);
  }

  @Test
  void activeSseGaugeIsSharedAcrossRecorderInstancesForSameRegistry() {
    SseOpsRecorder first = MicrometerOpsRecorders.sse(registry);
    SseOpsRecorder second = MicrometerOpsRecorders.sse(registry);

    first.opened(SseStreamType.PRACTICE_MESSAGE);
    assertThat(activeGauge("practice_message")).isEqualTo(1.0);

    second.opened(SseStreamType.PRACTICE_MESSAGE);
    assertThat(activeGauge("practice_message")).isEqualTo(2.0);

    first.completed(SseStreamType.PRACTICE_MESSAGE);
    assertThat(activeGauge("practice_message")).isEqualTo(1.0);

    second.completed(SseStreamType.PRACTICE_MESSAGE);
    assertThat(activeGauge("practice_message")).isEqualTo(0.0);
  }

  @Test
  void recordsAgentAndLearningCounters() {
    agent.runStarted(AgentOpsSource.AGENT_CONVERSATION);
    agent.runCompleted(AgentOpsSource.AGENT_CONVERSATION);
    agent.runFailed(AgentOpsSource.AGENT_CONVERSATION);
    agent.toolPermissionDecision("allow");
    agent.toolExecution("submit_practice_code_review", OpsStatus.FAILED);
    learning.learningPlanDraft(OpsStatus.FAILED);
    learning.practiceMessageStream(OpsStatus.COMPLETED);
    learning.practiceCodeReview(OpsStatus.UNREVIEWABLE);

    assertThat(counter(OpsMetricNames.AGENT_RUNS,
        "source", "agent_conversation",
        "status", "started")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.AGENT_RUNS,
        "source", "agent_conversation",
        "status", "completed")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.AGENT_RUNS,
        "source", "agent_conversation",
        "status", "failed")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.AGENT_TOOL_PERMISSION_DECISIONS,
        "decision", "allow")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.AGENT_TOOL_EXECUTIONS,
        "tool_name", "submit_practice_code_review",
        "status", "failed")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.LEARNING_PLAN_DRAFT_GENERATIONS,
        "status", "failed")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.PRACTICE_MESSAGE_STREAMS,
        "status", "completed")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.PRACTICE_CODE_REVIEWS,
        "status", "unreviewable")).isEqualTo(1.0);
  }

  @Test
  void normalizesToolPermissionDecisionTagsToLowCardinalityValues() {
    agent.toolPermissionDecision("ALLOW");
    agent.toolPermissionDecision(" deny ");
    agent.toolPermissionDecision("Timeout");
    agent.toolPermissionDecision("canceled");
    agent.toolPermissionDecision("cancelled");
    agent.toolPermissionDecision("please allow this specific request");
    agent.toolPermissionDecision("");
    agent.toolPermissionDecision(null);

    assertThat(counter(OpsMetricNames.AGENT_TOOL_PERMISSION_DECISIONS,
        "decision", "allow")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.AGENT_TOOL_PERMISSION_DECISIONS,
        "decision", "deny")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.AGENT_TOOL_PERMISSION_DECISIONS,
        "decision", "timeout")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.AGENT_TOOL_PERMISSION_DECISIONS,
        "decision", "cancelled")).isEqualTo(2.0);
    assertThat(counter(OpsMetricNames.AGENT_TOOL_PERMISSION_DECISIONS,
        "decision", "unknown")).isEqualTo(3.0);
    assertThat(registry.find(OpsMetricNames.AGENT_TOOL_PERMISSION_DECISIONS)
        .tag("decision", "please allow this specific request")
        .counter()).isNull();
  }

  @Test
  void noopFactoriesReturnReusableRecorders() {
    assertThat(NoopOpsRecorders.sse()).isSameAs(NoopOpsRecorders.sse());
    assertThat(NoopOpsRecorders.agent()).isSameAs(NoopOpsRecorders.agent());
    assertThat(NoopOpsRecorders.learning()).isSameAs(NoopOpsRecorders.learning());
    assertThat(NoopOpsRecorders.practiceRealtime()).isSameAs(NoopOpsRecorders.practiceRealtime());
  }

  @Test
  void recordsPracticeRealtimeRedisOperationsWithLowCardinalityTags() {
    practiceRealtime.redisOperation(
        PracticeRealtimeOperation.APPEND, PracticeRealtimeOutcome.SUCCESS, java.time.Duration.ofMillis(12));
    practiceRealtime.redisOperation(
        PracticeRealtimeOperation.READ, PracticeRealtimeOutcome.FAILURE, java.time.Duration.ofMillis(8));
    practiceRealtime.redisOperation(
        PracticeRealtimeOperation.CONNECT, PracticeRealtimeOutcome.SUCCESS, java.time.Duration.ofMillis(3));
    practiceRealtime.redisOperation(
        PracticeRealtimeOperation.RECONNECT, PracticeRealtimeOutcome.FAILURE, java.time.Duration.ofMillis(9));

    assertThat(counter(OpsMetricNames.PRACTICE_REALTIME_REDIS_OPERATIONS,
        "operation", "append", "outcome", "success")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.PRACTICE_REALTIME_REDIS_OPERATIONS,
        "operation", "read", "outcome", "failure")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.PRACTICE_REALTIME_REDIS_OPERATIONS,
        "operation", "connect", "outcome", "success")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.PRACTICE_REALTIME_REDIS_OPERATIONS,
        "operation", "reconnect", "outcome", "failure")).isEqualTo(1.0);
    assertThat(registry.get(OpsMetricNames.PRACTICE_REALTIME_REDIS_OPERATION_DURATION)
        .tags("operation", "append", "outcome", "success")
        .timer()
        .count()).isEqualTo(1L);
  }

  @Test
  void recordsPublicPracticePayloadBytesByWhitelistedEventName() {
    practiceRealtime.publicEventAppend("content_delta", 42, PracticeRealtimeOutcome.SUCCESS);
    practiceRealtime.publicEventAppend("content_delta", 17, PracticeRealtimeOutcome.FAILURE);

    assertThat(counter(OpsMetricNames.PRACTICE_REALTIME_PUBLIC_EVENT_APPENDS,
        "event_name", "content_delta", "outcome", "success")).isEqualTo(1.0);
    assertThat(counter(OpsMetricNames.PRACTICE_REALTIME_PUBLIC_EVENT_APPENDS,
        "event_name", "content_delta", "outcome", "failure")).isEqualTo(1.0);
    assertThat(registry.get(OpsMetricNames.PRACTICE_REALTIME_PUBLIC_EVENT_PAYLOAD_BYTES)
        .tag("event_name", "content_delta")
        .summary()
        .count()).isEqualTo(2L);
  }

  private double counter(String name, String... tags) {
    return registry.get(name).tags(tags).counter().count();
  }

  private double activeGauge(String streamType) {
    return registry.get(OpsMetricNames.SSE_CONNECTIONS_ACTIVE)
        .tag("stream_type", streamType)
        .gauge()
        .value();
  }

}
