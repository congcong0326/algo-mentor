package org.congcong.algomentor.api.service;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.congcong.algomentor.mentor.application.ExplainTopicUseCase;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AiExplanationService {

  private final ExplainTopicUseCase explainTopicUseCase;

  public AiExplanationService(ExplainTopicUseCase explainTopicUseCase) {
    this.explainTopicUseCase = explainTopicUseCase;
  }

  public SseEmitter streamExplanation(String topic) {
    SseEmitter emitter = new SseEmitter(30_000L);

    CompletableFuture.runAsync(() -> {
      try {
        String explanation = explainTopicUseCase.explain(topic);
        emitter.send(SseEmitter.event()
            .name("explanation")
            .data(explanation));
        emitter.complete();
      } catch (IOException ex) {
        emitter.completeWithError(ex);
      } catch (RuntimeException ex) {
        sendError(emitter, ex);
      }
    });

    return emitter;
  }

  private void sendError(SseEmitter emitter, RuntimeException ex) {
    try {
      emitter.send(SseEmitter.event()
          .name("error")
          .data(ex.getMessage()));
      emitter.complete();
    } catch (IOException sendFailure) {
      emitter.completeWithError(sendFailure);
    }
  }
}
