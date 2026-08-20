package org.congcong.algomentor.mentor.application.learningplan.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.agent.core.AgentErrorCode;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.api.AgentPreparedStream;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanCoveragePolicy;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanClarificationMessages;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftSource;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContextService;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationScenario;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyConstants;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyService;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanDraftCreationAdmission;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 首次 AI 草案的控制面协调服务。
 *
 * <p>订阅者在执行器同步受理后、数据库事务提交前保持零 demand；这样执行器拒绝会回滚草案和额度，
 * 而已受理的 Agent 仅在生成草案持久化后才开始消费事件。</p>
 */
public class LearningPlanDraftGenerationService {

  private static final Logger log = LoggerFactory.getLogger(LearningPlanDraftGenerationService.class);
  private final LearningPlanDraftRepository draftRepository;
  private final LearningPlanDraftValidator validator;
  private final AgentRuntime agentRuntime;
  private final LearningPlanDraftStructuredOutputMapper outputMapper;
  private final LearningPlanLoadService loadService;
  private final ObjectMapper objectMapper;
  private final Clock clock;
  private final LearningPlanPersonalizationContextService personalizationContextService;
  private final LearningPlanCreationPolicyService creationPolicyService;
  private final LearningPlanDraftGenerationEventPublisher eventPublisher;

  public LearningPlanDraftGenerationService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanDraftValidator validator,
      AgentRuntime agentRuntime,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      LearningPlanLoadService loadService,
      Clock clock,
      LearningPlanPersonalizationContextService personalizationContextService,
      LearningPlanCreationPolicyService creationPolicyService,
      LearningPlanDraftGenerationEventPublisher eventPublisher
  ) {
    this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository");
    this.validator = Objects.requireNonNull(validator, "validator");
    this.agentRuntime = Objects.requireNonNull(agentRuntime, "agentRuntime");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.outputMapper = new LearningPlanDraftStructuredOutputMapper(objectMapper,
        Objects.requireNonNull(problemCatalog, "problemCatalog"));
    this.loadService = Objects.requireNonNull(loadService, "loadService");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.personalizationContextService = Objects.requireNonNull(
        personalizationContextService, "personalizationContextService");
    this.creationPolicyService = Objects.requireNonNull(creationPolicyService, "creationPolicyService");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
  }

  @Transactional
  public LearningPlanDraftGenerationStart start(
      long userId,
      LearningPlanBrief brief,
      String requestKey
  ) {
    LearningPlanBrief candidate = Objects.requireNonNull(brief, "brief must not be null");
    String normalizedRequestKey = normalizeRequestKey(requestKey);
    String fingerprint = fingerprint(candidate);
    draftRepository.lockGenerationRequest(userId, normalizedRequestKey);

    LearningPlanDraft existing = draftRepository.findDraftByGenerationRequestKey(userId, normalizedRequestKey)
        .orElse(null);
    if (existing != null) {
      if (!fingerprint.equals(existing.generationRequestFingerprint())) {
        throw new LearningPlanException(
            LearningPlanDraftGenerationConstants.IDEMPOTENCY_CONFLICT_CODE,
            "同一幂等键不能用于不同的学习计划创建请求。");
      }
      return new LearningPlanDraftGenerationStart(existing, false);
    }

    List<String> missingFields = validator.missingRequiredFields(candidate);
    if (!missingFields.isEmpty()) {
      LearningPlanDraft collecting = createInitialDraft(userId, candidate).withState(
              LearningPlanDraftStatus.COLLECTING,
              missingFields,
              LearningPlanClarificationMessages.forField(missingFields.get(0), candidate.contentLocale()),
              null,
              clock.instant())
          .withGenerationRequest(normalizedRequestKey, fingerprint);
      return new LearningPlanDraftGenerationStart(draftRepository.save(collecting), true);
    }

    String generationRunId = UUID.randomUUID().toString();
    LearningPlanDraft generating = createInitialDraft(userId, candidate)
        .withGenerationStarted(normalizedRequestKey, fingerprint, generationRunId, clock.instant());
    LearningPlanDraft saved = saveWithinDailyLimit(generating);
    LearningPlanPersonalizationSnapshot snapshot = personalizationContextService.snapshot(
        userId, candidate.personalizationEnabled(), LearningPlanPersonalizationScenario.DRAFT);
    AgentPreparedStream prepared = agentRuntime.prepareStream(invocation(userId, candidate, generationRunId, snapshot));
    if (prepared.idempotentReplay()) {
      throw new LearningPlanException(
          LearningPlanDraftGenerationConstants.GENERATION_FAILED_CODE,
          LearningPlanDraftGenerationConstants.GENERATION_FAILED_MESSAGE);
    }

    GenerationSubscriber subscriber = new GenerationSubscriber(saved);
    // 执行器拒绝会在这里同步抛出；当前事务回滚后不会留下草案、额度或成功幂等资源。
    prepared.subscribe(subscriber);
    activateAfterCommit(subscriber);
    return new LearningPlanDraftGenerationStart(saved, true);
  }

  private LearningPlanDraft createInitialDraft(long userId, LearningPlanBrief brief) {
    Instant now = clock.instant();
    LearningPlanDraftCreationAdmission admission = creationPolicyService.draftAdmission(userId, now);
    return new LearningPlanDraft(
        null,
        userId,
        LearningPlanDraftSource.AI_PERSONALIZED,
        LearningPlanDraftStatus.COLLECTING,
        brief,
        List.of(),
        List.of(),
        null,
        null,
        null,
        admission.expiresAt(),
        now,
        now);
  }

  private LearningPlanDraft saveWithinDailyLimit(LearningPlanDraft draft) {
    LearningPlanDraftCreationAdmission admission = creationPolicyService.draftAdmission(draft.userId(), clock.instant());
    return draftRepository.createWithinDailyLimit(
            draft,
            admission.quotaDate(),
            admission.dailyLimit(),
            clock.instant())
        .orElseThrow(() -> new LearningPlanException(
            LearningPlanCreationPolicyConstants.DRAFT_DAILY_LIMIT_EXCEEDED_CODE,
            "api.error." + LearningPlanCreationPolicyConstants.DRAFT_DAILY_LIMIT_EXCEEDED_CODE,
            "今天创建的学习计划草案已达到上限，请明天再试。"));
  }

  private AgentInvocation<LearningPlanDraftAgentInput> invocation(
      long userId,
      LearningPlanBrief brief,
      String generationRunId,
      LearningPlanPersonalizationSnapshot snapshot
  ) {
    return new AgentInvocation<>(
        LearningPlanDraftAgentDefinition.KEY,
        new LearningPlanDraftAgentInput(userId, brief, generationRunId, snapshot),
        new AgentInvocationContext(
            userId,
            AgentInvocationMode.USER_ENTRY,
            generationRunId,
            null,
            null,
            requestSize(brief),
            true));
  }

  private void activateAfterCommit(GenerationSubscriber subscriber) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      subscriber.activate();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        subscriber.activate();
      }

      @Override
      public void afterCompletion(int status) {
        if (status != TransactionSynchronization.STATUS_COMMITTED) {
          subscriber.abortBeforeActivation();
        }
      }
    });
  }

  private String normalizeRequestKey(String requestKey) {
    if (requestKey == null || requestKey.isBlank() || requestKey.trim().length() > 128) {
      throw new LearningPlanException(
          LearningPlanDraftGenerationConstants.IDEMPOTENCY_KEY_INVALID_CODE,
          "Idempotency-Key 必须为不超过 128 个字符的非空值。");
    }
    return requestKey.trim();
  }

  private String fingerprint(LearningPlanBrief brief) {
    try {
      byte[] bytes = objectMapper.writer()
          .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsBytes(brief);
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
      throw new LearningPlanException(
          LearningPlanDraftGenerationConstants.GENERATION_FAILED_CODE,
          "学习计划创建请求无法处理。");
    }
  }

  private int requestSize(LearningPlanBrief brief) {
    try {
      return objectMapper.writeValueAsBytes(brief).length;
    } catch (JsonProcessingException exception) {
      throw new LearningPlanException("LEARNING_PLAN_BRIEF_INVALID", "学习计划输入无法序列化。");
    }
  }

  private final class GenerationSubscriber implements Flow.Subscriber<AgentStreamEvent> {

    private final LearningPlanDraft draft;
    private final StringBuilder stepContent = new StringBuilder();
    private final AtomicBoolean terminal = new AtomicBoolean();
    private volatile Flow.Subscription subscription;
    private volatile boolean active;
    private volatile boolean abandoned;
    private String finalContent;

    private GenerationSubscriber(LearningPlanDraft draft) {
      this.draft = draft;
    }

    @Override
    public void onSubscribe(Flow.Subscription nextSubscription) {
      subscription = Objects.requireNonNull(nextSubscription, "subscription");
      // 在事务提交前不申请事件，确保浏览器和 Redis 都不会看到未提交的草案。
      if (abandoned) {
        subscription.cancel();
      }
    }

    private void activate() {
      if (abandoned) {
        return;
      }
      Flow.Subscription current = subscription;
      if (current == null) {
        log.warn("Learning plan draft generation subscription was not established. draftId={}", draft.id());
        return;
      }
      active = true;
      current.request(1);
    }

    private void abortBeforeActivation() {
      abandoned = true;
      Flow.Subscription current = subscription;
      if (current != null) {
        current.cancel();
      }
    }

    @Override
    public void onNext(AgentStreamEvent event) {
      if (!active || terminal.get()) {
        return;
      }
      try {
        captureContent(event);
        publishProgress(event);
        if (event instanceof AgentStreamEvent.AgentRunEnd) {
          complete();
          return;
        }
        if (event instanceof AgentStreamEvent.AgentError error) {
          fail(publicErrorCode(error.error().code()));
          return;
        }
        Flow.Subscription current = subscription;
        if (current != null) {
          current.request(1);
        }
      } catch (RuntimeException exception) {
        log.warn("Learning plan draft generation event handling failed. draftId={} exceptionType={}",
            draft.id(), exception.getClass().getSimpleName());
        fail(LearningPlanDraftGenerationConstants.GENERATION_FAILED_CODE);
      }
    }

    @Override
    public void onError(Throwable throwable) {
      if (active) {
        fail(LearningPlanDraftGenerationConstants.GENERATION_FAILED_CODE);
      }
    }

    @Override
    public void onComplete() {
      if (active && !terminal.get()) {
        fail(LearningPlanDraftGenerationConstants.GENERATION_FAILED_CODE);
      }
    }

    private void complete() {
      if (!terminal.compareAndSet(false, true)) {
        return;
      }
      try {
        if (finalContent == null || finalContent.isBlank()) {
          throw new LearningPlanException("LEARNING_PLAN_FINAL_OUTPUT_MISSING", "模型未返回学习计划结果。");
        }
        var plan = loadService.withLoadMetadata(
            outputMapper.map(objectMapper.readTree(finalContent), draft.brief()),
            LearningPlanCoveragePolicy.FIT_USER_BUDGET);
        validator.validateGeneratedPlan(plan);
        if (draftRepository.completeGeneration(draft, plan, clock.instant()).isPresent()) {
          append(new LearningPlanDraftGenerationEvent.Completed());
        }
      } catch (JsonProcessingException | LearningPlanException exception) {
        terminal.set(false);
        fail(LearningPlanDraftGenerationConstants.GENERATION_FAILED_CODE);
      } finally {
        cancel();
      }
    }

    private void fail(String code) {
      if (!terminal.compareAndSet(false, true)) {
        return;
      }
      try {
        if (draftRepository.failGeneration(
            draft,
            code,
            LearningPlanDraftGenerationConstants.GENERATION_FAILED_MESSAGE,
            clock.instant()).isPresent()) {
          append(new LearningPlanDraftGenerationEvent.Failed(code));
        }
      } finally {
        cancel();
      }
    }

    private void captureContent(AgentStreamEvent event) {
      if (event instanceof AgentStreamEvent.AgentStepStart) {
        stepContent.setLength(0);
      } else if (event instanceof AgentStreamEvent.Llm llm
          && llm.event() instanceof LlmStreamEvent.ContentDelta delta) {
        stepContent.append(delta.content());
      } else if (event instanceof AgentStreamEvent.AgentStepEnd end && end.toolCallCount() == 0) {
        finalContent = stepContent.toString();
      }
    }

    private void publishProgress(AgentStreamEvent event) {
      if (event instanceof AgentStreamEvent.AgentRunStart) {
        append(new LearningPlanDraftGenerationEvent.WorkStarted(
            LearningPlanDraftGenerationConstants.WORK_STARTED_MESSAGE));
      } else if (event instanceof AgentStreamEvent.AgentStepStart) {
        append(new LearningPlanDraftGenerationEvent.WorkProgress(
            LearningPlanDraftGenerationConstants.WORK_PROGRESS_MESSAGE));
      } else if (event instanceof AgentStreamEvent.AgentToolStart start
          && LearningPlanDraftGenerationConstants.PUBLIC_TOOL_NAMES.contains(start.toolName())) {
        append(new LearningPlanDraftGenerationEvent.WorkToolStarted(start.toolName()));
      } else if (event instanceof AgentStreamEvent.AgentToolEnd end
          && LearningPlanDraftGenerationConstants.PUBLIC_TOOL_NAMES.contains(end.toolName())) {
        append(new LearningPlanDraftGenerationEvent.WorkToolEnded(end.toolName()));
      }
    }

    private void append(LearningPlanDraftGenerationEvent event) {
      try {
        eventPublisher.append(draft.id(), event);
      } catch (RuntimeException exception) {
        log.warn("Learning plan generation realtime append failed. draftId={} exceptionType={}",
            draft.id(), exception.getClass().getSimpleName());
      }
    }

    private String publicErrorCode(AgentErrorCode code) {
      return code == AgentErrorCode.AGENT_EXECUTOR_OVERLOADED
          ? AgentErrorCode.AGENT_EXECUTOR_OVERLOADED.name()
          : LearningPlanDraftGenerationConstants.GENERATION_FAILED_CODE;
    }

    private void cancel() {
      Flow.Subscription current = subscription;
      if (current != null) {
        current.cancel();
      }
    }
  }
}
