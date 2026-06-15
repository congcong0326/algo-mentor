package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.TimeUnit;
import org.congcong.algomentor.domain.learning.LearningTopic;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
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

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(LearningTopic.of("two pointers"))));

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
    assertThat(gateway.requests.get(0).modelSelector().purpose()).isEqualTo("topic-explanation");
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

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(LearningTopic.of("two pointers"))));

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
    assertThat(gateway.requests.get(1).messages().get(2).role()).isEqualTo(LlmMessage.Role.TOOL);
    assertThat(gateway.requests.get(1).messages().get(2).toolCallId()).isEqualTo("call_1");
    assertThat(tool.executedArguments).isEqualTo(toolCall.arguments());
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

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(LearningTopic.of("two pointers"))));

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

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(LearningTopic.of("two pointers"))));

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

    List<AgentStreamEvent> events = collect(runner.stream(new AgentRequest(LearningTopic.of("two pointers"))));

    assertThat(events.get(events.size() - 1)).isInstanceOf(AgentStreamEvent.AgentError.class);
    AgentStreamEvent.AgentError error = (AgentStreamEvent.AgentError) events.get(events.size() - 1);
    assertThat(error.error().code()).isEqualTo(AgentErrorCode.TOOL_EXECUTION_FAILED);
    assertThat(error.error().metadata()).containsEntry("toolName", "fake_lookup");
  }

  private List<AgentStreamEvent> collect(Flow.Publisher<AgentStreamEvent> publisher) {
    CollectingSubscriber subscriber = new CollectingSubscriber();
    publisher.subscribe(subscriber);
    subscriber.await();
    assertThat(subscriber.error).isNull();
    return subscriber.events;
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

  private static final class FakeGateway implements LlmGateway {
    private final List<LlmCompletionRequest> requests = new ArrayList<>();
    private final List<List<LlmStreamEvent>> steps = new ArrayList<>();

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("Agent loop should use stream calls internally");
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
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
}
