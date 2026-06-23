package org.congcong.algomentor.api.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmission;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionService;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunContext;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.mentor.application.ExplainTopicUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@ConditionalOnBean(AiRunAdmissionService.class)
public class AiExplanationService {

  private final ExplainTopicUseCase explainTopicUseCase;
  private final LlmStreamSseMapper sseMapper;
  private final AiActorResolver actorResolver;
  private final AiRunAdmissionService admissionService;

  public AiExplanationService(
      ExplainTopicUseCase explainTopicUseCase,
      LlmStreamSseMapper sseMapper,
      AiActorResolver actorResolver,
      AiRunAdmissionService admissionService) {
    this.explainTopicUseCase = explainTopicUseCase;
    this.sseMapper = sseMapper;
    this.actorResolver = actorResolver;
    this.admissionService = admissionService;
  }

  public SseEmitter streamExplanation(String topic) {
    AiRunAdmission admission = admissionService.admit(new AiRunContext(
        UUID.randomUUID().toString(),
        actorResolver.currentActor(),
        AiPurpose.PROBLEM_EXPLANATION,
        AiRunSource.PROBLEM_DETAIL,
        null,
        topic.getBytes(StandardCharsets.UTF_8).length,
        true,
        Map.of("topicCharCount", topic.length()),
        Instant.now()));
    SseEmitter emitter = new SseEmitter(30_000L);
    SseLlmStreamSubscriber subscriber = new SseLlmStreamSubscriber(emitter, sseMapper);

    emitter.onCompletion(subscriber::cancel);
    emitter.onTimeout(subscriber::cancel);
    emitter.onError(ignored -> subscriber.cancel());

    try {
      explainTopicUseCase.stream(topic, admission.metadata()).subscribe(subscriber);
    } catch (RuntimeException ex) {
      subscriber.onError(ex);
    }
    return emitter;
  }
}
