package org.congcong.algomentor.api.service;

import java.io.IOException;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.llm.core.exception.LlmErrorCode;
import org.congcong.algomentor.llm.core.exception.LlmException;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseLlmStreamSubscriber implements Flow.Subscriber<LlmStreamEvent> {

  private final SseEmitter emitter;
  private final LlmStreamSseMapper mapper;
  private final AtomicBoolean terminalEventSent = new AtomicBoolean(false);
  private Flow.Subscription subscription;

  SseLlmStreamSubscriber(SseEmitter emitter, LlmStreamSseMapper mapper) {
    this.emitter = emitter;
    this.mapper = mapper;
  }

  @Override
  public void onSubscribe(Flow.Subscription subscription) {
    this.subscription = subscription;
    subscription.request(1);
  }

  @Override
  public void onNext(LlmStreamEvent event) {
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
        emitter.send(mapper.toSseEvent(new LlmStreamEvent.Error(toLlmException(throwable))));
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
        emitter.send(mapper.toSseEvent(new LlmStreamEvent.MessageEnd(LlmFinishReason.UNKNOWN, null)));
        emitter.complete();
      } catch (IOException | RuntimeException sendFailure) {
        emitter.completeWithError(sendFailure);
      }
    }
  }

  void cancel() {
    if (subscription != null) {
      subscription.cancel();
    }
  }

  private boolean isTerminalEvent(LlmStreamEvent event) {
    return event instanceof LlmStreamEvent.MessageEnd || event instanceof LlmStreamEvent.Error;
  }

  private LlmException toLlmException(Throwable throwable) {
    if (throwable instanceof LlmException llmException) {
      return llmException;
    }
    String message = throwable.getMessage() == null ? "LLM stream failed" : throwable.getMessage();
    return new LlmException(LlmErrorCode.UNKNOWN, message, throwable);
  }
}
