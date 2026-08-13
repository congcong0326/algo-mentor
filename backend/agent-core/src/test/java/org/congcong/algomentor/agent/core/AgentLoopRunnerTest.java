package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.agent.core.execution.AgentExecutionRejectedException;
import org.congcong.algomentor.agent.core.execution.AgentExecutionGroup;
import org.congcong.algomentor.agent.core.execution.AgentExecutionRejectionReason;
import org.congcong.algomentor.agent.core.execution.AgentExecutor;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionType;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionDecisionPlan;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionGuard;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHook;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHookChain;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionResultFactory;
import org.congcong.algomentor.agent.core.permission.InMemoryAgentToolPermissionCoordinator;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultJsonKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentToolResultTypes;
import org.congcong.algomentor.agent.core.structuredoutput.StructuredOutputRepairEvent;
import org.congcong.algomentor.agent.core.toolresult.InMemoryToolResultStore;
import org.congcong.algomentor.agent.core.toolresult.ToolResultStore;
import org.congcong.algomentor.common.trace.RequestTraceContext;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmContentPart;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmReasoningEffort;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.junit.jupiter.api.Test;

class AgentLoopRunnerTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final AtomicInteger WORKER_SEQUENCE = new AtomicInteger(1);
  private static final AgentExecutor TEST_EXECUTOR = new AgentExecutor() {
    @Override
    public void execute(AgentExecutionGroup group, Runnable task) {
      Thread worker = new Thread(
          RequestTraceContext.wrap(task),
          "agent-loop-test-" + WORKER_SEQUENCE.getAndIncrement());
      worker.setDaemon(true);
      worker.start();
    }

    @Override
    public boolean isShutdown() {
      return false;
    }
  };

  @Test
  void rejectsMissingToolRegistry() {
    assertThatThrownBy(() -> newTestRunner(new FakeGateway(), testModelSelector(), null, 1))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("agent tool registry must not be null");
  }

  @Test
  void streamsOneStepWhenNoToolIsRequested() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.MessageStart(LlmProviderId.of("test"), LlmModelId.of("gpt-test")),
        new LlmStreamEvent.ContentDelta("Use two indices."),
        new LlmStreamEvent.Usage(LlmUsage.empty()),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    AgentLoopRunner runner = newTestRunner(
        gateway,
        new LlmModelSelector(
            LlmProviderId.of("test-provider"),
            LlmModelId.of("gpt-test"),
            Set.of(),
            null),
        AgentToolRegistry.empty(),
        4);

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(events)
        .extracting(AgentStreamEvent::name)
        .containsExactly(
            "agent_run_start",
            "agent_step_start",
            "message_start",
            "content_delta",
            "usage",
            "message_end",
            "agent_step_end",
            "agent_run_end");
    assertThat(gateway.requests).hasSize(1);
    assertThat(gateway.requests.get(0).tools()).isEmpty();
    assertThat(gateway.requests.get(0).messages().get(0).text()).contains("two pointers");
    assertThat(gateway.requests.get(0).modelSelector().providerId())
        .hasValue(LlmProviderId.of("test-provider"));
    assertThat(gateway.requests.get(0).modelSelector().modelId())
        .hasValue(LlmModelId.of("gpt-test"));
    assertThat(gateway.requests.get(0).modelSelector().purpose()).isNull();
  }

  @Test
  void runEndIncludesRequestMetadata() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("Use two indices."),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of("finish", "stop"))));
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        4);

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(
        "run-uuid",
        "Two pointers",
        List.of(LlmMessage.user("two pointers")),
        Map.of("runDbId", 501L, "practiceSessionId", 28L))));

    AgentStreamEvent.AgentRunEnd runEnd = events.stream()
        .filter(AgentStreamEvent.AgentRunEnd.class::isInstance)
        .map(AgentStreamEvent.AgentRunEnd.class::cast)
        .findFirst()
        .orElseThrow();
    assertThat(runEnd.metadata())
        .containsEntry("runDbId", 501L)
        .containsEntry("practiceSessionId", 28L);
  }

  @Test
  void streamWorkerPropagatesRequestTraceContext() {
    FakeGateway gateway = new FakeGateway();
    AtomicReference<String> observedRequestId = new AtomicReference<>();
    gateway.beforeStream = () -> observedRequestId.set(RequestTraceContext.currentRequestId().orElse(null));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("Use two indices."),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        4);

    try (RequestTraceContext.RequestTraceScope ignored = RequestTraceContext.withRequestId("request-agent-1")) {
      collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));
    }

    assertThat(observedRequestId).hasValue("request-agent-1");
  }

  @Test
  void deliversAgentEventsOnAgentLoopWorkerThread() {
    SynchronousGateway gateway = new SynchronousGateway(List.of(
        new LlmStreamEvent.ContentDelta("Use two indices."),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        4);
    ThreadRecordingSubscriber subscriber = new ThreadRecordingSubscriber();

    runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))).subscribe(subscriber);

    subscriber.await();
    assertThat(subscriber.error).isNull();
    assertThat(subscriber.callbackThreads)
        .isNotEmpty()
        .allMatch(threadName -> threadName.startsWith("agent-loop-test-"))
        .noneMatch(threadName -> threadName.startsWith("ForkJoinPool.commonPool-worker-"));
  }

  @Test
  void slowSubscriberOnlyBlocksItsOwnAgentRun() {
    AgentLoopRunner slowRunner = newTestRunner(
        new SynchronousGateway(List.of(new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of()))),
        testModelSelector(),
        AgentToolRegistry.empty(),
        4);
    BlockingSubscriber slowSubscriber = new BlockingSubscriber();
    slowRunner.stream(new AgentRequest(List.of(LlmMessage.user("slow")))).subscribe(slowSubscriber);
    slowSubscriber.awaitBlocked();

    try {
      AgentLoopRunner fastRunner = newTestRunner(
          new SynchronousGateway(List.of(new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of()))),
          testModelSelector(),
          AgentToolRegistry.empty(),
          4);

      List<AgentStreamEvent> fastEvents = collect(
          fastRunner.stream(new AgentRequest(List.of(LlmMessage.user("fast")))));

      assertThat(fastEvents).extracting(AgentStreamEvent::name).endsWith(AgentStreamEventNames.AGENT_RUN_END);
    } finally {
      slowSubscriber.release();
    }
    slowSubscriber.awaitCompletion();
  }

  @Test
  void cancelDuringSubscriptionStillNotifiesTerminalObserversWithoutCallingLlm() {
    SynchronousGateway gateway = new SynchronousGateway(List.of(
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    AtomicReference<AgentErrorCode> observedError = new AtomicReference<>();
    CountDownLatch errorObserved = new CountDownLatch(1);
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onError(AgentLoopContext context, AgentException error) {
        observedError.set(error.code());
        errorObserved.countDown();
      }
    };
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        LlmToolChoice.auto(),
        4,
        List.of(observer),
        List.of());

    runner.stream(new AgentRequest(List.of(LlmMessage.user("cancel"))))
        .subscribe(new CancellingOnSubscribeSubscriber());

    await(errorObserved);
    assertThat(observedError).hasValue(AgentErrorCode.CANCELLED);
    assertThat(gateway.streamCalls).isZero();
  }

  @Test
  void executorRejectionNotifiesTerminalObserversWithoutCallingLlm() {
    FakeGateway gateway = new FakeGateway();
    AtomicReference<AgentException> observedError = new AtomicReference<>();
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onError(AgentLoopContext context, AgentException error) {
        observedError.set(error);
      }
    };
    AgentExecutor rejectingExecutor = new AgentExecutor() {
      @Override
      public void execute(AgentExecutionGroup group, Runnable task) {
        throw new AgentExecutionRejectedException(
            AgentExecutionRejectionReason.SATURATED,
            "saturated",
            new java.util.concurrent.RejectedExecutionException("saturated"));
      }

      @Override
      public boolean isShutdown() {
        return false;
      }
    };
    AgentLoopRunner runner = new AgentLoopRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        LlmToolChoice.auto(),
        4,
        List.of(observer),
        List.of(),
        rejectingExecutor);
    CollectingSubscriber subscriber = new CollectingSubscriber();

    runner.stream(new AgentRequest(List.of(LlmMessage.user("busy")))).subscribe(subscriber);

    subscriber.await();
    assertThat(subscriber.error)
        .isInstanceOf(AgentException.class)
        .extracting(error -> ((AgentException) error).code())
        .isEqualTo(AgentErrorCode.AGENT_EXECUTOR_OVERLOADED);
    assertThat(observedError.get().code()).isEqualTo(AgentErrorCode.AGENT_EXECUTOR_OVERLOADED);
    assertThat(observedError.get().retryable()).isTrue();
    assertThat(subscriber.events).isEmpty();
    assertThat(gateway.requests).isEmpty();
  }

  @Test
  void executorSubmissionFailureNotifiesObserverBeforeSubscriber() {
    FakeGateway gateway = new FakeGateway();
    List<String> notifications = new ArrayList<>();
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onError(AgentLoopContext context, AgentException error) {
        notifications.add("observer:" + error.code());
      }
    };
    AgentExecutor failingExecutor = new AgentExecutor() {
      @Override
      public void execute(AgentExecutionGroup group, Runnable task) {
        throw new IllegalStateException("executor unavailable");
      }

      @Override
      public boolean isShutdown() {
        return false;
      }
    };
    AgentLoopRunner runner = new AgentLoopRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        LlmToolChoice.auto(),
        4,
        List.of(observer),
        List.of(),
        failingExecutor);
    Flow.Subscriber<AgentStreamEvent> subscriber = new Flow.Subscriber<>() {
      @Override
      public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
      }

      @Override
      public void onNext(AgentStreamEvent item) {
      }

      @Override
      public void onError(Throwable throwable) {
        notifications.add("subscriber:" + throwable.getMessage());
      }

      @Override
      public void onComplete() {
      }
    };

    runner.stream(new AgentRequest(List.of(LlmMessage.user("submission failure")))).subscribe(subscriber);

    assertThat(notifications).containsExactly(
        "observer:UNKNOWN",
        "subscriber:executor unavailable");
    assertThat(gateway.requests).isEmpty();
  }

  @Test
  void executesToolCallAndContinuesWithToolResultMessage() {
    FakeGateway gateway = new FakeGateway();
    LlmToolCall toolCall = new LlmToolCall(
        "call_1",
        "fake_lookup",
        JsonNodeFactory.instance.objectNode().put("topic", "two pointers"));
    gateway.steps.add(List.of(
        new LlmStreamEvent.MessageStart(LlmProviderId.of("test"), LlmModelId.of("gpt-test")),
        new LlmStreamEvent.ToolCallStart("call_1", "fake_lookup"),
        new LlmStreamEvent.ToolCallEnd(toolCall),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    gateway.steps.add(List.of(
        new LlmStreamEvent.MessageStart(LlmProviderId.of("test"), LlmModelId.of("gpt-test")),
        new LlmStreamEvent.ContentDelta("Tool result explained."),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    FakeTool tool = new FakeTool("fake_lookup", JsonNodeFactory.instance.objectNode().put("summary", "two pointers data"));
    AgentLoopRunner runner = newTestRunner(
        gateway,
        "gpt-test",
        AgentToolRegistry.of(List.of(tool)),
        4);

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(events)
        .extracting(AgentStreamEvent::name)
        .contains(
            "tool_call_start",
            "tool_call_end",
            "agent_tool_start",
            "agent_tool_end",
            "agent_run_end");
    assertThat(gateway.requests).hasSize(2);
    assertThat(gateway.requests.get(0).tools()).extracting(LlmToolSpec::name).containsExactly("fake_lookup");
    assertThat(gateway.requests.get(1).messages().get(1).role()).isEqualTo(LlmMessage.Role.ASSISTANT);
    assertThat(gateway.requests.get(1).messages().get(1).toolCalls()).containsExactly(toolCall);
    assertThat(gateway.requests.get(1).messages().get(2).role()).isEqualTo(LlmMessage.Role.TOOL);
    assertThat(gateway.requests.get(1).messages().get(2).toolCallId()).isEqualTo("call_1");
    assertThat(tool.executedArguments).isEqualTo(toolCall.arguments());
    assertThat(tool.executionCount).isEqualTo(1);
  }

  @Test
  void permissionAllowRunsRealToolWithoutPermissionEvents() {
    FakeGateway gateway = new FakeGateway();
    LlmToolCall toolCall = new LlmToolCall(
        "call_1",
        "fake_lookup",
        JsonNodeFactory.instance.objectNode().put("topic", "two pointers"));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(toolCall),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("Tool result explained."),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    FakeTool tool = new FakeTool("fake_lookup", JsonNodeFactory.instance.objectNode().put("summary", "allowed data"));
    AgentLoopRunner runner = runnerWithPermissionPlan(
        gateway,
        tool,
        AgentToolPermissionDecisionPlan.allow("test-policy"),
        InMemoryAgentToolPermissionCoordinator.DEFAULT_TIMEOUT);

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(
        "run-allow",
        "Permission allow",
        List.of(LlmMessage.user("two pointers")),
        Map.of(AgentRuntimeMetadataKeys.USER_ID, 7L))));

    assertThat(tool.executionCount).isEqualTo(1);
    assertThat(tool.executedArguments).isEqualTo(toolCall.arguments());
    assertThat(events).extracting(AgentStreamEvent::name)
        .contains("agent_tool_start", "agent_tool_end", "agent_run_end")
        .doesNotContain("tool_permission_request", "tool_permission_decision", "tool_permission_timeout");
    LlmContentPart.ToolResult toolResult = toolResultFromSecondLlmRequest(gateway);
    assertThat(toolResult.result().get("summary").asText()).isEqualTo("allowed data");
  }

  @Test
  void permissionDenySkipsToolStartExecutionAndContinuesWithSyntheticToolResult() {
    FakeGateway gateway = new FakeGateway();
    LlmToolCall toolCall = new LlmToolCall(
        "call_1",
        "fake_lookup",
        JsonNodeFactory.instance.objectNode().put("topic", "two pointers"));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(toolCall),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("I can continue without running it."),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    FakeTool tool = new FakeTool("fake_lookup", JsonNodeFactory.instance.objectNode().put("summary", "should not run"));
    AgentLoopRunner runner = runnerWithPermissionPlan(
        gateway,
        tool,
        AgentToolPermissionDecisionPlan.deny("policy_blocked", "test-policy"),
        InMemoryAgentToolPermissionCoordinator.DEFAULT_TIMEOUT);

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(tool.executionCount).isZero();
    assertThat(tool.executedArguments).isNull();
    assertThat(events).extracting(AgentStreamEvent::name)
        .doesNotContain("agent_tool_start")
        .contains("agent_tool_end", "agent_run_end");
    assertThat(gateway.requests).hasSize(2);
    LlmContentPart.ToolResult toolResult = toolResultFromSecondLlmRequest(gateway);
    assertThat(toolResult.result().get(AgentToolResultJsonKeys.TYPE).asText())
        .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_DENIED);
    assertThat(toolResult.result().get(AgentToolResultJsonKeys.REASON).asText()).isEqualTo("policy_blocked");
  }

  @Test
  void permissionAskUserAllowEmitsDecisionRunsToolAndContinuesWithRealToolResult() {
    FakeGateway gateway = new FakeGateway();
    LlmToolCall toolCall = new LlmToolCall(
        "call_1",
        "fake_lookup",
        JsonNodeFactory.instance.objectNode().put("topic", "two pointers"));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(toolCall),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("Allowed tool result explained."),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    FakeTool tool = new FakeTool("fake_lookup", JsonNodeFactory.instance.objectNode().put("summary", "real data"));
    InMemoryAgentToolPermissionCoordinator coordinator = permissionCoordinator(Duration.ofSeconds(5));
    AgentLoopRunner runner = runnerWithPermissionCoordinator(
        gateway,
        tool,
        askPlan(),
        coordinator,
        List.of());
    PermissionDecisionSubscriber subscriber = new PermissionDecisionSubscriber();

    runner.stream(new AgentRequest(
        "run-ask-allow",
        "Permission ask allow",
        List.of(LlmMessage.user("two pointers")),
        Map.of(AgentRuntimeMetadataKeys.USER_ID, 7L))).subscribe(subscriber);

    AgentStreamEvent.ToolPermissionRequest request = subscriber.awaitPermissionRequest();
    assertThat(tool.executionCount).isZero();
    assertThat(subscriber.events()).extracting(AgentStreamEvent::name)
        .contains("tool_permission_request")
        .doesNotContain("tool_permission_decision", "agent_tool_start");

    coordinator.decide(
        request.permissionRequestId(),
        AgentToolPermissionDecisionType.ALLOW,
        "user_confirmed",
        7L);
    List<AgentStreamEvent> events = subscriber.awaitCompletion();

    assertThat(tool.executionCount).isEqualTo(1);
    assertThat(tool.executedArguments).isEqualTo(toolCall.arguments());
    assertThat(coordinator.pendingRequestCount()).isZero();
    assertThat(events).extracting(AgentStreamEvent::name).containsExactly(
        "agent_run_start",
        "agent_step_start",
        "tool_call_end",
        "message_end",
        "agent_step_end",
        "tool_permission_request",
        "tool_permission_decision",
        "agent_tool_start",
        "agent_tool_end",
        "agent_step_start",
        "content_delta",
        "message_end",
        "agent_step_end",
        "agent_run_end");
    AgentStreamEvent.ToolPermissionDecision decision = events.stream()
        .filter(AgentStreamEvent.ToolPermissionDecision.class::isInstance)
        .map(AgentStreamEvent.ToolPermissionDecision.class::cast)
        .findFirst()
        .orElseThrow();
    assertThat(decision.permissionRequestId()).isEqualTo(request.permissionRequestId());
    assertThat(decision.decision()).isEqualTo(AgentToolPermissionDecisionType.ALLOW);
    LlmContentPart.ToolResult toolResult = toolResultFromSecondLlmRequest(gateway);
    assertThat(toolResult.result().get("summary").asText()).isEqualTo("real data");
  }

  @Test
  void permissionAskUserDenySkipsToolStartAndContinuesWithSyntheticToolResult() {
    FakeGateway gateway = new FakeGateway();
    LlmToolCall toolCall = new LlmToolCall(
        "call_1",
        "fake_lookup",
        JsonNodeFactory.instance.objectNode().put("topic", "two pointers"));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(toolCall),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("Denied, continuing without tool execution."),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    FakeTool tool = new FakeTool("fake_lookup", JsonNodeFactory.instance.objectNode().put("summary", "should not run"));
    InMemoryAgentToolPermissionCoordinator coordinator = permissionCoordinator(Duration.ofSeconds(5));
    AgentLoopRunner runner = runnerWithPermissionCoordinator(
        gateway,
        tool,
        askPlan(),
        coordinator,
        List.of());
    PermissionDecisionSubscriber subscriber = new PermissionDecisionSubscriber();

    runner.stream(new AgentRequest(
        "run-ask-deny",
        "Permission ask deny",
        List.of(LlmMessage.user("two pointers")),
        Map.of(AgentRuntimeMetadataKeys.USER_ID, 7L))).subscribe(subscriber);

    AgentStreamEvent.ToolPermissionRequest request = subscriber.awaitPermissionRequest();
    assertThat(tool.executionCount).isZero();
    coordinator.decide(
        request.permissionRequestId(),
        AgentToolPermissionDecisionType.DENY,
        "user_rejected",
        7L);
    List<AgentStreamEvent> events = subscriber.awaitCompletion();

    assertThat(tool.executionCount).isZero();
    assertThat(tool.executedArguments).isNull();
    assertThat(coordinator.pendingRequestCount()).isZero();
    assertThat(events).extracting(AgentStreamEvent::name).containsExactly(
        "agent_run_start",
        "agent_step_start",
        "tool_call_end",
        "message_end",
        "agent_step_end",
        "tool_permission_request",
        "tool_permission_decision",
        "agent_tool_end",
        "agent_step_start",
        "content_delta",
        "message_end",
        "agent_step_end",
        "agent_run_end");
    AgentStreamEvent.AgentToolEnd toolEnd = events.stream()
        .filter(AgentStreamEvent.AgentToolEnd.class::isInstance)
        .map(AgentStreamEvent.AgentToolEnd.class::cast)
        .findFirst()
        .orElseThrow();
    assertThat(toolEnd.result().get(AgentToolResultJsonKeys.TYPE).asText())
        .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_DENIED);
    assertThat(toolEnd.result().get(AgentToolResultJsonKeys.PERMISSION_REQUEST_ID).asText())
        .isEqualTo(request.permissionRequestId());
    assertThat(toolEnd.result().get(AgentToolResultJsonKeys.REASON).asText()).isEqualTo("user_rejected");
    LlmContentPart.ToolResult toolResult = toolResultFromSecondLlmRequest(gateway);
    assertThat(toolResult.result()).isEqualTo(toolEnd.result());
  }

  @Test
  void permissionTimeoutSkipsToolStartExecutionAndContinuesWithSyntheticToolResult() {
    FakeGateway gateway = new FakeGateway();
    LlmToolCall toolCall = new LlmToolCall(
        "call_1",
        "fake_lookup",
        JsonNodeFactory.instance.objectNode().put("topic", "two pointers"));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(toolCall),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("Permission timed out, so I did not run it."),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    FakeTool tool = new FakeTool("fake_lookup", JsonNodeFactory.instance.objectNode().put("summary", "should not run"));
    AgentLoopRunner runner = runnerWithPermissionPlan(
        gateway,
        tool,
        AgentToolPermissionDecisionPlan.ask(
            "Fake lookup",
            "需要用户确认",
            Map.of("toolName", "fake_lookup"),
            "test-policy"),
        Duration.ofMillis(1));

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(
        "run-1",
        "Permission timeout",
        List.of(LlmMessage.user("two pointers")),
        Map.of(AgentRuntimeMetadataKeys.USER_ID, 7L))));

    assertThat(tool.executionCount).isZero();
    assertThat(tool.executedArguments).isNull();
    assertThat(events).extracting(AgentStreamEvent::name)
        .contains("tool_permission_request", "tool_permission_timeout", "agent_tool_end", "agent_run_end")
        .doesNotContain("tool_permission_decision", "agent_tool_start");
    assertEventOrder(
        events,
        "agent_step_end",
        "tool_permission_request",
        "tool_permission_timeout",
        "agent_tool_end",
        "agent_step_start",
        "agent_run_end");
    assertThat(gateway.requests).hasSize(2);
    AgentStreamEvent.AgentToolEnd toolEnd = events.stream()
        .filter(AgentStreamEvent.AgentToolEnd.class::isInstance)
        .map(AgentStreamEvent.AgentToolEnd.class::cast)
        .findFirst()
        .orElseThrow();
    LlmContentPart.ToolResult toolResult = toolResultFromSecondLlmRequest(gateway);
    assertThat(toolResult.result()).isEqualTo(toolEnd.result());
    assertThat(toolResult.result().get(AgentToolResultJsonKeys.TYPE).asText())
        .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_TIMEOUT);
    assertThat(toolResult.result().get(AgentToolResultJsonKeys.RETRYABLE).asBoolean()).isTrue();
  }

  @Test
  void cancellationDuringPendingPermissionDoesNotHangAndDoesNotExecuteTool() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(
            new LlmToolCall("call_1", "fake_lookup", JsonNodeFactory.instance.objectNode().put("topic", "cancel"))),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    FakeTool tool = new FakeTool("fake_lookup", JsonNodeFactory.instance.objectNode().put("summary", "should not run"));
    InMemoryAgentToolPermissionCoordinator coordinator = permissionCoordinator(Duration.ofSeconds(5));
    List<AgentErrorCode> observedErrors = new java.util.concurrent.CopyOnWriteArrayList<>();
    List<JsonNode> observedToolResults = new java.util.concurrent.CopyOnWriteArrayList<>();
    CountDownLatch errorObserved = new CountDownLatch(1);
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onToolEnd(AgentLoopContext context, int stepIndex, LlmToolCall toolCall, JsonNode result) {
        observedToolResults.add(result);
      }

      @Override
      public void onError(AgentLoopContext context, AgentException error) {
        observedErrors.add(error.code());
        errorObserved.countDown();
      }
    };
    AgentLoopRunner runner = runnerWithPermissionCoordinator(
        gateway,
        tool,
        askPlan(),
        coordinator,
        List.of(observer));
    PermissionDecisionSubscriber subscriber = new PermissionDecisionSubscriber();

    runner.stream(new AgentRequest(
        "run-cancel",
        "Permission cancel",
        List.of(LlmMessage.user("two pointers")),
        Map.of(AgentRuntimeMetadataKeys.USER_ID, 7L))).subscribe(subscriber);

    subscriber.awaitPermissionRequest();
    assertThat(coordinator.pendingRequestCount()).isEqualTo(1);
    subscriber.cancel();
    await(errorObserved);

    assertThat(tool.executionCount).isZero();
    assertThat(tool.executedArguments).isNull();
    assertThat(coordinator.pendingRequestCount()).isZero();
    assertThat(observedErrors).containsExactly(AgentErrorCode.CANCELLED);
    assertThat(observedToolResults).singleElement().satisfies(result -> {
      assertThat(result.get(AgentToolResultJsonKeys.TYPE).asText())
          .isEqualTo(AgentToolResultTypes.TOOL_PERMISSION_DENIED);
      assertThat(result.get(AgentToolResultJsonKeys.REASON).asText())
          .isEqualTo(AgentToolPermissionResultFactory.REASON_RUN_CANCELLED);
    });
    assertThat(subscriber.events()).extracting(AgentStreamEvent::name)
        .contains("tool_permission_request")
        .doesNotContain("agent_tool_start");
  }

  @Test
  void sendsPreviewInsteadOfLargeToolResultToNextLlmRequest() {
    FakeGateway gateway = new FakeGateway();
    LlmToolCall toolCall = new LlmToolCall(
        "call_1",
        "fake_lookup",
        JsonNodeFactory.instance.objectNode());
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(toolCall),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    gateway.steps.add(List.of(new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    String largePayload = "abcdefghijklmnopqrstuvwxyz";
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.of(List.of(new FakeTool(
            "fake_lookup",
            JsonNodeFactory.instance.objectNode().put("payload", largePayload)))),
        LlmToolChoice.auto(),
        4,
        List.of(),
        List.of(),
        new ToolResultCompactionPolicy(10, 8, 100, true, 1_000, 3, true, 1_000, 80, 2, 24, true, false),
        new InMemoryToolResultStore(),
        new com.fasterxml.jackson.databind.ObjectMapper());

    collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    LlmContentPart.ToolResult toolResult =
        (LlmContentPart.ToolResult) gateway.requests.get(1).messages().get(2).content().get(0);
    assertThat(toolResult.result().get("type").asText()).isEqualTo("tool_result_preview");
    assertThat(toolResult.result().get("preview").asText()).hasSize(8);
    assertThat(toolResult.result().toString()).doesNotContain(largePayload);
  }

  @Test
  void forwardsConfiguredToolChoiceToLlmRequest() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    FakeTool tool = new FakeTool("calculator", JsonNodeFactory.instance.objectNode().put("value", "3"));
    AgentLoopRunner runner = newTestRunner(
        gateway,
        new LlmModelSelector(null, LlmModelId.of("gpt-test"), Set.of(), null),
        AgentToolRegistry.of(List.of(tool)),
        LlmToolChoice.specific("calculator"),
        4);

    collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("calculate 1 + 2")))));

    assertThat(gateway.requests.get(0).tools()).extracting(LlmToolSpec::name).containsExactly("calculator");
    assertThat(gateway.requests.get(0).toolChoice().mode()).isEqualTo(LlmToolChoice.Mode.SPECIFIC);
    assertThat(gateway.requests.get(0).toolChoice().toolName()).isEqualTo("calculator");
  }

  @Test
  void capturesFinalTextOutputAndExposesItBeforeRunEnd() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("Use "),
        new LlmStreamEvent.ContentDelta("two indices."),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    List<String> observed = new ArrayList<>();
    List<AgentOutput> outputs = new ArrayList<>();
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onFinalOutput(AgentLoopContext context, AgentOutput output) {
        observed.add("final-output:" + output.text());
        outputs.add(output);
      }

      @Override
      public void onRunEnd(AgentLoopContext context, AgentRunResult result) {
        observed.add("run-end:" + result.output().text());
      }
    };
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        LlmToolChoice.auto(),
        4,
        List.of(observer),
        List.of());

    collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(outputs).hasSize(1);
    assertThat(outputs.get(0).text()).isEqualTo("Use two indices.");
    assertThat(outputs.get(0).hasStructuredOutput()).isFalse();
    assertThat(observed).containsExactly(
        "final-output:Use two indices.",
        "run-end:Use two indices.");
  }

  @Test
  void parsesJsonFinalOutputForProviderNativeResponseFormat() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("{\"days\":7,\"title\":\"Plan\"}"),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    List<AgentOutput> outputs = new ArrayList<>();
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onFinalOutput(AgentLoopContext context, AgentOutput output) {
        outputs.add(output);
      }
    };
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        LlmToolChoice.auto(),
        4,
        List.of(observer),
        List.of());
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("create plan")),
        Map.of(),
        new AgentExecutionOptions(
            null,
            new LlmResponseFormat.JsonSchema(
                "learning_plan_draft",
                JsonNodeFactory.instance.objectNode().put("type", "object"),
                true),
            new AgentStructuredOutputOptions(
                StructuredOutputStrategy.PROVIDER_NATIVE,
                "learning_plan_draft",
                "v1",
                true)));

    collect(runner.stream(request));

    assertThat(outputs).hasSize(1);
    AgentOutput output = outputs.get(0);
    assertThat(output.text()).isEqualTo("{\"days\":7,\"title\":\"Plan\"}");
    assertThat(output.hasStructuredOutput()).isTrue();
    assertThat(output.structured().get("days").asInt()).isEqualTo(7);
    assertThat(output.schemaName()).isEqualTo("learning_plan_draft");
    assertThat(output.schemaVersion()).isEqualTo("v1");
  }

  @Test
  void repairsRequiredStructuredOutputOnceWithoutToolsOrReasoning() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("```json\n{\"days\":7}\n```"),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("{\"days\":7}"),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    List<AgentOutput> outputs = new ArrayList<>();
    List<StructuredOutputRepairEvent> repairEvents = new ArrayList<>();
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onFinalOutput(AgentLoopContext context, AgentOutput output) {
        outputs.add(output);
      }

      @Override
      public void onStructuredOutputRepair(
          AgentLoopContext context,
          StructuredOutputRepairEvent event
      ) {
        repairEvents.add(event);
      }
    };
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        LlmToolChoice.auto(),
        4,
        List.of(observer),
        List.of());
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("create plan")),
        Map.of(),
        new AgentExecutionOptions(
            new LlmGenerationOptions(0.7, 0.8, 3000, List.of("STOP"), 7L, Duration.ofSeconds(30)),
            new LlmResponseFormat.JsonSchema(
                "learning_plan_draft",
                JsonNodeFactory.instance.objectNode().put("type", "object"),
                true),
            new AgentStructuredOutputOptions(
                StructuredOutputStrategy.PROVIDER_NATIVE,
                "learning_plan_draft",
                "v1",
                true)));

    List<AgentStreamEvent> events = collect(runner.stream(request));

    assertThat(gateway.requests).hasSize(2);
    assertThat(gateway.requests.get(1)).satisfies(repairRequest -> {
      assertThat(repairRequest.tools()).isEmpty();
      assertThat(repairRequest.toolChoice()).isEqualTo(LlmToolChoice.none());
      assertThat(repairRequest.options().reasoningEffort()).isEqualTo(LlmReasoningEffort.NONE);
      assertThat(repairRequest.options().temperature()).isZero();
      assertThat(repairRequest.options().topP()).isNull();
      assertThat(repairRequest.options().stop()).isEmpty();
      assertThat(repairRequest.responseFormat()).isEqualTo(request.executionOptions().responseFormat());
      assertThat(repairRequest.messages()).hasSize(2);
      assertThat(repairRequest.messages().get(1).text())
          .contains("previousResponse", "```json", "JSON_PARSE");
      assertThat(repairRequest.metadata())
          .containsEntry(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_REPAIR_ATTEMPT, 1)
          .containsEntry(AgentRuntimeMetadataKeys.STEP_INDEX, 2);
    });
    assertThat(outputs).singleElement().satisfies(output -> {
      assertThat(output.text()).isEqualTo("{\"days\":7}");
      assertThat(output.structured().path("days").asInt()).isEqualTo(7);
      assertThat(output.metadata())
          .containsEntry(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_REPAIRED, true)
          .containsEntry(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_REPAIR_ATTEMPTS, 1)
          .containsEntry(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_INITIAL_FAILURE_TYPE, "JSON_PARSE");
    });
    assertThat(events.stream()
        .filter(AgentStreamEvent.AgentStepStart.class::isInstance)
        .map(AgentStreamEvent.AgentStepStart.class::cast)
        .map(AgentStreamEvent.AgentStepStart::stepIndex))
        .containsExactly(1, 2);
    assertThat(events.stream()
        .filter(AgentStreamEvent.AgentRunEnd.class::isInstance)
        .map(AgentStreamEvent.AgentRunEnd.class::cast)
        .map(AgentStreamEvent.AgentRunEnd::steps))
        .containsExactly(2);
    assertThat(repairEvents)
        .extracting(StructuredOutputRepairEvent::outcome)
        .containsExactly(
            StructuredOutputRepairEvent.Outcome.TRIGGERED,
            StructuredOutputRepairEvent.Outcome.SUCCEEDED);
  }

  @Test
  void repairsJsonThatDoesNotMatchTheRequestedSchema() {
    ObjectNode schema = JsonNodeFactory.instance.objectNode();
    schema.put("type", "object");
    schema.put("additionalProperties", false);
    schema.putObject("properties").putObject("days").put("type", "integer");
    schema.putArray("required").add("days");
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("{\"days\":\"seven\"}"),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("{\"days\":7}"),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    List<AgentOutput> outputs = new ArrayList<>();
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onFinalOutput(AgentLoopContext context, AgentOutput output) {
        outputs.add(output);
      }
    };
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        LlmToolChoice.auto(),
        4,
        List.of(observer),
        List.of());
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("create plan")),
        Map.of(),
        new AgentExecutionOptions(
            null,
            new LlmResponseFormat.JsonSchema("learning_plan_draft", schema, true),
            new AgentStructuredOutputOptions(
                StructuredOutputStrategy.PROVIDER_NATIVE,
                "learning_plan_draft",
                "v1",
                true)));

    collect(runner.stream(request));

    assertThat(gateway.requests).hasSize(2);
    assertThat(gateway.requests.get(1).messages().get(1).text()).contains("JSON_SCHEMA", "days");
    assertThat(outputs).singleElement().satisfies(output -> assertThat(output.metadata())
        .containsEntry(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_INITIAL_FAILURE_TYPE, "JSON_SCHEMA")
        .containsEntry(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_REPAIRED, true));
  }

  @Test
  void emitsAgentErrorWhenRequiredStructuredOutputIsInvalidJson() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("not-json"),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("still-not-json"),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    List<StructuredOutputRepairEvent> repairEvents = new ArrayList<>();
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        LlmToolChoice.auto(),
        4,
        List.of(new AgentLoopObserver() {
          @Override
          public void onStructuredOutputRepair(
              AgentLoopContext context,
              StructuredOutputRepairEvent event
          ) {
            repairEvents.add(event);
          }
        }),
        List.of());
    AgentRequest request = new AgentRequest(
        "run-1",
        "request-1",
        List.of(LlmMessage.user("create plan")),
        Map.of(),
        new AgentExecutionOptions(
            null,
            new LlmResponseFormat.JsonObject(),
            new AgentStructuredOutputOptions(
                StructuredOutputStrategy.PROVIDER_NATIVE,
                "learning_plan_draft",
                "v1",
                true)));

    List<AgentStreamEvent> events = collect(runner.stream(request));

    assertThat(events).extracting(AgentStreamEvent::name).doesNotContain("agent_run_end");
    assertThat(events.get(events.size() - 1)).isInstanceOf(AgentStreamEvent.AgentError.class);
    AgentStreamEvent.AgentError error = (AgentStreamEvent.AgentError) events.get(events.size() - 1);
    assertThat(error.error().code()).isEqualTo(AgentErrorCode.STRUCTURED_OUTPUT_INVALID);
    assertThat(error.error().metadata())
        .containsEntry("schemaName", "learning_plan_draft")
        .containsEntry(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_FAILURE_TYPE, "JSON_PARSE")
        .containsEntry(AgentRuntimeMetadataKeys.STRUCTURED_OUTPUT_REPAIR_ATTEMPTS, 1);
    assertThat(gateway.requests).hasSize(2);
    assertThat(repairEvents)
        .extracting(StructuredOutputRepairEvent::outcome)
        .containsExactly(
            StructuredOutputRepairEvent.Outcome.TRIGGERED,
            StructuredOutputRepairEvent.Outcome.FAILED);
  }

  @Test
  void ignoresToolCallStepContentWhenCapturingFinalOutput() {
    FakeGateway gateway = new FakeGateway();
    LlmToolCall toolCall = new LlmToolCall(
        "call_1",
        "fake_lookup",
        JsonNodeFactory.instance.objectNode());
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("I will call a tool. "),
        new LlmStreamEvent.ToolCallEnd(toolCall),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("Final answer."),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    List<AgentOutput> outputs = new ArrayList<>();
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onFinalOutput(AgentLoopContext context, AgentOutput output) {
        outputs.add(output);
      }
    };
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.of(List.of(new FakeTool("fake_lookup", JsonNodeFactory.instance.objectNode()))),
        LlmToolChoice.auto(),
        4,
        List.of(observer),
        List.of());

    collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(outputs).singleElement().extracting(AgentOutput::text).isEqualTo("Final answer.");
  }

  @Test
  void emitsAgentErrorWhenToolIsUnknown() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(
            new LlmToolCall("call_1", "missing_tool", JsonNodeFactory.instance.objectNode())),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    AgentLoopRunner runner = newTestRunner(
        gateway,
        new LlmModelSelector(null, LlmModelId.of("gpt-test"), Set.of(), null),
        AgentToolRegistry.empty(),
        4);

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(events.get(events.size() - 1)).isInstanceOf(AgentStreamEvent.AgentError.class);
    assertThat(events).extracting(AgentStreamEvent::name)
        .doesNotContain("tool_permission_request", "tool_permission_decision", "tool_permission_timeout");
    AgentStreamEvent.AgentError error = (AgentStreamEvent.AgentError) events.get(events.size() - 1);
    assertThat(error.error().code()).isEqualTo(AgentErrorCode.UNKNOWN_TOOL);
    assertThat(error.error().metadata()).containsEntry("toolName", "missing_tool");
  }

  @Test
  void emitsAgentErrorWhenMaxStepsIsExceeded() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(
            new LlmToolCall("call_1", "fake_lookup", JsonNodeFactory.instance.objectNode())),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(
            new LlmToolCall("call_2", "fake_lookup", JsonNodeFactory.instance.objectNode())),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    AgentLoopRunner runner = newTestRunner(
        gateway,
        "gpt-test",
        AgentToolRegistry.of(List.of(new FakeTool("fake_lookup", JsonNodeFactory.instance.objectNode()))),
        1);

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(events.get(events.size() - 1)).isInstanceOf(AgentStreamEvent.AgentError.class);
    AgentStreamEvent.AgentError error = (AgentStreamEvent.AgentError) events.get(events.size() - 1);
    assertThat(error.error().code()).isEqualTo(AgentErrorCode.MAX_STEPS_EXCEEDED);
    assertThat(gateway.requests).hasSize(1);
  }

  @Test
  void emitsAgentErrorWhenToolExecutionFails() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(
            new LlmToolCall("call_1", "fake_lookup", JsonNodeFactory.instance.objectNode())),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    AgentLoopRunner runner = newTestRunner(
        gateway,
        "gpt-test",
        AgentToolRegistry.of(List.of(new FailingTool("fake_lookup"))),
        4);

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(events.get(events.size() - 1)).isInstanceOf(AgentStreamEvent.AgentError.class);
    AgentStreamEvent.AgentError error = (AgentStreamEvent.AgentError) events.get(events.size() - 1);
    assertThat(error.error().code()).isEqualTo(AgentErrorCode.TOOL_EXECUTION_FAILED);
    assertThat(error.error().metadata())
        .containsEntry("toolName", "fake_lookup")
        .containsEntry("toolCallId", "call_1")
        .containsEntry("errorType", IllegalStateException.class.getName())
        .containsEntry("errorMessage", "tool failed")
        .containsEntry("rootCauseType", IllegalStateException.class.getName())
        .containsEntry("rootCauseMessage", "tool failed");
  }

  @Test
  void enrichesAgentToolErrorsWithCauseMetadata() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(
            new LlmToolCall("call_1", "fake_lookup", JsonNodeFactory.instance.objectNode())),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    AgentLoopRunner runner = newTestRunner(
        gateway,
        "gpt-test",
        AgentToolRegistry.of(List.of(new AgentFailingTool("fake_lookup"))),
        4);

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(events.get(events.size() - 1)).isInstanceOf(AgentStreamEvent.AgentError.class);
    AgentStreamEvent.AgentError error = (AgentStreamEvent.AgentError) events.get(events.size() - 1);
    assertThat(error.error().code()).isEqualTo(AgentErrorCode.TOOL_EXECUTION_FAILED);
    assertThat(error.error().getMessage()).isEqualTo("wrapped tool failure");
    assertThat(error.error().metadata())
        .containsEntry("toolName", "fake_lookup")
        .containsEntry("toolCallId", "call_1")
        .containsEntry("errorType", AgentException.class.getName())
        .containsEntry("errorMessage", "wrapped tool failure")
        .containsEntry("causeType", IllegalStateException.class.getName())
        .containsEntry("causeMessage", "repository failed")
        .containsEntry("rootCauseType", IllegalArgumentException.class.getName())
        .containsEntry("rootCauseMessage", "database rejected query")
        .containsEntry("businessKey", "kept");
  }

  @Test
  void notifiesObserverWhenToolExecutionFails() {
    FakeGateway gateway = new FakeGateway();
    LlmToolCall toolCall = new LlmToolCall("call_1", "fake_lookup", JsonNodeFactory.instance.objectNode());
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(toolCall),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    List<String> observed = new ArrayList<>();
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onToolError(
          AgentLoopContext context,
          int stepIndex,
          LlmToolCall toolCall,
          AgentException error
      ) {
        observed.add(stepIndex + ":" + toolCall.id() + ":" + error.code());
      }
    };
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.of(List.of(new FailingTool("fake_lookup"))),
        LlmToolChoice.auto(),
        4,
        List.of(observer),
        List.of());

    collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(observed).containsExactly("1:call_1:TOOL_EXECUTION_FAILED");
  }

  @Test
  void notifiesObserverInLifecycleOrderAndKeepsStreamEventContract() {
    FakeGateway gateway = new FakeGateway();
    LlmToolCall toolCall = new LlmToolCall(
        "call_1",
        "fake_lookup",
        JsonNodeFactory.instance.objectNode());
    gateway.steps.add(List.of(
        new LlmStreamEvent.MessageStart(LlmProviderId.of("test"), LlmModelId.of("gpt-test")),
        new LlmStreamEvent.ToolCallEnd(toolCall),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("done"),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    List<String> observed = new ArrayList<>();
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onRunStart(AgentLoopContext context) {
        observed.add("run-start");
      }

      @Override
      public void onStepStart(AgentLoopContext context, int stepIndex) {
        observed.add("step-start-" + stepIndex);
      }

      @Override
      public void onLlmRequestReady(AgentLoopContext context, int stepIndex, LlmCompletionRequest request) {
        observed.add("request-ready-" + stepIndex + "-" + request.messages().size());
      }

      @Override
      public void onLlmEvent(AgentLoopContext context, int stepIndex, LlmStreamEvent event) {
        observed.add("llm-" + stepIndex + "-" + event.getClass().getSimpleName());
      }

      @Override
      public void onStepEnd(AgentLoopContext context, int stepIndex, AgentStepResult result) {
        observed.add("step-end-" + stepIndex + "-" + result.finishReason());
      }

      @Override
      public void onFinalOutput(AgentLoopContext context, AgentOutput output) {
        observed.add("final-output-" + output.text());
      }

      @Override
      public void onToolStart(AgentLoopContext context, int stepIndex, LlmToolCall toolCall) {
        observed.add("tool-start-" + toolCall.name());
      }

      @Override
      public void onToolEnd(AgentLoopContext context, int stepIndex, LlmToolCall toolCall, JsonNode result) {
        observed.add("tool-end-" + result.get("summary").asText());
      }

      @Override
      public void onRunEnd(AgentLoopContext context, AgentRunResult result) {
        observed.add("run-end-" + result.steps());
      }
    };
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.of(List.of(new FakeTool(
            "fake_lookup",
            JsonNodeFactory.instance.objectNode().put("summary", "tool data")))),
        LlmToolChoice.auto(),
        4,
        List.of(observer),
        List.of());

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(events)
        .extracting(AgentStreamEvent::name)
        .containsExactly(
            "agent_run_start",
            "agent_step_start",
            "message_start",
            "tool_call_end",
            "message_end",
            "agent_step_end",
            "agent_tool_start",
            "agent_tool_end",
            "agent_step_start",
            "content_delta",
            "message_end",
            "agent_step_end",
            "agent_run_end");
    assertThat(observed).containsExactly(
        "run-start",
        "step-start-1",
        "request-ready-1-1",
        "llm-1-MessageStart",
        "llm-1-ToolCallEnd",
        "llm-1-MessageEnd",
        "step-end-1-TOOL_CALLS",
        "tool-start-fake_lookup",
        "tool-end-tool data",
        "step-start-2",
        "request-ready-2-3",
        "llm-2-ContentDelta",
        "llm-2-MessageEnd",
        "step-end-2-STOP",
        "final-output-done",
        "run-end-2");
  }

  @Test
  void notifiesFinalLlmRequestAfterInterceptorsAndBeforeGatewayCall() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    List<String> order = new ArrayList<>();
    List<LlmCompletionRequest> observedRequests = new ArrayList<>();
    AgentLoopInterceptor interceptor = new AgentLoopInterceptor() {
      @Override
      public LlmCompletionRequest beforeLlmRequest(
          AgentLoopContext context,
          int stepIndex,
          LlmCompletionRequest request
      ) {
        order.add("interceptor");
        return new LlmCompletionRequest(
            request.modelSelector(),
            request.messages(),
            request.options(),
            request.tools(),
            request.toolChoice(),
            request.responseFormat(),
            Map.of("afterInterceptor", true));
      }
    };
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onLlmRequestReady(AgentLoopContext context, int stepIndex, LlmCompletionRequest request) {
        order.add("observer");
        observedRequests.add(request);
      }
    };
    gateway.beforeStream = () -> order.add("gateway");
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        LlmToolChoice.auto(),
        4,
        List.of(observer),
        List.of(interceptor));

    collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(order).containsSubsequence("interceptor", "observer", "gateway");
    assertThat(observedRequests).hasSize(1);
    assertThat(observedRequests.get(0).metadata()).containsEntry("afterInterceptor", true);
  }

  @Test
  void observerFailureDoesNotFailRun() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    AgentLoopObserver failingObserver = new AgentLoopObserver() {
      @Override
      public void onStepStart(AgentLoopContext context, int stepIndex) {
        throw new IllegalStateException("observer failed");
      }
    };
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        LlmToolChoice.auto(),
        4,
        List.of(failingObserver),
        List.of());

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(events).extracting(AgentStreamEvent::name).endsWith("agent_run_end");
  }

  @Test
  void interceptorsCanRewriteLlmRequestToolCallAndToolResultInOrder() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ToolCallEnd(
            new LlmToolCall("call_1", "original_tool", JsonNodeFactory.instance.objectNode().put("value", "raw"))),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of())));
    gateway.steps.add(List.of(new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    FakeTool tool = new FakeTool("rewritten_tool", JsonNodeFactory.instance.objectNode().put("summary", "raw result"));
    AgentLoopInterceptor interceptor = new AgentLoopInterceptor() {
      @Override
      public LlmCompletionRequest beforeLlmRequest(
          AgentLoopContext context,
          int stepIndex,
          LlmCompletionRequest request
      ) {
        return new LlmCompletionRequest(
            request.modelSelector(),
            request.messages(),
            new LlmGenerationOptions(0.2, null, null, List.of(), null, null),
            request.tools(),
            request.toolChoice(),
            request.responseFormat(),
            Map.of("step", stepIndex));
      }

      @Override
      public LlmToolCall beforeToolCall(AgentLoopContext context, int stepIndex, LlmToolCall toolCall) {
        ObjectNode arguments = JsonNodeFactory.instance.objectNode();
        arguments.put("value", toolCall.arguments().get("value").asText());
        arguments.put("approved", true);
        return new LlmToolCall(toolCall.id(), "rewritten_tool", arguments);
      }

      @Override
      public JsonNode afterToolCall(
          AgentLoopContext context,
          int stepIndex,
          LlmToolCall toolCall,
          JsonNode result
      ) {
        return JsonNodeFactory.instance.objectNode()
            .put("summary", result.get("summary").asText())
            .put("source", "interceptor");
      }
    };
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.of(List.of(tool)),
        LlmToolChoice.auto(),
        4,
        List.of(),
        List.of(interceptor));

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(gateway.requests.get(0).options().temperature()).isEqualTo(0.2);
    assertThat(gateway.requests.get(0).metadata()).containsEntry("step", 1);
    assertThat(tool.executedArguments.get("approved").asBoolean()).isTrue();
    assertThat(gateway.requests.get(1).messages().get(1).toolCalls().get(0).name()).isEqualTo("rewritten_tool");
    LlmContentPart.ToolResult toolResult =
        (LlmContentPart.ToolResult) gateway.requests.get(1).messages().get(2).content().get(0);
    assertThat(toolResult.result().get("source").asText()).isEqualTo("interceptor");
    AgentStreamEvent.AgentToolEnd toolEnd = events.stream()
        .filter(AgentStreamEvent.AgentToolEnd.class::isInstance)
        .map(AgentStreamEvent.AgentToolEnd.class::cast)
        .findFirst()
        .orElseThrow();
    assertThat(toolEnd.toolName()).isEqualTo("rewritten_tool");
    assertThat(toolEnd.result().get("source").asText()).isEqualTo("interceptor");
  }

  @Test
  void interceptorFailureEmitsAgentErrorAndStopsRun() {
    FakeGateway gateway = new FakeGateway();
    AgentLoopInterceptor interceptor = new AgentLoopInterceptor() {
      @Override
      public LlmCompletionRequest beforeLlmRequest(
          AgentLoopContext context,
          int stepIndex,
          LlmCompletionRequest request
      ) {
        throw new AgentException(AgentErrorCode.UNKNOWN, "blocked by interceptor");
      }
    };
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        LlmToolChoice.auto(),
        4,
        List.of(),
        List.of(interceptor));

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(gateway.requests).isEmpty();
    assertThat(events).extracting(AgentStreamEvent::name).containsExactly(
        "agent_run_start",
        "agent_step_start",
        "agent_error");
    AgentStreamEvent.AgentError error = (AgentStreamEvent.AgentError) events.get(2);
    assertThat(error.error().getMessage()).isEqualTo("blocked by interceptor");
  }

  @Test
  void downstreamCancelCancelsCurrentLlmStreamAndMarksRunCancelled() {
    BlockingGateway gateway = new BlockingGateway();
    List<AgentErrorCode> observedErrors = new ArrayList<>();
    CountDownLatch errorObserved = new CountDownLatch(1);
    AgentLoopObserver observer = new AgentLoopObserver() {
      @Override
      public void onError(AgentLoopContext context, AgentException error) {
        observedErrors.add(error.code());
        errorObserved.countDown();
      }
    };
    AgentLoopRunner runner = newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        LlmToolChoice.auto(),
        4,
        List.of(observer),
        List.of());
    CancellingSubscriber subscriber = new CancellingSubscriber();

    runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))).subscribe(subscriber);

    subscriber.awaitStepStart();
    subscriber.subscription.cancel();
    await(errorObserved);

    assertThat(gateway.cancelled).isTrue();
    assertThat(observedErrors).containsExactly(AgentErrorCode.CANCELLED);
  }

  private List<AgentStreamEvent> collect(Flow.Publisher<AgentStreamEvent> publisher) {
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    subscriber.await();
    assertThat(subscriber.error).isNull();
    return subscriber.events;
  }

  private static LlmModelSelector testModelSelector() {
    return new LlmModelSelector(null, LlmModelId.of("gpt-test"), Set.of(), null);
  }

  private static AgentLoopRunner newTestRunner(
      LlmGateway gateway,
      String model,
      AgentToolRegistry toolRegistry,
      int maxSteps
  ) {
    return new AgentLoopRunner(gateway, model, toolRegistry, maxSteps, TEST_EXECUTOR);
  }

  private static AgentLoopRunner newTestRunner(
      LlmGateway gateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      int maxSteps
  ) {
    return new AgentLoopRunner(gateway, modelSelector, toolRegistry, maxSteps, TEST_EXECUTOR);
  }

  private static AgentLoopRunner newTestRunner(
      LlmGateway gateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps
  ) {
    return new AgentLoopRunner(gateway, modelSelector, toolRegistry, toolChoice, maxSteps, TEST_EXECUTOR);
  }

  private static AgentLoopRunner newTestRunner(
      LlmGateway gateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors
  ) {
    return new AgentLoopRunner(
        gateway,
        modelSelector,
        toolRegistry,
        toolChoice,
        maxSteps,
        observers,
        interceptors,
        TEST_EXECUTOR);
  }

  private static AgentLoopRunner newTestRunner(
      LlmGateway gateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors,
      ToolResultCompactionPolicy toolResultPolicy,
      ToolResultStore toolResultStore,
      ObjectMapper objectMapper
  ) {
    return new AgentLoopRunner(
        gateway,
        modelSelector,
        toolRegistry,
        toolChoice,
        maxSteps,
        observers,
        interceptors,
        toolResultPolicy,
        toolResultStore,
        objectMapper,
        null,
        TEST_EXECUTOR);
  }

  private static AgentLoopRunner newTestRunner(
      LlmGateway gateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors,
      ToolResultCompactionPolicy toolResultPolicy,
      ToolResultStore toolResultStore,
      ObjectMapper objectMapper,
      AgentToolPermissionGuard permissionGuard
  ) {
    return new AgentLoopRunner(
        gateway,
        modelSelector,
        toolRegistry,
        toolChoice,
        maxSteps,
        observers,
        interceptors,
        toolResultPolicy,
        toolResultStore,
        objectMapper,
        permissionGuard,
        TEST_EXECUTOR);
  }

  private AgentLoopRunner runnerWithPermissionPlan(
      FakeGateway gateway,
      AgentTool tool,
      AgentToolPermissionDecisionPlan plan,
      Duration timeout
  ) {
    return runnerWithPermissionCoordinator(
        gateway,
        tool,
        plan,
        permissionCoordinator(timeout),
        List.of());
  }

  private AgentLoopRunner runnerWithPermissionCoordinator(
      FakeGateway gateway,
      AgentTool tool,
      AgentToolPermissionDecisionPlan plan,
      InMemoryAgentToolPermissionCoordinator coordinator,
      List<AgentLoopObserver> observers
  ) {
    return newTestRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.of(List.of(tool)),
        LlmToolChoice.auto(),
        4,
        observers,
        List.of(),
        ToolResultCompactionPolicy.defaults(),
        new InMemoryToolResultStore(),
        OBJECT_MAPPER,
        new AgentToolPermissionGuard(
            new AgentToolPermissionHookChain(List.of(new FixedPermissionHook(plan))),
            coordinator));
  }

  private InMemoryAgentToolPermissionCoordinator permissionCoordinator(Duration timeout) {
    return new InMemoryAgentToolPermissionCoordinator(
        new AgentToolPermissionResultFactory(OBJECT_MAPPER),
        timeout,
        java.time.Clock.systemUTC());
  }

  private AgentToolPermissionDecisionPlan askPlan() {
    return AgentToolPermissionDecisionPlan.ask(
        "Fake lookup",
        "需要用户确认",
        Map.of("toolName", "fake_lookup"),
        "test-policy");
  }

  private LlmContentPart.ToolResult toolResultFromSecondLlmRequest(FakeGateway gateway) {
    assertThat(gateway.requests).hasSize(2);
    return (LlmContentPart.ToolResult) gateway.requests.get(1).messages().get(2).content().get(0);
  }

  private void assertEventOrder(List<AgentStreamEvent> events, String... names) {
    int fromIndex = 0;
    List<String> actualNames = events.stream().map(AgentStreamEvent::name).toList();
    for (String name : names) {
      int index = actualNames.subList(fromIndex, actualNames.size()).indexOf(name);
      assertThat(index).as("event %s after index %s in %s", name, fromIndex, actualNames).isNotNegative();
      fromIndex += index + 1;
    }
  }

  private void await(CountDownLatch latch) {
    try {
      assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new AssertionError(ex);
    }
  }

  private static final class CollectingSubscriber implements Flow.Subscriber<AgentStreamEvent> {
    private final List<AgentStreamEvent> events = new ArrayList<>();
    private final java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(1);
    private Throwable error;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AgentStreamEvent item) {
      events.add(item);
    }

    @Override
    public void onError(Throwable throwable) {
      this.error = throwable;
      done.countDown();
    }

    @Override
    public void onComplete() {
      done.countDown();
    }

    private void await() {
      try {
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError(ex);
      }
    }
  }

  private static final class ThreadRecordingSubscriber implements Flow.Subscriber<AgentStreamEvent> {
    private final List<String> callbackThreads = new ArrayList<>();
    private final CountDownLatch done = new CountDownLatch(1);
    private Throwable error;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AgentStreamEvent item) {
      callbackThreads.add(Thread.currentThread().getName());
    }

    @Override
    public void onError(Throwable throwable) {
      error = throwable;
      done.countDown();
    }

    @Override
    public void onComplete() {
      done.countDown();
    }

    private void await() {
      try {
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interrupted);
      }
    }
  }

  private static final class BlockingSubscriber implements Flow.Subscriber<AgentStreamEvent> {
    private final CountDownLatch blocked = new CountDownLatch(1);
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch completed = new CountDownLatch(1);
    private final AtomicBoolean firstEvent = new AtomicBoolean(true);

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AgentStreamEvent item) {
      if (firstEvent.compareAndSet(true, false)) {
        blocked.countDown();
        await(release);
      }
    }

    @Override
    public void onError(Throwable throwable) {
      completed.countDown();
    }

    @Override
    public void onComplete() {
      completed.countDown();
    }

    private void awaitBlocked() {
      await(blocked);
    }

    private void release() {
      release.countDown();
    }

    private void awaitCompletion() {
      await(completed);
    }

    private void await(CountDownLatch latch) {
      try {
        assertThat(latch.await(5, TimeUnit.SECONDS)).isTrue();
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw new AssertionError(interrupted);
      }
    }
  }

  private static final class CancellingOnSubscribeSubscriber implements Flow.Subscriber<AgentStreamEvent> {

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.cancel();
    }

    @Override
    public void onNext(AgentStreamEvent item) {
    }

    @Override
    public void onError(Throwable throwable) {
    }

    @Override
    public void onComplete() {
    }
  }

  private static final class CancellingSubscriber implements Flow.Subscriber<AgentStreamEvent> {
    private final List<AgentStreamEvent> events = new ArrayList<>();
    private final CountDownLatch stepStarted = new CountDownLatch(1);
    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AgentStreamEvent item) {
      events.add(item);
      if (item instanceof AgentStreamEvent.AgentStepStart) {
        stepStarted.countDown();
      }
    }

    @Override
    public void onError(Throwable throwable) {
    }

    @Override
    public void onComplete() {
    }

    private void awaitStepStart() {
      try {
        assertThat(stepStarted.await(5, TimeUnit.SECONDS)).isTrue();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AssertionError(ex);
      }
    }
  }

  private final class PermissionDecisionSubscriber implements Flow.Subscriber<AgentStreamEvent> {
    private final List<AgentStreamEvent> events = new java.util.concurrent.CopyOnWriteArrayList<>();
    private final CountDownLatch permissionRequested = new CountDownLatch(1);
    private final CountDownLatch done = new CountDownLatch(1);
    private final AtomicReference<AgentStreamEvent.ToolPermissionRequest> request = new AtomicReference<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription = subscription;
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(AgentStreamEvent item) {
      events.add(item);
      if (item instanceof AgentStreamEvent.ToolPermissionRequest permissionRequest) {
        request.set(permissionRequest);
        permissionRequested.countDown();
      }
    }

    @Override
    public void onError(Throwable throwable) {
      error.set(throwable);
      done.countDown();
    }

    @Override
    public void onComplete() {
      done.countDown();
    }

    private AgentStreamEvent.ToolPermissionRequest awaitPermissionRequest() {
      await(permissionRequested);
      return request.get();
    }

    private List<AgentStreamEvent> awaitCompletion() {
      await(done);
      assertThat(error.get()).isNull();
      return List.copyOf(events);
    }

    private List<AgentStreamEvent> events() {
      return List.copyOf(events);
    }

    private void cancel() {
      subscription.cancel();
    }
  }

  private record FixedPermissionHook(AgentToolPermissionDecisionPlan plan) implements AgentToolPermissionHook {

    @Override
    public int order() {
      return 1;
    }

    @Override
    public AgentToolPermissionDecisionPlan evaluate(
        org.congcong.algomentor.agent.core.permission.AgentToolPermissionCheck check
    ) {
      return plan;
    }
  }

  private static final class FakeGateway implements LlmGateway {
    private final List<LlmCompletionRequest> requests = new ArrayList<>();
    private final List<List<LlmStreamEvent>> steps = new ArrayList<>();
    private Runnable beforeStream = () -> {};

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("Agent loop should use stream calls internally");
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      beforeStream.run();
      requests.add(request);
      List<LlmStreamEvent> events = steps.remove(0);
      return subscriber -> {
        SubmissionPublisher<LlmStreamEvent> publisher = new SubmissionPublisher<>();
        publisher.subscribe(subscriber);
        events.forEach(publisher::submit);
        publisher.close();
      };
    }
  }

  private static final class SynchronousGateway implements LlmGateway {
    private final List<LlmStreamEvent> events;
    private int streamCalls;

    private SynchronousGateway(List<LlmStreamEvent> events) {
      this.events = List.copyOf(events);
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("Agent loop should use stream calls internally");
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      streamCalls++;
      return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
        private boolean completed;
        private boolean cancelled;

        @Override
        public void request(long count) {
          if (completed || cancelled) {
            return;
          }
          if (count <= 0) {
            completed = true;
            subscriber.onError(new IllegalArgumentException("Flow request count must be positive: " + count));
            return;
          }
          completed = true;
          for (LlmStreamEvent event : events) {
            if (cancelled) {
              return;
            }
            subscriber.onNext(event);
          }
          if (!cancelled) {
            subscriber.onComplete();
          }
        }

        @Override
        public void cancel() {
          cancelled = true;
        }
      });
    }
  }

  private static final class BlockingGateway implements LlmGateway {
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("Agent loop should use stream calls internally");
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
        @Override
        public void request(long n) {
        }

        @Override
        public void cancel() {
          cancelled.set(true);
        }
      });
    }
  }

  private static final class FakeTool implements AgentTool {
    private final String name;
    private final JsonNode result;
    private JsonNode executedArguments;
    private int executionCount;

    private FakeTool(String name, JsonNode result) {
      this.name = name;
      this.result = result;
    }

    @Override
    public LlmToolSpec spec() {
      return new LlmToolSpec(name, "Fake lookup", JsonNodeFactory.instance.objectNode(), true);
    }

    @Override
    public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
      executionCount++;
      this.executedArguments = arguments;
      return result;
    }
  }

  private static final class FailingTool implements AgentTool {
    private final String name;

    private FailingTool(String name) {
      this.name = name;
    }

    @Override
    public LlmToolSpec spec() {
      return new LlmToolSpec(name, "Failing lookup", JsonNodeFactory.instance.objectNode(), true);
    }

    @Override
    public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
      throw new IllegalStateException("tool failed");
    }
  }

  private static final class AgentFailingTool implements AgentTool {
    private final String name;

    private AgentFailingTool(String name) {
      this.name = name;
    }

    @Override
    public LlmToolSpec spec() {
      return new LlmToolSpec(name, "Agent failing lookup", JsonNodeFactory.instance.objectNode(), true);
    }

    @Override
    public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
      throw new AgentException(
          AgentErrorCode.TOOL_EXECUTION_FAILED,
          "wrapped tool failure",
          false,
          Map.of("businessKey", "kept"),
          new IllegalStateException("repository failed", new IllegalArgumentException("database rejected query")));
    }
  }
}
