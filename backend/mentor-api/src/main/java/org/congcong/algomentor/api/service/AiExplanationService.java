package org.congcong.algomentor.api.service;

import java.util.Map;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.ai.governance.admission.AiRunAdmissionException;
import org.congcong.algomentor.ai.governance.model.AiActor;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.congcong.algomentor.ai.governance.model.AiRunStatus;
import org.congcong.algomentor.mentor.application.ExplainTopicUseCase;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.NoopOpsRecorders;
import org.congcong.algomentor.ops.observability.SseOpsRecorder;
import org.congcong.algomentor.ops.observability.SseStreamType;
import org.congcong.algomentor.ops.observability.StructuredOpsLogger;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Topic SSE 适配器；AI 准入、路由和终态收尾统一由 Runtime 负责。 */
@Service
@ConditionalOnBean(AgentRuntime.class)
public class AiExplanationService {

  private final ExplainTopicUseCase explainTopicUseCase;
  private final LlmStreamSseMapper sseMapper;
  private final AiActorResolver actorResolver;
  private final SseOpsRecorder sseOpsRecorder;
  private final LearningOpsRecorder learningOpsRecorder;
  private final StructuredOpsLogger opsLogger;

  public AiExplanationService(
      ExplainTopicUseCase explainTopicUseCase,
      LlmStreamSseMapper sseMapper,
      AiActorResolver actorResolver
  ) {
    this(
        explainTopicUseCase,
        sseMapper,
        actorResolver,
        NoopOpsRecorders.sse(),
        NoopOpsRecorders.learning(),
        new StructuredOpsLogger());
  }

  @Autowired
  public AiExplanationService(
      ExplainTopicUseCase explainTopicUseCase,
      LlmStreamSseMapper sseMapper,
      AiActorResolver actorResolver,
      ObjectProvider<SseOpsRecorder> sseOpsRecorder,
      ObjectProvider<LearningOpsRecorder> learningOpsRecorder
  ) {
    this(
        explainTopicUseCase,
        sseMapper,
        actorResolver,
        sseOpsRecorder.getIfAvailable(NoopOpsRecorders::sse),
        learningOpsRecorder.getIfAvailable(NoopOpsRecorders::learning),
        new StructuredOpsLogger());
  }

  private AiExplanationService(
      ExplainTopicUseCase explainTopicUseCase,
      LlmStreamSseMapper sseMapper,
      AiActorResolver actorResolver,
      SseOpsRecorder sseOpsRecorder,
      LearningOpsRecorder learningOpsRecorder,
      StructuredOpsLogger opsLogger
  ) {
    this.explainTopicUseCase = explainTopicUseCase;
    this.sseMapper = sseMapper;
    this.actorResolver = actorResolver;
    this.sseOpsRecorder = sseOpsRecorder;
    this.learningOpsRecorder = learningOpsRecorder;
    this.opsLogger = opsLogger;
  }

  public SseEmitter streamExplanation(String topic) {
    long userId = currentUserId();
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
      explainTopicUseCase.stream(topic, userId).subscribe(subscriber);
    } catch (RuntimeException exception) {
      subscriber.onError(exception);
    }
    return emitter;
  }

  private long currentUserId() {
    AiActor actor = actorResolver.currentActor();
    if (actor != null && actor.authenticated() && actor.userId() != null && actor.userId() > 0) {
      return actor.userId();
    }
    throw new AiRunAdmissionException(
        AiGovernanceErrorCode.AI_UNAUTHENTICATED,
        AiRunStatus.REJECTED_UNAUTHENTICATED,
        "当前请求未登录或无法解析当前用户。",
        HttpStatus.UNAUTHORIZED,
        Map.of());
  }
}
