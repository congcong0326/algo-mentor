package org.congcong.algomentor.ops.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentLoopContext;
import org.congcong.algomentor.agent.core.AgentLoopObserver;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecision;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionType;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.ops.observability.autoconfigure.OpsObservabilityAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgentOpsObserverTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final FakeAgentOpsRecorder recorder = new FakeAgentOpsRecorder();
  private final AgentOpsObserver observer = new AgentOpsObserver(recorder);

  @Test
  void recordsRunLifecycleEvents() {
    AgentLoopContext context = context(Map.of("aiSource", "LEARNING_PLAN_DRAFT"));

    observer.onRunStart(context);
    observer.onRunEnd(context, new AgentRunResult(1, LlmFinishReason.STOP, Map.of()));
    observer.onError(context, new AgentException(AgentErrorCode.UNKNOWN, "failed"));

    assertThat(recorder.events()).containsExactly(
        "runStarted:LEARNING_PLAN_DRAFT",
        "runCompleted:LEARNING_PLAN_DRAFT",
        "runFailed:LEARNING_PLAN_DRAFT");
  }

  @Test
  void logsAgentRunFailuresAndPermissionTimeouts() {
    RecordingStructuredOpsLogger opsLogger = new RecordingStructuredOpsLogger();
    AgentOpsObserver loggingObserver = new AgentOpsObserver(recorder, opsLogger);
    AgentLoopContext context = context(Map.of("aiSource", "PRACTICE_CHAT"));
    AgentToolPermissionRequest request = permissionRequest();

    loggingObserver.onError(context, new AgentException(AgentErrorCode.UNKNOWN, "failed"));
    loggingObserver.onToolPermissionTimeout(
        context,
        request,
        "expired",
        Instant.parse("2026-06-30T00:01:00Z"),
        permissionPlan());

    assertThat(opsLogger.warnEvents).containsExactly(
        "eventType=agent_run_failed exceptionType=AgentException agentSource=practice_message",
        "eventType=agent_tool_permission_timeout toolName=submit_practice_code_review");
  }

  @Test
  void recordsToolEndAndError() {
    AgentLoopContext context = context(Map.of());
    LlmToolCall toolCall = new LlmToolCall(
        "call-1",
        "submit_practice_code_review",
        OBJECT_MAPPER.createObjectNode());

    observer.onToolEnd(context, 1, toolCall, OBJECT_MAPPER.createObjectNode());
    observer.onToolError(context, 1, toolCall, new AgentException(AgentErrorCode.TOOL_EXECUTION_FAILED, "failed"));

    assertThat(recorder.events()).containsExactly(
        "toolExecution:submit_practice_code_review:COMPLETED",
        "toolExecution:submit_practice_code_review:FAILED");
  }

  @Test
  void recordsPermissionDecisionAndTimeout() {
    AgentLoopContext context = context(Map.of());
    AgentToolPermissionRequest request = permissionRequest();
    AgentToolPermissionDecisionPlan plan = permissionPlan();

    observer.onToolPermissionDecision(
        context,
        request,
        new AgentToolPermissionDecision(
            "perm-1",
            AgentToolPermissionDecisionType.ALLOW,
            "user_confirmed",
            7L,
            Instant.parse("2026-06-30T00:00:10Z")),
        plan);
    observer.onToolPermissionTimeout(
        context,
        request,
        "timeout",
        Instant.parse("2026-06-30T00:01:00Z"),
        plan);

    assertThat(recorder.events()).containsExactly(
        "toolPermissionDecision:ALLOW",
        "toolPermissionDecision:timeout");
  }

  @Test
  void mapsKnownRunSourcesAndFallsBackToAgentConversation() {
    assertThat(observer.source(context(Map.of("aiSource", "PROBLEM_DETAIL"))))
        .isEqualTo(AgentOpsSource.AI_EXPLANATION);
    assertThat(observer.source(context(Map.of("aiSource", "PRACTICE_CHAT"))))
        .isEqualTo(AgentOpsSource.PRACTICE_MESSAGE);
    assertThat(observer.source(context(Map.of("aiSource", "LEARNING_CHAT"))))
        .isEqualTo(AgentOpsSource.AGENT_CONVERSATION);
    assertThat(observer.source(context(Map.of("aiSource", "new_high_cardinality_value"))))
        .isEqualTo(AgentOpsSource.AGENT_CONVERSATION);
  }

  @Test
  void sourceMappingFallsBackToRequestMetadata() {
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("hello")),
        Map.of("aiSource", "LEARNING_PLAN_DRAFT"));
    AgentLoopContext context = new AgentLoopContext("run-1", request, 4, Map.of());

    assertThat(observer.source(context)).isEqualTo(AgentOpsSource.LEARNING_PLAN_DRAFT);
  }

  @Test
  void autoConfigurationCreatesMicrometerRecordersWhenMeterRegistryExists() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OpsObservabilityAutoConfiguration.class))
        .withBean(SimpleMeterRegistry.class)
        .run(context -> {
          assertThat(context).hasSingleBean(SseOpsRecorder.class);
          assertThat(context).hasSingleBean(AgentOpsRecorder.class);
          assertThat(context).hasSingleBean(LearningOpsRecorder.class);
          assertThat(context).hasSingleBean(AgentLoopObserver.class);

          context.getBean(AgentOpsRecorder.class).runStarted(AgentOpsSource.AGENT_CONVERSATION);
          SimpleMeterRegistry registry = context.getBean(SimpleMeterRegistry.class);
          assertThat(registry.get(OpsMetricNames.AGENT_RUNS)
              .tag("source", "agent_conversation")
              .tag("status", "started")
              .counter()
              .count()).isEqualTo(1.0);
        });
  }

  @Test
  void autoConfigurationCreatesNoopRecordersWhenMeterRegistryIsMissing() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(OpsObservabilityAutoConfiguration.class))
        .run(context -> {
          assertThat(context.getBean(SseOpsRecorder.class)).isSameAs(NoopOpsRecorders.sse());
          assertThat(context.getBean(AgentOpsRecorder.class)).isSameAs(NoopOpsRecorders.agent());
          assertThat(context.getBean(LearningOpsRecorder.class)).isSameAs(NoopOpsRecorders.learning());
          assertThat(context).hasSingleBean(AgentLoopObserver.class);
        });
  }

  private static AgentLoopContext context(Map<String, Object> metadata) {
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("hello")),
        metadata);
    return new AgentLoopContext("run-1", request, 4, metadata);
  }

  private static AgentToolPermissionRequest permissionRequest() {
    return new AgentToolPermissionRequest(
        "perm-1",
        "run-1",
        1,
        "call-1",
        "submit_practice_code_review",
        "Submit code review",
        "The agent is requesting a persisted review",
        Map.of("effect", "save_review"),
        Instant.parse("2026-06-30T00:00:00Z"),
        Instant.parse("2026-06-30T00:01:00Z"));
  }

  private static AgentToolPermissionDecisionPlan permissionPlan() {
    return AgentToolPermissionDecisionPlan.ask(
        "Submit code review",
        "The agent is requesting a persisted review",
        Map.of("effect", "save_review"),
        "test-policy");
  }

  private static final class FakeAgentOpsRecorder implements AgentOpsRecorder {

    private final List<String> events = new ArrayList<>();

    @Override
    public void runStarted(AgentOpsSource source) {
      events.add("runStarted:" + source.name());
    }

    @Override
    public void runCompleted(AgentOpsSource source) {
      events.add("runCompleted:" + source.name());
    }

    @Override
    public void runFailed(AgentOpsSource source) {
      events.add("runFailed:" + source.name());
    }

    @Override
    public void toolPermissionDecision(String decision) {
      events.add("toolPermissionDecision:" + decision);
    }

    @Override
    public void toolExecution(String toolName, OpsStatus status) {
      events.add("toolExecution:" + toolName + ":" + status.name());
    }

    List<String> events() {
      return List.copyOf(events);
    }

  }

  private static final class RecordingStructuredOpsLogger extends StructuredOpsLogger {

    private final List<String> warnEvents = new ArrayList<>();

    @Override
    public void warn(
        org.slf4j.Logger log,
        OpsLogEventType eventType,
        Map<String, ?> fields,
        Throwable throwable) {
      warnEvents.add(format(eventType, fields));
    }
  }

}
