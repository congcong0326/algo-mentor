package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.agent.core.toolresult.InMemoryToolResultStore;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmContentPart;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
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

  @Test
  void streamsOneStepWhenNoToolIsRequested() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.MessageStart(LlmProviderId.of("test"), LlmModelId.of("gpt-test")),
        new LlmStreamEvent.ContentDelta("Use two indices."),
        new LlmStreamEvent.Usage(LlmUsage.empty()),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
  void emitsAgentErrorWhenRequiredStructuredOutputIsInvalidJson() {
    FakeGateway gateway = new FakeGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("not-json"),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    AgentLoopRunner runner = new AgentLoopRunner(
        gateway,
        testModelSelector(),
        AgentToolRegistry.empty(),
        LlmToolChoice.auto(),
        4,
        List.of(),
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
    assertThat(error.error().metadata()).containsEntry("schemaName", "learning_plan_draft");
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
        gateway,
        new LlmModelSelector(null, LlmModelId.of("gpt-test"), Set.of(), null),
        AgentToolRegistry.empty(),
        4);

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(List.of(LlmMessage.user("two pointers")))));

    assertThat(events.get(events.size() - 1)).isInstanceOf(AgentStreamEvent.AgentError.class);
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
    AgentLoopRunner runner = new AgentLoopRunner(
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
