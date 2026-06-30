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
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.NoopOpsRecorders;
import org.congcong.algomentor.ops.observability.SseOpsRecorder;
import org.congcong.algomentor.ops.observability.SseStreamType;
import org.congcong.algomentor.ops.observability.StructuredOpsLogger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
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
  private final SseOpsRecorder sseOpsRecorder;
  private final LearningOpsRecorder learningOpsRecorder;
  private final StructuredOpsLogger opsLogger;

  public AiExplanationService(
      ExplainTopicUseCase explainTopicUseCase,
      LlmStreamSseMapper sseMapper,
      AiActorResolver actorResolver,
      AiRunAdmissionService admissionService) {
    this(
        explainTopicUseCase,
        sseMapper,
        actorResolver,
        admissionService,
        NoopOpsRecorders.sse(),
        NoopOpsRecorders.learning(),
        new StructuredOpsLogger());
  }

  @Autowired
  public AiExplanationService(
      ExplainTopicUseCase explainTopicUseCase,
      LlmStreamSseMapper sseMapper,
      AiActorResolver actorResolver,
      AiRunAdmissionService admissionService,
      ObjectProvider<SseOpsRecorder> sseOpsRecorder,
      ObjectProvider<LearningOpsRecorder> learningOpsRecorder) {
    this(
        explainTopicUseCase,
        sseMapper,
        actorResolver,
        admissionService,
        sseOpsRecorder.getIfAvailable(NoopOpsRecorders::sse),
        learningOpsRecorder.getIfAvailable(NoopOpsRecorders::learning),
        new StructuredOpsLogger());
  }

  private AiExplanationService(
      ExplainTopicUseCase explainTopicUseCase,
      LlmStreamSseMapper sseMapper,
      AiActorResolver actorResolver,
      AiRunAdmissionService admissionService,
      SseOpsRecorder sseOpsRecorder,
      LearningOpsRecorder learningOpsRecorder,
      StructuredOpsLogger opsLogger) {
    this.explainTopicUseCase = explainTopicUseCase;
    this.sseMapper = sseMapper;
    this.actorResolver = actorResolver;
    this.admissionService = admissionService;
    this.sseOpsRecorder = sseOpsRecorder;
    this.learningOpsRecorder = learningOpsRecorder;
    this.opsLogger = opsLogger;
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
    SseLlmStreamSubscriber subscriber = new SseLlmStreamSubscriber(
        emitter,
        sseMapper,
        true,
        SseStreamType.AI_EXPLANATION,
        sseOpsRecorder,
        learningOpsRecorder,
        opsLogger);

    emitter.onCompletion(() -> subscriber.clientDisconnected(null));
    emitter.onTimeout(subscriber::timeout);
    emitter.onError(subscriber::clientDisconnected);

    try {
      explainTopicUseCase.stream(topic, admission.metadata()).subscribe(subscriber);
    } catch (RuntimeException ex) {
      subscriber.onError(ex);
    }
    return emitter;
  }
}
