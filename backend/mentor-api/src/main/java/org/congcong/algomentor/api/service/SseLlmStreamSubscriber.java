package org.congcong.algomentor.api.service;

import java.io.IOException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentException;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public class SseLlmStreamSubscriber implements Flow.Subscriber<AgentStreamEvent> {

  private final SseEmitter emitter;
  private final LlmStreamSseMapper mapper;
  private final AtomicBoolean terminalEventSent = new AtomicBoolean(false);
  private Flow.Subscription subscription;

  public SseLlmStreamSubscriber(SseEmitter emitter, LlmStreamSseMapper mapper) {
    this.emitter = emitter;
    this.mapper = mapper;
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    subscription.request(1);
  }

  @Override
  public void onNext(AgentStreamEvent event) {
    try {
      emitter.send(mapper.toSseEvent(event));
      if (isTerminalEvent(event)) {
        terminalEventSent.set(true);
        emitter.complete();
        cancel();
        return;
      }
      subscription.request(1);
    } catch (IOException | RuntimeException sendFailure) {
      cancel();
      emitter.completeWithError(sendFailure);
    }
  }

  @Override
  public void onError(Throwable throwable) {
    if (terminalEventSent.compareAndSet(false, true)) {
      try {
        emitter.send(mapper.toSseEvent(new AgentStreamEvent.AgentError("unknown", toAgentException(throwable))));
        emitter.complete();
      } catch (IOException | RuntimeException sendFailure) {
        emitter.completeWithError(sendFailure);
      }
    }
  }

  @Override
  public void onComplete() {
    if (terminalEventSent.compareAndSet(false, true)) {
      try {
        emitter.send(mapper.toSseEvent(new AgentStreamEvent.AgentRunEnd(
            "unknown",
            1,
            LlmFinishReason.UNKNOWN,
            null)));
        emitter.complete();
      } catch (IOException | RuntimeException sendFailure) {
        emitter.completeWithError(sendFailure);
      }
    }
  }

  public void cancel() {
    if (subscription != null) {
      subscription.cancel();
    }
  }

  private boolean isTerminalEvent(AgentStreamEvent event) {
    return event instanceof AgentStreamEvent.AgentRunEnd || event instanceof AgentStreamEvent.AgentError;
  }

  private AgentException toAgentException(Throwable throwable) {
    if (throwable instanceof AgentException agentException) {
      return agentException;
    }
    String message = throwable.getMessage() == null ? "Agent stream failed" : throwable.getMessage();
    return new AgentException(AgentErrorCode.UNKNOWN, message, false, null, throwable);
  }
}
