package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.compaction.RunMessageCompactor;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactionPolicy;
import org.congcong.algomentor.agent.core.compaction.ToolResultCompactor;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionGuard;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionHookChain;
import org.congcong.algomentor.agent.core.permission.AgentToolPermissionResultFactory;
import org.congcong.algomentor.agent.core.permission.InMemoryAgentToolPermissionCoordinator;
import org.congcong.algomentor.agent.core.toolresult.InMemoryToolResultStore;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.junit.jupiter.api.Test;

class AgentLoopEngineTest {

  @Test
  void runsTheLoopSynchronouslyOnTheCallingThread() {
    RecordingGateway gateway = new RecordingGateway();
    gateway.steps.add(List.of(
        new LlmStreamEvent.ContentDelta("Use two indices."),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    AgentLoopEngine engine = engine(gateway);
    List<AgentStreamEvent> events = new ArrayList<>();
    Thread callingThread = Thread.currentThread();

    engine.run(
        new AgentRequest(List.of(LlmMessage.user("two pointers"))),
        AgentLoopExecution.forRuntime(AgentToolRegistry.empty(), List.of(), 5),
        event -> {
          events.add(event);
          return true;
        },
        new AgentCancellationToken());

    assertThat(gateway.streamThread).hasValue(callingThread);
    assertThat(gateway.requests).singleElement().satisfies(request -> {
      assertThat(request.tools()).isEmpty();
      assertThat(request.toolChoice()).isEqualTo(LlmToolChoice.none());
    });
    assertThat(events)
        .extracting(AgentStreamEvent::name)
        .containsExactly(
            AgentStreamEventNames.AGENT_RUN_START,
            AgentStreamEventNames.AGENT_STEP_START,
            AgentStreamEventNames.CONTENT_DELTA,
            AgentStreamEventNames.MESSAGE_END,
            AgentStreamEventNames.AGENT_STEP_END,
            AgentStreamEventNames.AGENT_RUN_END);
  }

  @Test
  void sendsOnlyToolsAllowedForTheCurrentRun() {
    RecordingGateway gateway = new RecordingGateway();
    gateway.steps.add(List.of(new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    AgentLoopEngine engine = engine(gateway);
    AgentToolRegistry registeredTools = AgentToolRegistry.of(List.of(tool("lookup"), tool("calculator")));

    engine.run(
        new AgentRequest(List.of(LlmMessage.user("calculate"))),
        AgentLoopExecution.forRuntime(registeredTools, List.of("calculator"), 4),
        event -> true,
        new AgentCancellationToken());

    assertThat(gateway.requests).singleElement().satisfies(request -> {
      assertThat(request.tools()).extracting(LlmToolSpec::name).containsExactly("calculator");
      assertThat(request.toolChoice()).isEqualTo(LlmToolChoice.auto());
    });
  }

  @Test
  void rejectsGloballyRegisteredButRunLocalForbiddenToolCall() {
    RecordingGateway gateway = new RecordingGateway();
    gateway.steps.add(toolCallStep("calculator", "call_1"));
    AgentLoopEngine engine = engine(gateway);
    TestTool lookup = tool("lookup");
    AgentToolRegistry registeredTools = AgentToolRegistry.of(List.of(lookup, tool("calculator")));
    List<AgentStreamEvent> events = new ArrayList<>();

    engine.run(
        new AgentRequest(List.of(LlmMessage.user("lookup"))),
        AgentLoopExecution.forRuntime(registeredTools, List.of("lookup"), 4),
        event -> {
          events.add(event);
          return true;
        },
        new AgentCancellationToken());

    AgentStreamEvent.AgentError error = (AgentStreamEvent.AgentError) events.get(events.size() - 1);
    assertThat(error.error().code()).isEqualTo(AgentErrorCode.TOOL_NOT_ALLOWED);
    assertThat(lookup.executionCount).isZero();
    assertThat(gateway.requests).singleElement().satisfies(request ->
        assertThat(request.tools()).extracting(LlmToolSpec::name).containsExactly("lookup"));
  }

  @Test
  void keepsUnknownToolCallsDistinctFromForbiddenCalls() {
    RecordingGateway gateway = new RecordingGateway();
    gateway.steps.add(toolCallStep("missing", "call_1"));
    AgentLoopEngine engine = engine(gateway);
    AgentToolRegistry registeredTools = AgentToolRegistry.of(List.of(tool("lookup")));
    List<AgentStreamEvent> events = new ArrayList<>();

    engine.run(
        new AgentRequest(List.of(LlmMessage.user("lookup"))),
        AgentLoopExecution.forRuntime(registeredTools, List.of("lookup"), 4),
        event -> {
          events.add(event);
          return true;
        },
        new AgentCancellationToken());

    AgentStreamEvent.AgentError error = (AgentStreamEvent.AgentError) events.get(events.size() - 1);
    assertThat(error.error().code()).isEqualTo(AgentErrorCode.UNKNOWN_TOOL);
  }

  @Test
  void appliesMaxStepsIndependentlyToEachRun() {
    RecordingGateway gateway = new RecordingGateway();
    gateway.steps.add(toolCallStep("lookup", "call_1"));
    gateway.steps.add(toolCallStep("lookup", "call_2"));
    gateway.steps.add(List.of(new LlmStreamEvent.MessageEnd(LlmFinishReason.STOP, Map.of())));
    AgentLoopEngine engine = engine(gateway);
    TestTool lookup = tool("lookup");
    AgentToolRegistry registeredTools = AgentToolRegistry.of(List.of(lookup));
    List<AgentStreamEvent> firstRunEvents = new ArrayList<>();
    List<AgentStreamEvent> secondRunEvents = new ArrayList<>();

    engine.run(
        new AgentRequest(List.of(LlmMessage.user("first"))),
        AgentLoopExecution.forRuntime(registeredTools, List.of("lookup"), 1),
        event -> {
          firstRunEvents.add(event);
          return true;
        },
        new AgentCancellationToken());
    engine.run(
        new AgentRequest(List.of(LlmMessage.user("second"))),
        AgentLoopExecution.forRuntime(registeredTools, List.of("lookup"), 2),
        event -> {
          secondRunEvents.add(event);
          return true;
        },
        new AgentCancellationToken());

    AgentStreamEvent.AgentError firstError = (AgentStreamEvent.AgentError) firstRunEvents.get(firstRunEvents.size() - 1);
    assertThat(firstError.error().code()).isEqualTo(AgentErrorCode.MAX_STEPS_EXCEEDED);
    assertThat(firstError.error().metadata()).containsEntry("maxSteps", 1);
    assertThat(secondRunEvents.get(secondRunEvents.size() - 1)).isInstanceOf(AgentStreamEvent.AgentRunEnd.class);
    assertThat(gateway.requests).hasSize(3);
    assertThat(lookup.executionCount).isEqualTo(2);
  }

  @Test
  void validatesDefinitionMaxStepsAgainstTheRuntimeHardLimit() {
    assertThatThrownBy(() -> AgentLoopExecution.validateMaxSteps(0, 4))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent loop max steps must be positive");
    assertThatThrownBy(() -> AgentLoopExecution.validateMaxSteps(5, 4))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Agent loop max steps exceeds hard limit");
  }

  private static AgentLoopEngine engine(LlmGateway gateway) {
    ObjectMapper objectMapper = new ObjectMapper();
    ToolResultCompactor toolResultCompactor = new ToolResultCompactor(
        objectMapper,
        ToolResultCompactionPolicy.defaults(),
        new InMemoryToolResultStore());
    return new AgentLoopEngine(
        gateway,
        new AgentLlmRequestFactory(new LlmModelSelector(null, LlmModelId.of("gpt-test"), java.util.Set.of(), null)),
        List.of(),
        List.of(),
        toolResultCompactor,
        new RunMessageCompactor(objectMapper, toolResultCompactor),
        objectMapper,
        new AgentToolPermissionGuard(
            new AgentToolPermissionHookChain(),
            new InMemoryAgentToolPermissionCoordinator(new AgentToolPermissionResultFactory(objectMapper))));
  }

  private static List<LlmStreamEvent> toolCallStep(String toolName, String callId) {
    return List.of(
        new LlmStreamEvent.ToolCallEnd(new LlmToolCall(
            callId,
            toolName,
            JsonNodeFactory.instance.objectNode())),
        new LlmStreamEvent.MessageEnd(LlmFinishReason.TOOL_CALLS, Map.of()));
  }

  private static TestTool tool(String name) {
    return new TestTool(name);
  }

  private static final class RecordingGateway implements LlmGateway {
    private final List<LlmCompletionRequest> requests = new ArrayList<>();
    private final List<List<LlmStreamEvent>> steps = new ArrayList<>();
    private final AtomicReference<Thread> streamThread = new AtomicReference<>();

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      streamThread.set(Thread.currentThread());
      requests.add(request);
      List<LlmStreamEvent> events = steps.remove(0);
      return subscriber -> subscriber.onSubscribe(new Flow.Subscription() {
        private boolean completed;

        @Override
        public void request(long count) {
          if (completed) {
            return;
          }
          completed = true;
          events.forEach(subscriber::onNext);
          subscriber.onComplete();
        }

        @Override
        public void cancel() {
          completed = true;
        }
      });
    }
  }

  private static final class TestTool implements AgentTool {
    private final String name;
    private int executionCount;

    private TestTool(String name) {
      this.name = name;
    }

    @Override
    public LlmToolSpec spec() {
      return new LlmToolSpec(name, name + " tool", JsonNodeFactory.instance.objectNode(), true);
    }

    @Override
    public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
      executionCount++;
      return JsonNodeFactory.instance.objectNode().put("tool", name);
    }
  }
}
