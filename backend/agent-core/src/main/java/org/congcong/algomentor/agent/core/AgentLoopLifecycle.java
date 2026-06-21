package org.congcong.algomentor.agent.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.SubmissionPublisher;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolCall;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class AgentLoopLifecycle {

  private static final Logger log = LoggerFactory.getLogger(AgentLoopLifecycle.class);

  private final SubmissionPublisher<AgentStreamEvent> publisher;
  private final List<AgentLoopObserver> observers;
  private final List<AgentLoopInterceptor> interceptors;

  public AgentLoopLifecycle(
      SubmissionPublisher<AgentStreamEvent> publisher,
      List<AgentLoopObserver> observers,
      List<AgentLoopInterceptor> interceptors
  ) {
    this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
    this.observers = observers == null ? List.of() : List.copyOf(observers);
    this.interceptors = interceptors == null ? List.of() : List.copyOf(interceptors);
  }

  public void runStarted(AgentLoopContext context) {
    notifyObserver(observer -> observer.onRunStart(context), "onRunStart");
    publisher.submit(new AgentStreamEvent.AgentRunStart(
        context.runId(),
        context.request().displayTitle(),
        context.maxSteps(),
        context.metadata()));
  }

  public void stepStarted(AgentLoopContext context, int stepIndex) {
    notifyObserver(observer -> observer.onStepStart(context, stepIndex), "onStepStart");
    publisher.submit(new AgentStreamEvent.AgentStepStart(context.runId(), stepIndex));
  }

  public LlmCompletionRequest beforeLlmRequest(
      AgentLoopContext context,
      int stepIndex,
      LlmCompletionRequest request
  ) {
    LlmCompletionRequest effectiveRequest = Objects.requireNonNull(request, "request must not be null");
    for (AgentLoopInterceptor interceptor : interceptors) {
      effectiveRequest = Objects.requireNonNull(
          interceptor.beforeLlmRequest(context, stepIndex, effectiveRequest),
          "Agent loop interceptor returned null LLM request");
    }
    return effectiveRequest;
  }

  public void llmRequestReady(
      AgentLoopContext context,
      int stepIndex,
      LlmCompletionRequest request
  ) {
    notifyObserver(observer -> observer.onLlmRequestReady(context, stepIndex, request), "onLlmRequestReady");
  }

  public void llmEvent(AgentLoopContext context, int stepIndex, LlmStreamEvent event) {
    notifyObserver(observer -> observer.onLlmEvent(context, stepIndex, event), "onLlmEvent");
    publisher.submit(AgentStreamEvent.fromLlm(event));
  }

  public void stepEnded(AgentLoopContext context, int stepIndex, AgentStepResult result) {
    notifyObserver(observer -> observer.onStepEnd(context, stepIndex, result), "onStepEnd");
    publisher.submit(new AgentStreamEvent.AgentStepEnd(
        context.runId(),
        stepIndex,
        result.finishReason(),
        result.toolCalls().size()));
  }

  public LlmToolCall beforeToolCall(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall
  ) {
    LlmToolCall effectiveToolCall = Objects.requireNonNull(toolCall, "toolCall must not be null");
    for (AgentLoopInterceptor interceptor : interceptors) {
      effectiveToolCall = Objects.requireNonNull(
          interceptor.beforeToolCall(context, stepIndex, effectiveToolCall),
          "Agent loop interceptor returned null tool call");
    }
    return effectiveToolCall;
  }

  public void toolStarted(AgentLoopContext context, int stepIndex, LlmToolCall toolCall) {
    notifyObserver(observer -> observer.onToolStart(context, stepIndex, toolCall), "onToolStart");
    publisher.submit(new AgentStreamEvent.AgentToolStart(
        context.runId(),
        stepIndex,
        toolCall.id(),
        toolCall.name()));
  }

  public JsonNode afterToolCall(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      JsonNode result
  ) {
    JsonNode effectiveResult = Objects.requireNonNull(result, "result must not be null");
    for (AgentLoopInterceptor interceptor : interceptors) {
      effectiveResult = Objects.requireNonNull(
          interceptor.afterToolCall(context, stepIndex, toolCall, effectiveResult),
          "Agent loop interceptor returned null tool result");
    }
    return effectiveResult;
  }

  public void toolEnded(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      JsonNode result
  ) {
    notifyObserver(observer -> observer.onToolEnd(context, stepIndex, toolCall, result), "onToolEnd");
    publisher.submit(new AgentStreamEvent.AgentToolEnd(
        context.runId(),
        stepIndex,
        toolCall.id(),
        toolCall.name(),
        result));
  }

  public void toolErrored(
      AgentLoopContext context,
      int stepIndex,
      LlmToolCall toolCall,
      AgentException error
  ) {
    notifyObserver(observer -> observer.onToolError(context, stepIndex, toolCall, error), "onToolError");
  }

  public void runEnded(AgentLoopContext context, AgentRunResult result) {
    notifyObserver(observer -> observer.onRunEnd(context, result), "onRunEnd");
    publisher.submit(new AgentStreamEvent.AgentRunEnd(
        context.runId(),
        result.steps(),
        result.finishReason(),
        result.metadata()));
  }

  public void error(AgentLoopContext context, AgentException error) {
    notifyObserver(observer -> observer.onError(context, error), "onError");
    publisher.submit(new AgentStreamEvent.AgentError(context.runId(), error));
  }

  private void notifyObserver(ObserverCallback callback, String methodName) {
    for (AgentLoopObserver observer : observers) {
      try {
        callback.call(observer);
      } catch (RuntimeException ex) {
        log.warn("Agent loop observer failed in {}", methodName, ex);
      }
    }
  }

  @FunctionalInterface
  private interface ObserverCallback {
    void call(AgentLoopObserver observer);
  }
}
