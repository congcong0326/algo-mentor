package org.congcong.algomentor.api.service;

import org.congcong.algomentor.mentor.application.ExplainTopicUseCase;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class AiExplanationService {

  private final ExplainTopicUseCase explainTopicUseCase;
  private final LlmStreamSseMapper sseMapper;

  public AiExplanationService(ExplainTopicUseCase explainTopicUseCase, LlmStreamSseMapper sseMapper) {
    this.explainTopicUseCase = explainTopicUseCase;
    this.sseMapper = sseMapper;
  }

  public SseEmitter streamExplanation(String topic) {
    SseEmitter emitter = new SseEmitter(30_000L);
    SseLlmStreamSubscriber subscriber = new SseLlmStreamSubscriber(emitter, sseMapper);

    emitter.onCompletion(subscriber::cancel);
    emitter.onTimeout(subscriber::cancel);
    emitter.onError(ignored -> subscriber.cancel());

    try {
      explainTopicUseCase.stream(topic).subscribe(subscriber);
    } catch (RuntimeException ex) {
      subscriber.onError(ex);
    }
    return emitter;
  }
}
