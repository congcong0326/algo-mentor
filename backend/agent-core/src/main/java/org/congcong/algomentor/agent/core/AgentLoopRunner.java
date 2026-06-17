package org.congcong.algomentor.agent.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.model.LlmModelSelector;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.congcong.algomentor.llm.core.tool.LlmToolChoice;

public class AgentLoopRunner {

  private final LlmGateway llmGateway;
  private final LlmModelSelector modelSelector;
  private final AgentToolRegistry toolRegistry;
  private final LlmToolChoice toolChoice;
  private final int maxSteps;
  private final List<AgentLoopObserver> observers;
  private final List<AgentLoopInterceptor> interceptors;

  @Deprecated(forRemoval = false)
  public AgentLoopRunner(LlmGateway llmGateway, String model, AgentToolRegistry toolRegistry, int maxSteps) {
    this(llmGateway, selectorFromModel(model), toolRegistry, maxSteps);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      int maxSteps
  ) {
    this(llmGateway, modelSelector, toolRegistry, null, maxSteps);
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps
  ) {
    this(llmGateway, modelSelector, toolRegistry, toolChoice, maxSteps, List.of(), List.of());
  }

  public AgentLoopRunner(
      LlmGateway llmGateway,
      LlmModelSelector modelSelector,
      AgentToolRegistry toolRegistry,
      LlmToolChoice toolChoice,
      int maxSteps,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors
  ) {
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Agent loop max steps must be positive");
    }
    this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway must not be null");
    this.modelSelector = Objects.requireNonNull(modelSelector, "modelSelector must not be null");
    this.toolRegistry = toolRegistry == null ? AgentToolRegistry.empty() : toolRegistry;
    this.toolChoice = toolChoice == null ? LlmToolChoice.auto() : toolChoice;
    this.maxSteps = maxSteps;
    this.observers = observers == null ? List.of() : List.copyOf(observers);
    this.interceptors = interceptors == null ? List.of() : List.copyOf(interceptors);
  }

  public Flow.Publisher<AgentStreamEvent> stream(AgentRequest request) {
    Objects.requireNonNull(request, "request must not be null");
    return subscriber -> {
      SubmissionPublisher<AgentStreamEvent> publisher = new SubmissionPublisher<>();
      publisher.subscribe(subscriber);
      Thread worker = new Thread(() -> runLoop(request, publisher), "agent-loop-stream");
      worker.setDaemon(true);
      worker.start();
    };
  }

  private void runLoop(AgentRequest request, SubmissionPublisher<AgentStreamEvent> publisher) {
    AgentLoopContext context = new AgentLoopContext(
        request.runId() == null ? UUID.randomUUID().toString() : request.runId(),
        request,
        maxSteps,
        request.metadata());
    AgentLoopLifecycle lifecycle = new AgentLoopLifecycle(publisher, observers, interceptors);
    List<LlmMessage> messages = new ArrayList<>(AgentLlmRequestFactory.initialMessages(request));
    lifecycle.runStarted(context);
    try {
      for (int stepIndex = 1; stepIndex <= maxSteps; stepIndex++) {
        AgentStepResult stepResult = runStep(context, stepIndex, messages, lifecycle);
        if (!stepResult.requiresTools()) {
          lifecycle.runEnded(context, new AgentRunResult(
              stepIndex,
              stepResult.finishReason(),
              Map.of()));
          publisher.close();
          return;
        }
        List<LlmToolCall> effectiveToolCalls = new ArrayList<>();
        for (LlmToolCall toolCall : stepResult.toolCalls()) {
          effectiveToolCalls.add(lifecycle.beforeToolCall(context, stepIndex, toolCall));
        }
        messages.add(LlmMessage.assistantToolCalls(effectiveToolCalls));
        for (LlmToolCall toolCall : effectiveToolCalls) {
          AgentTool tool = toolRegistry.find(toolCall.name())
              .orElseThrow(() -> new AgentException(
                  AgentErrorCode.UNKNOWN_TOOL,
                  "Unknown agent tool: " + toolCall.name(),
                  false,
                  Map.of("toolName", toolCall.name(), "toolCallId", toolCall.id()),
                  null));
          lifecycle.toolStarted(context, stepIndex, toolCall);
          var result = executeTool(context, stepIndex, toolCall, tool, lifecycle);
          result = lifecycle.afterToolCall(context, stepIndex, toolCall, result);
          lifecycle.toolEnded(context, stepIndex, toolCall, result);
          messages.add(LlmMessage.toolResult(toolCall.id(), result));
        }
      }
      lifecycle.error(context, new AgentException(
          AgentErrorCode.MAX_STEPS_EXCEEDED,
          "Agent loop exceeded max steps",
          false,
          Map.of("maxSteps", maxSteps),
          null));
      publisher.close();
    } catch (AgentException ex) {
      lifecycle.error(context, ex);
      publisher.close();
    } catch (RuntimeException ex) {
      lifecycle.error(context, new AgentException(AgentErrorCode.UNKNOWN, "Agent loop failed", false, Map.of(), ex));
      publisher.close();
    }
  }

  private com.fasterxml.jackson.databind.JsonNode executeTool(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      AgentTool tool,
      AgentLoopLifecycle lifecycle
  ) {
    try {
      return tool.execute(
          toolCall.arguments(),
          new AgentExecutionContext(context.runId(), stepIndex, context.request().metadata(), false));
    } catch (AgentException ex) {
      lifecycle.toolErrored(context, stepIndex, toolCall, ex);
      throw ex;
    } catch (RuntimeException ex) {
      AgentException error = new AgentException(
          AgentErrorCode.TOOL_EXECUTION_FAILED,
          "Agent tool execution failed: " + toolCall.name(),
          false,
          Map.of("toolName", toolCall.name(), "toolCallId", toolCall.id()),
          ex);
      lifecycle.toolErrored(context, stepIndex, toolCall, error);
      throw error;
    }
  }

  private AgentStepResult runStep(
      AgentLoopContext context,
      int stepIndex,
      List<LlmMessage> messages,
      AgentLoopLifecycle lifecycle
  ) {
    lifecycle.stepStarted(context, stepIndex);
    LlmCompletionRequest llmRequest = lifecycle.beforeLlmRequest(context, stepIndex, AgentLlmRequestFactory.build(
        modelSelector,
        messages,
        toolRegistry.specs(),
        toolChoice,
        context.request().metadata()));
    lifecycle.llmRequestReady(context, stepIndex, llmRequest);
    StepCollector collector = new StepCollector(context, stepIndex, lifecycle);
    try {
      llmGateway.stream(llmRequest).subscribe(collector);
      collector.await();
    } catch (RuntimeException ex) {
      throw toAgentException(ex);
    }
    if (collector.error.get() != null) {
      throw toAgentException(collector.error.get());
    }
    AgentStepResult result = collector.result();
    lifecycle.stepEnded(context, stepIndex, result);
    return result;
  }

  private static LlmModelSelector selectorFromModel(String model) {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("Agent loop model must not be blank");
    }
    return new LlmModelSelector(null, LlmModelId.of(model), Set.of(), "topic-explanation");
  }

  private AgentException toAgentException(Throwable throwable) {
    if (throwable instanceof AgentException agentException) {
      return agentException;
    }
    if (throwable instanceof LlmException llmException) {
      return new AgentException(
          AgentErrorCode.LLM_STREAM_FAILED,
          llmException.getMessage(),
          llmException.retryable(),
          llmException.metadata(),
          llmException);
    }
    return new AgentException(AgentErrorCode.UNKNOWN, "Agent loop failed", false, Map.of(), throwable);
  }

  private static final class StepCollector implements Flow.Subscriber<LlmStreamEvent> {
    private final AgentLoopContext context;
    private final int stepIndex;
    private final AgentLoopLifecycle lifecycle;
    private final CountDownLatch done = new CountDownLatch(1);
    private final List<LlmToolCall> toolCalls = new ArrayList<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private LlmFinishReason finishReason = LlmFinishReason.UNKNOWN;

    private StepCollector(AgentLoopContext context, int stepIndex, AgentLoopLifecycle lifecycle) {
      this.context = context;
      this.stepIndex = stepIndex;
      this.lifecycle = lifecycle;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(LlmStreamEvent item) {
      lifecycle.llmEvent(context, stepIndex, item);
      if (item instanceof LlmStreamEvent.ToolCallEnd toolCallEnd) {
        toolCalls.add(toolCallEnd.toolCall());
      }
      if (item instanceof LlmStreamEvent.MessageEnd messageEnd) {
        finishReason = messageEnd.finishReason();
      }
      if (item instanceof LlmStreamEvent.Error llmError) {
        error.compareAndSet(null, llmError.error());
      }
    }

    @Override
    public void onError(Throwable throwable) {
      error.compareAndSet(null, throwable);
      done.countDown();
    }

    @Override
    public void onComplete() {
      done.countDown();
    }

    private void await() {
      try {
        done.await();
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new AgentException(AgentErrorCode.CANCELLED, "Agent loop was interrupted", false, Map.of(), ex);
      }
    }

    private AgentStepResult result() {
      return new AgentStepResult(toolCalls, finishReason);
    }
  }
}
