package org.congcong.algomentor.api.service;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AiExplanationService {

  public SseEmitter streamExplanation(String topic) {
    SseEmitter emitter = new SseEmitter(30_000L);

    CompletableFuture.runAsync(() -> {
      try {
        emitter.send(SseEmitter.event()
            .name("explanation")
            .data("AI explanation stream is ready for topic: " + topic));
        emitter.complete();
      } catch (IOException ex) {
        emitter.completeWithError(ex);
      }
    });

    return emitter;
  }
}

