package org.congcong.algomentor.mentor.application.practice;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationCommand;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunCoordinator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 训练聊天单轮编排器：先流式完成聊天 run，再在 run 结束事件上追加练习能力结果 metadata。
 */
public class PracticeTurnOrchestrator {

  static final String FAILURE_CODE_MESSAGES_MISSING = "PRACTICE_TURN_MESSAGES_MISSING";
  static final String FAILURE_CODE_RUN_ID_MISSING = "PRACTICE_TURN_RUN_ID_MISSING";
  static final String FAILURE_CODE_CAPABILITY_FAILED = "PRACTICE_TURN_CAPABILITY_FAILED";

  private static final Logger log = LoggerFactory.getLogger(PracticeTurnOrchestrator.class);
  private static final String DEFAULT_LOCALE = "zh-CN";

  private final PracticeSessionRepository sessionRepository;
  private final AgentConversationRunCoordinator coordinator;
  private final AgentTurnMessageLookupRepository turnMessageLookupRepository;
  private final PracticeTurnClassifier classifier;
  private final PracticeTurnCapabilityRegistry capabilityRegistry;
  private final PracticeChatProblemCatalog problemCatalog;

  public PracticeTurnOrchestrator(
      PracticeSessionRepository sessionRepository,
      AgentConversationRunCoordinator coordinator,
      AgentTurnMessageLookupRepository turnMessageLookupRepository,
      PracticeTurnClassifier classifier,
      PracticeTurnCapabilityRegistry capabilityRegistry,
      PracticeChatProblemCatalog problemCatalog
  ) {
    this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
    this.turnMessageLookupRepository = Objects.requireNonNull(
        turnMessageLookupRepository,
        "turnMessageLookupRepository must not be null");
    this.classifier = Objects.requireNonNull(classifier, "classifier must not be null");
    this.capabilityRegistry = Objects.requireNonNull(capabilityRegistry, "capabilityRegistry must not be null");
    this.problemCatalog = Objects.requireNonNull(problemCatalog, "problemCatalog must not be null");
  }

  public Flow.Publisher<AgentStreamEvent> stream(
      long userId,
      long sessionId,
      String message,
      String idempotencyKey,
      String locale,
      Map<String, Object> governanceMetadata
  ) {
    PracticeSession session = sessionRepository.findSessionForUser(sessionId, userId)
        .orElseThrow(() -> new LearningPlanException("PRACTICE_SESSION_NOT_FOUND", "题目训练会话不存在。"));
    if (session.status() != PracticeSessionStatus.ACTIVE) {
      throw new LearningPlanException("PRACTICE_SESSION_ARCHIVED", "题目训练会话已归档。");
    }
    if (session.agentTaskId() == null) {
      throw new LearningPlanException("PRACTICE_SESSION_AGENT_TASK_MISSING", "题目训练会话缺少运行任务。");
    }

    String effectiveLocale = effectiveLocale(session.locale(), locale);
    PracticeTurnClassification classification = classifier.classify(message, session.problemSlug(), session.problemSlug());
    Flow.Publisher<AgentStreamEvent> delegate = coordinator.stream(command(
        session,
        userId,
        message,
        idempotencyKey,
        effectiveLocale,
        governanceMetadata));
    return new MergingPublisher(this, delegate, session, userId, effectiveLocale, classification);
  }

  private AgentConversationCommand command(
      PracticeSession session,
      long userId,
      String message,
      String idempotencyKey,
      String effectiveLocale,
      Map<String, Object> governanceMetadata
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>(governanceMetadata == null ? Map.of() : governanceMetadata);
    metadata.put(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
    metadata.put(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, session.id());
    metadata.put(PracticeChatPromptConstants.METADATA_PLAN_ID, session.planId());
    metadata.put(PracticeChatPromptConstants.METADATA_PHASE_INDEX, session.phaseIndex());
    metadata.put(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, session.problemSlug());
    metadata.put(PracticeChatPromptConstants.METADATA_LOCALE, effectiveLocale);
    metadata.put(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, PracticeChatPromptConstants.MESSAGE_TYPE_CHAT);
    return new AgentConversationCommand(
        session.agentTaskId(),
        userId,
        message,
        idempotencyKey,
        Map.copyOf(metadata),
        new PracticeChatReference(session.planId(), session.phaseIndex(), session.problemSlug(), effectiveLocale));
  }

  private String effectiveLocale(String sessionLocale, String requestedLocale) {
    if (sessionLocale != null && !sessionLocale.isBlank()) {
      return sessionLocale.trim();
    }
    if (requestedLocale != null && !requestedLocale.isBlank()) {
      return requestedLocale.trim();
    }
    return DEFAULT_LOCALE;
  }

  private AgentStreamEvent.AgentRunEnd mergeRunEnd(
      AgentStreamEvent.AgentRunEnd runEnd,
      PracticeSession session,
      long userId,
      String locale,
      PracticeTurnClassification classification
  ) {
    Map<String, Object> metadata = new LinkedHashMap<>(runEnd.metadata());
    Map<String, Object> capabilityMetadata = capabilityMetadata(runEnd, session, userId, locale, classification);
    if (!capabilityMetadata.isEmpty()) {
      metadata.put(
          PracticeCodeReviewConstants.METADATA_PRACTICE_CAPABILITIES,
          mergePracticeCapabilities(runEnd.metadata(), capabilityMetadata));
    }
    return new AgentStreamEvent.AgentRunEnd(runEnd.runId(), runEnd.steps(), runEnd.finishReason(), metadata);
  }

  private Map<String, Object> capabilityMetadata(
      AgentStreamEvent.AgentRunEnd runEnd,
      PracticeSession session,
      long userId,
      String locale,
      PracticeTurnClassification classification
  ) {
    List<PracticeTurnCapability> capabilities = capabilityRegistry.capabilities();
    if (capabilities.isEmpty()) {
      return Map.of();
    }
    Optional<Long> runDbId = longMetadata(runEnd.metadata(), AgentRuntimeMetadataKeys.RUN_DB_ID);
    if (runDbId.isEmpty()) {
      return failedCapabilities(capabilities, FAILURE_CODE_RUN_ID_MISSING);
    }
    Optional<AgentTurnMessages> messages = turnMessageLookupRepository.findByRunId(runDbId.get());
    if (messages.isEmpty()) {
      return failedCapabilities(capabilities, FAILURE_CODE_MESSAGES_MISSING);
    }
    PracticeTurnClassification effectiveClassification = isIdempotentReplay(runEnd.metadata())
        ? classification.asIdempotentReplay()
        : classification;
    PracticeTurnContext context = context(session, userId, locale, runDbId.get(), messages.get(), effectiveClassification);
    Map<String, Object> values = new LinkedHashMap<>();
    for (PracticeTurnCapability capability : capabilities) {
      PracticeTurnCapabilityResult result;
      try {
        result = capability.afterTurn(context, effectiveClassification);
      } catch (RuntimeException exception) {
        log.warn(
            "Practice turn capability failed. sessionId={} runDbId={} capability={} exceptionType={}",
            session.id(),
            runDbId.get(),
            capability.capabilityName(),
            exception.getClass().getSimpleName());
        result = new PracticeTurnCapabilityResult(
            capability.capabilityName(),
            PracticeReviewStatus.FAILED,
            Map.of(
                "failureCode", FAILURE_CODE_CAPABILITY_FAILED,
                "exceptionType", exception.getClass().getSimpleName()));
      }
      values.put(result.capabilityName(), resultMetadata(result));
    }
    return Map.copyOf(values);
  }

  private PracticeTurnContext context(
      PracticeSession session,
      long userId,
      String locale,
      long runDbId,
      AgentTurnMessages messages,
      PracticeTurnClassification classification
  ) {
    Long assistantMessageId = messages.assistantMessage().map(message -> message.id()).orElse(null);
    Optional<PracticeChatProblemDetail> problem = problemCatalog.findProblemBySlug(session.problemSlug(), locale);
    return new PracticeTurnContext(
        userId,
        session.planId(),
        session.phaseIndex(),
        session.problemSlug(),
        session.id(),
        messages.userMessage().id(),
        assistantMessageId,
        runDbId,
        problem.map(this::problemFacts).orElse("slug=%s".formatted(session.problemSlug())),
        learningPlanFacts(session),
        classification.extractedCode(),
        classification.originalMessage(),
        "",
        locale);
  }

  private String problemFacts(PracticeChatProblemDetail problem) {
    return """
        slug=%s
        frontendId=%s
        title=%s
        difficulty=%s
        tags=%s
        statement=%s
        leetcodeUrl=%s
        """.formatted(
        problem.slug(),
        problem.frontendId() == null ? "" : problem.frontendId(),
        blankToEmpty(problem.title()),
        blankToEmpty(problem.difficulty()),
        String.join(", ", problem.tags()),
        blankToEmpty(problem.contentMarkdown()),
        blankToEmpty(problem.leetcodeUrl())).trim();
  }

  private String learningPlanFacts(PracticeSession session) {
    return "planId=%d phaseIndex=%d problemSlug=%s".formatted(
        session.planId(),
        session.phaseIndex(),
        session.problemSlug());
  }

  private String blankToEmpty(String value) {
    return value == null ? "" : value;
  }

  private Map<String, Object> mergePracticeCapabilities(
      Map<String, Object> originalMetadata,
      Map<String, Object> capabilityMetadata
  ) {
    Map<String, Object> merged = new LinkedHashMap<>();
    Object existing = originalMetadata.get(PracticeCodeReviewConstants.METADATA_PRACTICE_CAPABILITIES);
    if (existing instanceof Map<?, ?> existingMap) {
      existingMap.forEach((key, value) -> merged.put(String.valueOf(key), value));
    }
    merged.putAll(capabilityMetadata);
    return Map.copyOf(merged);
  }

  private Map<String, Object> failedCapabilities(List<PracticeTurnCapability> capabilities, String failureCode) {
    Map<String, Object> values = new LinkedHashMap<>();
    for (PracticeTurnCapability capability : capabilities) {
      values.put(capability.capabilityName(), Map.of(
          "status", PracticeReviewStatus.FAILED.name(),
          "failureCode", failureCode));
    }
    return Map.copyOf(values);
  }

  private Map<String, Object> resultMetadata(PracticeTurnCapabilityResult result) {
    Map<String, Object> values = new LinkedHashMap<>(result.metadata());
    values.put("status", result.status().name());
    return Map.copyOf(values);
  }

  private Optional<Long> longMetadata(Map<String, Object> metadata, String key) {
    Object value = metadata.get(key);
    if (value instanceof Number number) {
      return Optional.of(number.longValue()).filter(candidate -> candidate > 0);
    }
    if (value instanceof String text && !text.isBlank()) {
      try {
        long parsed = Long.parseLong(text.trim());
        return parsed > 0 ? Optional.of(parsed) : Optional.empty();
      } catch (NumberFormatException exception) {
        return Optional.empty();
      }
    }
    return Optional.empty();
  }

  private boolean isIdempotentReplay(Map<String, Object> metadata) {
    Object value = metadata.get(AgentRuntimeMetadataKeys.IDEMPOTENT_REPLAY);
    return Boolean.TRUE.equals(value) || (value instanceof String text && Boolean.parseBoolean(text));
  }

  private record MergingPublisher(
      PracticeTurnOrchestrator orchestrator,
      Flow.Publisher<AgentStreamEvent> delegate,
      PracticeSession session,
      long userId,
      String locale,
      PracticeTurnClassification classification
  ) implements Flow.Publisher<AgentStreamEvent> {

    @Override
    public void subscribe(Flow.Subscriber<? super AgentStreamEvent> subscriber) {
      delegate.subscribe(new Flow.Subscriber<>() {
        @Override
        public void onSubscribe(Flow.Subscription subscription) {
          subscriber.onSubscribe(subscription);
        }

        @Override
        public void onNext(AgentStreamEvent item) {
          if (item instanceof AgentStreamEvent.AgentRunEnd runEnd) {
            subscriber.onNext(orchestrator.mergeRunEnd(runEnd, session, userId, locale, classification));
            return;
          }
          subscriber.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
          subscriber.onError(throwable);
        }

        @Override
        public void onComplete() {
          subscriber.onComplete();
        }
      });
    }
  }
}
