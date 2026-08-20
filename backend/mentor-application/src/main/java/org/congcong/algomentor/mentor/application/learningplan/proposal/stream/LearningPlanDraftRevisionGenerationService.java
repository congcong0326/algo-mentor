package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContextService;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationScenario;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionAccessService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroup;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalTargetType;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalType;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionToolContracts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 草案修订的控制面协调器。
 *
 * <p>创建 revision、Agent 同步受理和订阅注册均发生在调用事务中；Subscriber 仅在提交后申请
 * demand，因此 HTTP SSE 连接的生命周期不会影响后台修订。</p>
 */
public class LearningPlanDraftRevisionGenerationService {

  private static final Logger log = LoggerFactory.getLogger(LearningPlanDraftRevisionGenerationService.class);

  private final LearningPlanDraftRepository draftRepository;
  private final LearningPlanProposalRepository proposalRepository;
  private final LearningPlanProposalGroupService groupService;
  private final AgentRuntime agentRuntime;
  private final ObjectMapper objectMapper;
  private final TransactionOperations transactions;
  private final Clock clock;
  private final LearningPlanPersonalizationContextService personalizationContextService;
  private final LearningPlanAiRevisionAccessService accessService;
  private final LearningPlanDraftRevisionGenerationEventPublisher eventPublisher;
  private final LearningPlanDraftRevisionGenerationMetrics metrics;
  private final LearningPlanDraftRevisionStructuredOutputMapper outputMapper =
      new LearningPlanDraftRevisionStructuredOutputMapper();

  public LearningPlanDraftRevisionGenerationService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanProposalRepository proposalRepository,
      LearningPlanProposalGroupService groupService,
      AgentRuntime agentRuntime,
      ObjectMapper objectMapper,
      TransactionOperations transactions,
      Clock clock,
      LearningPlanPersonalizationContextService personalizationContextService,
      LearningPlanAiRevisionAccessService accessService,
      LearningPlanDraftRevisionGenerationEventPublisher eventPublisher
  ) {
    this(
        draftRepository,
        proposalRepository,
        groupService,
        agentRuntime,
        objectMapper,
        transactions,
        clock,
        personalizationContextService,
        accessService,
        eventPublisher,
        LearningPlanDraftRevisionGenerationMetrics.NOOP);
  }

  public LearningPlanDraftRevisionGenerationService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanProposalRepository proposalRepository,
      LearningPlanProposalGroupService groupService,
      AgentRuntime agentRuntime,
      ObjectMapper objectMapper,
      TransactionOperations transactions,
      Clock clock,
      LearningPlanPersonalizationContextService personalizationContextService,
      LearningPlanAiRevisionAccessService accessService,
      LearningPlanDraftRevisionGenerationEventPublisher eventPublisher,
      LearningPlanDraftRevisionGenerationMetrics metrics
  ) {
    this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository");
    this.proposalRepository = Objects.requireNonNull(proposalRepository, "proposalRepository");
    this.groupService = Objects.requireNonNull(groupService, "groupService");
    this.agentRuntime = Objects.requireNonNull(agentRuntime, "agentRuntime");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.transactions = Objects.requireNonNull(transactions, "transactions");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.personalizationContextService = Objects.requireNonNull(personalizationContextService, "personalizationContextService");
    this.accessService = Objects.requireNonNull(accessService, "accessService");
    this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
    this.metrics = Objects.requireNonNull(metrics, "metrics");
  }

  @Transactional
  public LearningPlanDraftRevisionGenerationStart start(
      long userId, long draftId, String instruction, String requestKey) {
    String normalizedInstruction = requireInstruction(instruction);
    String normalizedRequestKey = normalizeRequestKey(requestKey);
    String fingerprint = fingerprint(normalizedInstruction);
    proposalRepository.lockDraftRevisionGenerationRequest(userId, draftId, normalizedRequestKey);

    LearningPlanDraftRevision existing = proposalRepository
        .findDraftRevisionByGenerationRequestKey(userId, draftId, normalizedRequestKey)
        .orElse(null);
    if (existing != null) {
      if (!fingerprint.equals(existing.generationRequestFingerprint())) {
        throw new LearningPlanException(
            LearningPlanDraftRevisionGenerationConstants.IDEMPOTENCY_CONFLICT_CODE,
            "同一幂等键不能用于不同的学习计划修订请求。");
      }
      metrics.recordIdempotencyReused();
      return new LearningPlanDraftRevisionGenerationStart(existing, false);
    }

    LearningPlanDraft draft = draftRepository.findDraftByIdForUserForUpdate(draftId, userId)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_DRAFT_NOT_FOUND", "学习计划草案不存在。"));
    accessService.requireDraftRevision(userId, draft.source());
    validateDraft(draft);
    LearningPlanProposalGroup group = latestActiveGroup(userId, draftId)
        .orElseGet(() -> groupService.createGroup(
            userId, LearningPlanProposalType.DRAFT_REVISION, LearningPlanProposalTargetType.DRAFT, draftId,
            normalizedInstruction));
    Instant startedAt = clock.instant();
    String runId = UUID.randomUUID().toString();
    LearningPlanDraftRevision saved = proposalRepository.saveDraftRevision(new LearningPlanDraftRevision(
        null, group.id(), draftId, userId, proposalRepository.nextRevisionNo(group.id()),
        LearningPlanProposalRevisionStatus.GENERATING, normalizedInstruction, draft.brief(), draft.draftPlan(),
        null, null, null, null, startedAt, startedAt)
        .withGenerationStarted(normalizedRequestKey, fingerprint, runId, startedAt));
    LearningPlanPersonalizationSnapshot snapshot = personalizationContextService.snapshot(
        userId, draft.brief().personalizationEnabled(), LearningPlanPersonalizationScenario.REVISION);
    AgentPreparedStream prepared = agentRuntime.prepareStream(invocation(draft, saved, runId, snapshot));
    if (prepared.idempotentReplay()) {
      throw new LearningPlanException(
          LearningPlanDraftRevisionGenerationConstants.GENERATION_FAILED_CODE,
          LearningPlanDraftRevisionGenerationConstants.GENERATION_FAILED_MESSAGE);
    }
    GenerationSubscriber subscriber = new GenerationSubscriber(draft, saved);
    prepared.subscribe(subscriber);
    activateAfterCommit(subscriber);
    return new LearningPlanDraftRevisionGenerationStart(saved, true);
  }

  private Optional<LearningPlanProposalGroup> latestActiveGroup(long userId, long draftId) {
    return proposalRepository.findLatestActiveGroup(
        userId, LearningPlanProposalType.DRAFT_REVISION, LearningPlanProposalTargetType.DRAFT, draftId);
  }

  private void validateDraft(LearningPlanDraft draft) {
    if (draft.status() == LearningPlanDraftStatus.CONFIRMED) {
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_REVISION_NOT_ALLOWED", "已确认的学习计划草案不能继续修订。");
    }
    if (draft.draftPlan() == null) {
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_PLAN_MISSING", "学习计划草案缺少可修订的计划内容。");
    }
  }

  private AgentInvocation<LearningPlanDraftRevisionAgentInput> invocation(
      LearningPlanDraft draft,
      LearningPlanDraftRevision revision,
      String runId,
      LearningPlanPersonalizationSnapshot snapshot
  ) {
    return new AgentInvocation<>(
        LearningPlanDraftRevisionAgentDefinition.KEY,
        new LearningPlanDraftRevisionAgentInput(draft.userId(), draft.id(), revision.id(), revision.instruction(),
            revision.baseBrief(), revision.basePlan(), runId, snapshot),
        new AgentInvocationContext(draft.userId(), AgentInvocationMode.USER_ENTRY, runId, null, null,
            revision.instruction().getBytes(StandardCharsets.UTF_8).length, true));
  }

  private void activateAfterCommit(GenerationSubscriber subscriber) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      metrics.recordStarted();
      subscriber.activate();
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        metrics.recordStarted();
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

  private String requireInstruction(String instruction) {
    if (instruction == null || instruction.isBlank()) {
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_REVISION_INSTRUCTION_REQUIRED", "修订要求不能为空。");
    }
    return instruction.trim();
  }

  private String normalizeRequestKey(String requestKey) {
    if (requestKey == null || requestKey.isBlank() || requestKey.trim().length() > 128) {
      throw new LearningPlanException(
          LearningPlanDraftRevisionGenerationConstants.IDEMPOTENCY_KEY_INVALID_CODE,
          "Idempotency-Key 必须为不超过 128 个字符的非空值。");
    }
    return requestKey.trim();
  }

  private String fingerprint(String instruction) {
    try {
      byte[] body = objectMapper.writer().with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsBytes(Map.of("instruction", instruction));
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));
    } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
      throw new LearningPlanException(
          LearningPlanDraftRevisionGenerationConstants.GENERATION_FAILED_CODE,
          LearningPlanDraftRevisionGenerationConstants.GENERATION_FAILED_MESSAGE);
    }
  }

  private final class GenerationSubscriber implements Flow.Subscriber<AgentStreamEvent> {

    private final LearningPlanDraft draft;
    private final LearningPlanDraftRevision revision;
    private final AtomicBoolean terminal = new AtomicBoolean();
    private final StringBuilder stepContent = new StringBuilder();
    private volatile Flow.Subscription subscription;
    private volatile boolean active;
    private volatile boolean abandoned;
    private String finalContent;

    private GenerationSubscriber(LearningPlanDraft draft, LearningPlanDraftRevision revision) {
      this.draft = draft;
      this.revision = revision;
    }

    @Override
    public void onSubscribe(Flow.Subscription nextSubscription) {
      subscription = Objects.requireNonNull(nextSubscription, "subscription");
      if (abandoned) {
        subscription.cancel();
      }
    }

    private void activate() {
      if (abandoned || subscription == null) {
        return;
      }
      active = true;
      subscription.request(1);
    }

    private void abortBeforeActivation() {
      abandoned = true;
      if (subscription != null) {
        subscription.cancel();
      }
    }

    @Override
    public void onNext(AgentStreamEvent event) {
      if (!active || terminal.get()) {
        return;
      }
      try {
        captureContent(event);
        projectProgress(event);
        if (event instanceof AgentStreamEvent.AgentRunEnd) {
          complete();
          return;
        }
        if (event instanceof AgentStreamEvent.AgentError error) {
          fail(publicErrorCode(error.error().code()));
          return;
        }
        if (subscription != null) {
          subscription.request(1);
        }
      } catch (RuntimeException exception) {
        log.warn("Learning plan draft revision generation event handling failed. revisionId={} exceptionType={}",
            revision.id(), exception.getClass().getSimpleName());
        fail(LearningPlanDraftRevisionGenerationConstants.GENERATION_FAILED_CODE);
      }
    }

    @Override
    public void onError(Throwable throwable) {
      if (active) {
        fail(LearningPlanDraftRevisionGenerationConstants.GENERATION_FAILED_CODE);
      }
    }

    @Override
    public void onComplete() {
      if (active && !terminal.get()) {
        fail(LearningPlanDraftRevisionGenerationConstants.GENERATION_FAILED_CODE);
      }
    }

    private void complete() {
      if (!terminal.compareAndSet(false, true)) {
        return;
      }
      try {
        if (finalContent == null || finalContent.isBlank()) {
          throw new LearningPlanException("LEARNING_PLAN_FINAL_OUTPUT_MISSING", "模型未返回学习计划修订结果。");
        }
        LearningPlanDraftRevisionOutput output = outputMapper.map(objectMapper.readTree(finalContent));
        if (!LearningPlanRevisionToolContracts.artifactRef(revision.id()).equals(output.artifactRef())) {
          throw new LearningPlanException("LEARNING_PLAN_REVISION_ARTIFACT_INVALID", "模型返回的修订 artifact 无效。");
        }
        Completion completion = transactions.execute(status -> completeInTransaction());
        if (completion != null) {
          recordCompletionMetrics(completion);
          append(completion.event());
        }
      } catch (JsonProcessingException | LearningPlanException exception) {
        terminal.set(false);
        fail(LearningPlanDraftRevisionGenerationConstants.GENERATION_FAILED_CODE);
      } catch (RuntimeException exception) {
        terminal.set(false);
        fail(LearningPlanDraftRevisionGenerationConstants.GENERATION_FAILED_CODE);
      } finally {
        cancel();
      }
    }

    private Completion completeInTransaction() {
      LearningPlanDraftRevision current = proposalRepository
          .findDraftRevisionForUserAndDraftForUpdate(revision.id(), revision.userId(), revision.draftId())
          .orElse(null);
      if (current == null || current.status() != LearningPlanProposalRevisionStatus.GENERATING
          || current.proposedBrief() == null || current.proposedPlan() == null) {
        return null;
      }
      LearningPlanDraft lockedDraft = draftRepository.findDraftByIdForUserForUpdate(draft.id(), draft.userId())
          .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_DRAFT_NOT_FOUND", "学习计划草案不存在。"));
      validateDraft(lockedDraft);
      LearningPlanProposalGroup group = proposalRepository.findGroupForUserForUpdate(current.proposalGroupId(), current.userId())
          .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_PROPOSAL_GROUP_NOT_FOUND", "学习计划提案分组不存在。"));
      int nextRevisionNo = proposalRepository.nextRevisionNo(group.id());
      Instant completedAt = clock.instant();
      if (nextRevisionNo > current.revisionNo() + 1
          || latestActiveGroup(current.userId(), current.draftId()).filter(active -> active.id().equals(group.id())).isEmpty()) {
        Optional<LearningPlanDraftRevision> superseded = proposalRepository.supersedeDraftRevisionIfGenerating(
            current.id(), current.userId(), current.draftId(), LearningPlanDraftRevisionGenerationConstants.SUPERSEDED_CODE,
            LearningPlanDraftRevisionGenerationConstants.SUPERSEDED_MESSAGE, completedAt);
        return superseded.map(ignored -> new Completion(
            new LearningPlanDraftRevisionGenerationEvent.Superseded(
                LearningPlanDraftRevisionGenerationConstants.SUPERSEDED_CODE),
            current.generationStartedAt(), completedAt, 0)).orElse(null);
      }
      LearningPlanDraftRevision ready = proposalRepository.completeDraftRevisionIfGenerating(current.withReady(completedAt))
          .orElse(null);
      if (ready == null) {
        return null;
      }
      int supersededReadyCount = proposalRepository
          .markReadyDraftRevisionsSuperseded(ready.proposalGroupId(), ready.id()).size();
      proposalRepository.saveGroup(group.withLatestProposalId(ready.id(), completedAt));
      draftRepository.save(draftWithRevisionPlan(lockedDraft, ready.proposedBrief(), ready.proposedPlan()));
      return new Completion(new LearningPlanDraftRevisionGenerationEvent.Completed(),
          ready.generationStartedAt(), completedAt, supersededReadyCount);
    }

    private void fail(String code) {
      if (!terminal.compareAndSet(false, true)) {
        return;
      }
      try {
        Instant completedAt = clock.instant();
        Optional<LearningPlanDraftRevision> failed = transactions.execute(status -> proposalRepository
            .failDraftRevisionIfGenerating(revision.id(), revision.userId(), revision.draftId(), code,
                LearningPlanDraftRevisionGenerationConstants.GENERATION_FAILED_MESSAGE, completedAt));
        if (failed != null && failed.isPresent()) {
          metrics.recordFailed(failed.get().generationStartedAt(), completedAt);
          append(new LearningPlanDraftRevisionGenerationEvent.Failed(code));
        }
      } finally {
        cancel();
      }
    }

    private LearningPlanDraft draftWithRevisionPlan(
        LearningPlanDraft current, LearningPlanBrief brief, LearningPlanDraftPlan plan) {
      List<String> messages = new ArrayList<>(current.messages());
      messages.add(revision.instruction());
      return current.withGeneratedRevision(brief, messages, plan, clock.instant());
    }

    private void captureContent(AgentStreamEvent event) {
      if (event instanceof AgentStreamEvent.AgentStepStart) {
        stepContent.setLength(0);
      } else if (event instanceof AgentStreamEvent.Llm llm && llm.event() instanceof LlmStreamEvent.ContentDelta delta) {
        stepContent.append(delta.content());
      } else if (event instanceof AgentStreamEvent.AgentStepEnd end && end.toolCallCount() == 0) {
        finalContent = stepContent.toString();
      }
    }

    private void projectProgress(AgentStreamEvent event) {
      if (event instanceof AgentStreamEvent.AgentRunStart) {
        append(new LearningPlanDraftRevisionGenerationEvent.WorkStarted());
      } else if (event instanceof AgentStreamEvent.AgentStepStart) {
        append(new LearningPlanDraftRevisionGenerationEvent.WorkProgress());
      }
    }

    private String publicErrorCode(AgentErrorCode code) {
      return code == AgentErrorCode.AGENT_EXECUTOR_OVERLOADED
          ? code.name()
          : LearningPlanDraftRevisionGenerationConstants.GENERATION_FAILED_CODE;
    }

    private void append(LearningPlanDraftRevisionGenerationEvent event) {
      try {
        eventPublisher.append(revision.draftId(), revision.id(), event);
      } catch (RuntimeException exception) {
        log.warn("Learning plan draft revision realtime append failed. revisionId={} exceptionType={}",
            revision.id(), exception.getClass().getSimpleName());
      }
    }

    private void cancel() {
      if (subscription != null) {
        subscription.cancel();
      }
    }

    private void recordCompletionMetrics(Completion completion) {
      if (completion.event() instanceof LearningPlanDraftRevisionGenerationEvent.Completed) {
        metrics.recordCompleted(completion.startedAt(), completion.completedAt());
      } else {
        metrics.recordSuperseded(completion.startedAt(), completion.completedAt());
      }
      for (int index = 0; index < completion.supersededReadyCount(); index++) {
        metrics.recordSuperseded(null, completion.completedAt());
      }
    }
  }

  private record Completion(
      LearningPlanDraftRevisionGenerationEvent event,
      Instant startedAt,
      Instant completedAt,
      int supersededReadyCount
  ) {
  }
}
