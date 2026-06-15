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
  private final int maxSteps;

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
    if (maxSteps < 1) {
      throw new IllegalArgumentException("Agent loop max steps must be positive");
    }
    this.llmGateway = Objects.requireNonNull(llmGateway, "llmGateway must not be null");
    this.modelSelector = Objects.requireNonNull(modelSelector, "modelSelector must not be null");
    this.toolRegistry = toolRegistry == null ? AgentToolRegistry.empty() : toolRegistry;
    this.maxSteps = maxSteps;
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
    String runId = UUID.randomUUID().toString();
    List<LlmMessage> messages = new ArrayList<>(AgentLlmRequestFactory.initialMessages(request));
    publisher.submit(new AgentStreamEvent.AgentRunStart(runId, request.topic().title(), maxSteps));
    try {
      for (int stepIndex = 1; stepIndex <= maxSteps; stepIndex++) {
        StepResult stepResult = runStep(runId, stepIndex, request, messages, publisher);
        if (!stepResult.requiresTools()) {
          publisher.submit(new AgentStreamEvent.AgentRunEnd(
              runId,
              stepIndex,
              stepResult.finishReason(),
              Map.of()));
          publisher.close();
          return;
        }
        messages.add(LlmMessage.assistant());
        for (LlmToolCall toolCall : stepResult.toolCalls()) {
          AgentTool tool = toolRegistry.find(toolCall.name())
              .orElseThrow(() -> new AgentException(
                  AgentErrorCode.UNKNOWN_TOOL,
                  "Unknown agent tool: " + toolCall.name(),
                  false,
                  Map.of("toolName", toolCall.name(), "toolCallId", toolCall.id()),
                  null));
          publisher.submit(new AgentStreamEvent.AgentToolStart(runId, stepIndex, toolCall.id(), toolCall.name()));
          var result = executeTool(runId, stepIndex, request, toolCall, tool);
          publisher.submit(new AgentStreamEvent.AgentToolEnd(runId, stepIndex, toolCall.id(), toolCall.name(), result));
          messages.add(LlmMessage.toolResult(toolCall.id(), result));
        }
      }
      publisher.submit(new AgentStreamEvent.AgentError(
          runId,
          new AgentException(
              AgentErrorCode.MAX_STEPS_EXCEEDED,
              "Agent loop exceeded max steps",
              false,
              Map.of("maxSteps", maxSteps),
              null)));
      publisher.close();
    } catch (AgentException ex) {
      publisher.submit(new AgentStreamEvent.AgentError(runId, ex));
      publisher.close();
    } catch (RuntimeException ex) {
      publisher.submit(new AgentStreamEvent.AgentError(
          runId,
          new AgentException(AgentErrorCode.UNKNOWN, "Agent loop failed", false, Map.of(), ex)));
      publisher.close();
    }
  }

  private com.fasterxml.jackson.databind.JsonNode executeTool(
      String runId,
      int stepIndex,
      AgentRequest request,
      LlmToolCall toolCall,
      AgentTool tool
  ) {
    try {
      return tool.execute(
          toolCall.arguments(),
          new AgentExecutionContext(runId, stepIndex, request.topic(), false));
    } catch (AgentException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new AgentException(
          AgentErrorCode.TOOL_EXECUTION_FAILED,
          "Agent tool execution failed: " + toolCall.name(),
          false,
          Map.of("toolName", toolCall.name(), "toolCallId", toolCall.id()),
          ex);
    }
  }

  private StepResult runStep(
      String runId,
      int stepIndex,
      AgentRequest request,
      List<LlmMessage> messages,
      SubmissionPublisher<AgentStreamEvent> publisher
  ) {
    publisher.submit(new AgentStreamEvent.AgentStepStart(runId, stepIndex));
    LlmCompletionRequest llmRequest = AgentLlmRequestFactory.build(modelSelector, messages, toolRegistry.specs());
    StepCollector collector = new StepCollector(publisher);
    try {
      llmGateway.stream(llmRequest).subscribe(collector);
      collector.await();
    } catch (RuntimeException ex) {
      throw toAgentException(ex);
    }
    if (collector.error.get() != null) {
      throw toAgentException(collector.error.get());
    }
    StepResult result = collector.result();
    publisher.submit(new AgentStreamEvent.AgentStepEnd(
        runId,
        stepIndex,
        result.finishReason(),
        result.toolCalls().size()));
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

  private record StepResult(List<LlmToolCall> toolCalls, LlmFinishReason finishReason) {
    private StepResult {
      toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
      finishReason = finishReason == null ? LlmFinishReason.UNKNOWN : finishReason;
    }

    private boolean requiresTools() {
      return finishReason == LlmFinishReason.TOOL_CALLS && !toolCalls.isEmpty();
    }
  }

  private static final class StepCollector implements Flow.Subscriber<LlmStreamEvent> {
    private final SubmissionPublisher<AgentStreamEvent> publisher;
    private final CountDownLatch done = new CountDownLatch(1);
    private final List<LlmToolCall> toolCalls = new ArrayList<>();
    private final AtomicReference<Throwable> error = new AtomicReference<>();
    private LlmFinishReason finishReason = LlmFinishReason.UNKNOWN;

    private StepCollector(SubmissionPublisher<AgentStreamEvent> publisher) {
      this.publisher = publisher;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(LlmStreamEvent item) {
      publisher.submit(AgentStreamEvent.fromLlm(item));
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

    private StepResult result() {
      return new StepResult(toolCalls, finishReason);
    }
  }
}
