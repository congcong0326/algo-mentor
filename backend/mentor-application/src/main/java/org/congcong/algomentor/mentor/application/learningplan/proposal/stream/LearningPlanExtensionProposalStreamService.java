package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusProfile;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusProjector;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftMetadataKeys;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionDraft;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionResult;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanExtensionValidator;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroup;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalPromptBuilder;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalTargetType;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalType;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContextService;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationScenario;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanAgentToolNames;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanStreamConstants;
import org.congcong.algomentor.mentor.application.learningplan.stream.SingleSubscriberSynchronousPublisher;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 学习计划扩展提案流式生成编排服务。
 */
public class LearningPlanExtensionProposalStreamService {

  private static final Logger log = LoggerFactory.getLogger(LearningPlanExtensionProposalStreamService.class);
  private static final LearningPlanProposalStreamEvent.ProposalProfile PROFILE =
      LearningPlanProposalStreamEvent.ProposalProfile.PLAN_EXTENSION;

  private final LearningPlanRepository learningPlanRepository;
  private final LearningPlanProposalRepository proposalRepository;
  private final LearningPlanProposalGroupService groupService;
  private final PracticeSessionRepository practiceSessionRepository;
  private final LearningPlanExtensionValidator validator;
  private final AgentRuntime agentRuntime;
  private final ObjectMapper objectMapper;
  private final LearningPlanExtensionStructuredOutputMapper outputMapper;
  private final TransactionOperations transactionOperations;
  private final Clock clock;
  private final LearningPlanPersonalizationContextService personalizationContextService;

  public LearningPlanExtensionProposalStreamService(
      LearningPlanRepository learningPlanRepository,
      LearningPlanProposalRepository proposalRepository,
      LearningPlanProposalGroupService groupService,
      PracticeSessionRepository practiceSessionRepository,
      LearningPlanExtensionValidator validator,
      AgentRuntime agentRuntime,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      TransactionOperations transactionOperations,
      Clock clock
  ) {
    this(
        learningPlanRepository,
        proposalRepository,
        groupService,
        practiceSessionRepository,
        validator,
        agentRuntime,
        objectMapper,
        problemCatalog,
        transactionOperations,
        clock,
        new LearningPlanPersonalizationContextService(null));
  }

  public LearningPlanExtensionProposalStreamService(
      LearningPlanRepository learningPlanRepository,
      LearningPlanProposalRepository proposalRepository,
      LearningPlanProposalGroupService groupService,
      PracticeSessionRepository practiceSessionRepository,
      LearningPlanExtensionValidator validator,
      AgentRuntime agentRuntime,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      TransactionOperations transactionOperations,
      Clock clock,
      LearningPlanPersonalizationContextService personalizationContextService
  ) {
    this.learningPlanRepository = Objects.requireNonNull(learningPlanRepository, "learningPlanRepository");
    this.proposalRepository = Objects.requireNonNull(proposalRepository, "proposalRepository");
    this.groupService = Objects.requireNonNull(groupService, "groupService");
    this.practiceSessionRepository = Objects.requireNonNull(practiceSessionRepository, "practiceSessionRepository");
    this.validator = Objects.requireNonNull(validator, "validator");
    this.agentRuntime = Objects.requireNonNull(agentRuntime, "agentRuntime");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.outputMapper = new LearningPlanExtensionStructuredOutputMapper(objectMapper, problemCatalog);
    this.transactionOperations = Objects.requireNonNull(transactionOperations, "transactionOperations");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.personalizationContextService = Objects.requireNonNull(
        personalizationContextService, "personalizationContextService");
  }

  public Flow.Publisher<LearningPlanProposalStreamEvent> streamFirstRevision(
      long userId,
      long planId,
      String instruction,
      String runId,
      Map<String, Object> metadata
  ) {
    String normalizedInstruction = requireInstruction(instruction);
    return singleUsePublisher(() -> prepareFirstRevision(userId, planId, normalizedInstruction, runId, metadata));
  }

  public Flow.Publisher<LearningPlanProposalStreamEvent> streamNextRevision(
      long userId,
      long planId,
      long proposalGroupId,
      String instruction,
      String runId,
      Map<String, Object> metadata
  ) {
    String normalizedInstruction = requireInstruction(instruction);
    return singleUsePublisher(() -> prepareNextRevision(
        userId,
        planId,
        proposalGroupId,
        normalizedInstruction,
        runId,
        metadata));
  }

  private Flow.Publisher<LearningPlanProposalStreamEvent> singleUsePublisher(RevisionContextFactory factory) {
    AtomicBoolean subscribed = new AtomicBoolean(false);
    return subscriber -> {
      if (!subscribed.compareAndSet(false, true)) {
        subscriber.onSubscribe(new Flow.Subscription() {
          @Override
          public void request(long n) {
          }

          @Override
          public void cancel() {
          }
        });
        subscriber.onError(new IllegalStateException("Learning plan extension stream publisher is single-use"));
        return;
      }
      SingleSubscriberSynchronousPublisher<LearningPlanProposalStreamEvent> publisher =
          new SingleSubscriberSynchronousPublisher<>();
      publisher.subscribe(subscriber);
      SubscriptionRevisionContext context = null;
      try {
        context = factory.create();
        AgentWorkStatusProjector projector = new AgentWorkStatusProjector(learningPlanProfile(), clock);
        LearningPlanPersonalizationSnapshot personalizationSnapshot = personalizationContextService.snapshot(
            context.plan().userId(),
            personalizationEnabled(context.plan()),
            LearningPlanPersonalizationScenario.EXTENSION);
        agentRuntime.stream(invocation(context, personalizationSnapshot)).subscribe(new StreamSubscriber(
            publisher,
            projector,
            context.revision()));
      } catch (RuntimeException exception) {
        if (context == null) {
          publisher.fail(exception);
          return;
        }
        failStartupRevisionAndEmit(publisher, context.revision(), exception);
      }
    };
  }

  private void failStartupRevisionAndEmit(
      SingleSubscriberSynchronousPublisher<LearningPlanProposalStreamEvent> publisher,
      LearningPlanExtensionRevision revision,
      RuntimeException exception
  ) {
    String code = "LEARNING_PLAN_EXTENSION_STREAM_FAILED";
    String message = "学习计划扩展生成失败，请稍后重试。";
    log.warn(
        "Learning plan extension stream startup failed after revision creation: revisionId={}, code={}, causeMessage={}",
        revision.id(),
        code,
        exception.getMessage(),
        exception);
    try {
      LearningPlanProposalEvent event = transactionOperations.execute(status -> failureTransition(
          revision,
          code,
          message,
          false));
      publisher.emit(new LearningPlanProposalStreamEvent.Proposal(PROFILE, event));
      publisher.complete();
    } catch (RuntimeException persistenceFailure) {
      publisher.fail(persistenceFailure);
    }
  }

  private String requireInstruction(String instruction) {
    if (instruction == null || instruction.isBlank()) {
      throw new LearningPlanException("LEARNING_PLAN_EXTENSION_INSTRUCTION_REQUIRED", "扩展要求不能为空。");
    }
    return instruction.trim();
  }

  private SubscriptionRevisionContext prepareFirstRevision(
      long userId,
      long planId,
      String instruction,
      String runId,
      Map<String, Object> metadata
  ) {
    List<PracticeProgress> progress = practiceSessionRepository.findProgressByPlan(userId, planId);
    return transactionOperations.execute(status -> createFirstRevision(
        userId, planId, instruction, runId, metadata, progress));
  }

  private SubscriptionRevisionContext createFirstRevision(
      long userId,
      long planId,
      String instruction,
      String runId,
      Map<String, Object> metadata,
      List<PracticeProgress> progress
  ) {
    LearningPlan lockedPlan = lockActivePlan(userId, planId);
    LearningPlanProposalGroup group = latestActiveGroup(userId, planId)
        .orElseGet(() -> groupService.createGroup(
            userId,
            LearningPlanProposalType.PLAN_EXTENSION,
            LearningPlanProposalTargetType.PLAN,
            planId,
            instruction));
    LearningPlanExtensionRevision revision = createGeneratingRevision(
        lockedPlan,
        group,
        instruction,
        progress,
        null);
    return new SubscriptionRevisionContext(
        lockedPlan,
        group.id(),
        instruction,
        progress,
        null,
        runId,
        revision);
  }

  private SubscriptionRevisionContext prepareNextRevision(
      long userId,
      long planId,
      long proposalGroupId,
      String instruction,
      String runId,
      Map<String, Object> metadata
  ) {
    List<PracticeProgress> progress = practiceSessionRepository.findProgressByPlan(userId, planId);
    return transactionOperations.execute(status -> createNextRevision(
        userId, planId, proposalGroupId, instruction, runId, metadata, progress));
  }

  private SubscriptionRevisionContext createNextRevision(
      long userId,
      long planId,
      long proposalGroupId,
      String instruction,
      String runId,
      Map<String, Object> metadata,
      List<PracticeProgress> progress
  ) {
    LearningPlan lockedPlan = lockActivePlan(userId, planId);
    LearningPlanProposalGroup group = proposalRepository.findGroupForUserForUpdate(proposalGroupId, userId)
        .orElseThrow(() -> new LearningPlanException(
            "LEARNING_PLAN_PROPOSAL_GROUP_NOT_FOUND",
            "学习计划提案分组不存在。"));
    validateExtensionGroup(group, planId);
    LearningPlanExtensionRevision latestReady = proposalRepository.findLatestReadyExtensionRevision(group.id())
        .orElseThrow(() -> new LearningPlanException(
            "LEARNING_PLAN_EXTENSION_READY_REVISION_NOT_FOUND",
            "没有可修订的学习计划扩展提案。"));
    validateRevisionOwner(latestReady, userId, planId);
    LearningPlanExtensionRevision revision = createGeneratingRevision(
        lockedPlan,
        group,
        instruction,
        progress,
        latestReady.proposedExtension());
    return new SubscriptionRevisionContext(
        lockedPlan,
        group.id(),
        instruction,
        progress,
        latestReady.proposedExtension(),
        runId,
        revision);
  }

  private LearningPlan lockActivePlan(long userId, long planId) {
    LearningPlan lockedPlan = learningPlanRepository.findPlanByIdForUserForUpdate(planId, userId)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_NOT_FOUND", "学习计划不存在。"));
    if (lockedPlan.status() != LearningPlanStatus.ACTIVE) {
      throw new LearningPlanException("LEARNING_PLAN_EXTENSION_PLAN_NOT_ACTIVE", "当前学习计划状态不允许生成扩展。");
    }
    if (lockedPlan.plan() == null) {
      throw new LearningPlanException("LEARNING_PLAN_EXTENSION_PLAN_MISSING", "学习计划缺少可扩展的计划内容。");
    }
    return lockedPlan;
  }

  private Optional<LearningPlanProposalGroup> latestActiveGroup(long userId, long planId) {
    return proposalRepository.findLatestActiveGroup(
        userId,
        LearningPlanProposalType.PLAN_EXTENSION,
        LearningPlanProposalTargetType.PLAN,
        planId);
  }

  private LearningPlanExtensionRevision createGeneratingRevision(
      LearningPlan plan,
      LearningPlanProposalGroup group,
      String instruction,
      List<PracticeProgress> progress,
      LearningPlanExtensionDraft previousExtension
  ) {
    Instant now = clock.instant();
    return proposalRepository.saveExtensionRevision(new LearningPlanExtensionRevision(
        null,
        group.id(),
        plan.id(),
        plan.userId(),
        proposalRepository.nextRevisionNo(group.id()),
        LearningPlanProposalRevisionStatus.GENERATING,
        instruction,
        plan.plan(),
        progressSnapshot(progress),
        maxPhaseIndex(plan),
        previousExtension,
        null,
        null,
        null,
        null,
        now,
        now));
  }

  private Map<String, Object> progressSnapshot(List<PracticeProgress> progress) {
    List<PracticeProgress> items = progress == null ? List.of() : List.copyOf(progress);
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("total", items.size());
    snapshot.put("completed", items.stream().filter(item -> item.status() == PracticeProgressStatus.COMPLETED).count());
    snapshot.put("inProgress", items.stream().filter(item -> item.status() == PracticeProgressStatus.IN_PROGRESS).count());
    snapshot.put("skipped", items.stream().filter(item -> item.status() == PracticeProgressStatus.SKIPPED).count());
    snapshot.put("problems", items.stream()
        .map(item -> Map.<String, Object>of(
            "phaseIndex", item.phaseIndex(),
            "problemSlug", item.problemSlug(),
            "status", item.status().name()))
        .collect(Collectors.toList()));
    return snapshot;
  }

  private static int maxPhaseIndex(LearningPlan plan) {
    return plan.plan().phases().stream()
        .mapToInt(LearningPlanPhaseDraft::phaseIndex)
        .max()
        .orElse(0);
  }

  private static void validateExtensionGroup(LearningPlanProposalGroup group, long planId) {
    if (group.status() != LearningPlanProposalGroupStatus.ACTIVE
        || group.proposalType() != LearningPlanProposalType.PLAN_EXTENSION
        || group.targetType() != LearningPlanProposalTargetType.PLAN
        || group.targetId() != planId) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_GROUP_INVALID", "学习计划扩展提案组与请求不匹配。");
    }
  }

  private static void validateRevisionOwner(LearningPlanExtensionRevision revision, long userId, long planId) {
    if (revision.userId() != userId || revision.planId() != planId) {
      throw new LearningPlanException("LEARNING_PLAN_PROPOSAL_REVISION_INVALID", "学习计划扩展提案与请求不匹配。");
    }
  }

  private LearningPlanProposalEvent failureTransition(
      LearningPlanExtensionRevision revision,
      String code,
      String message,
      boolean retryable
  ) {
    LearningPlanExtensionRevision failedRevision = proposalRepository.saveExtensionRevision(revision.withFailure(
        code,
        message,
        clock.instant()));
    return new LearningPlanProposalEvent.ProposalError(
        failedRevision.errorCode(),
        failedRevision.errorMessage(),
        retryable);
  }

  private AgentInvocation<LearningPlanExtensionAgentInput> invocation(
      SubscriptionRevisionContext context,
      LearningPlanPersonalizationSnapshot personalizationSnapshot
  ) {
    return new AgentInvocation<>(
        LearningPlanExtensionAgentDefinition.KEY,
        new LearningPlanExtensionAgentInput(
            context.plan().userId(),
            context.plan().id(),
            context.proposalGroupId(),
            context.instruction(),
            context.plan(),
            context.progress(),
            context.previousExtension(),
            context.idempotencyKey(),
            personalizationSnapshot),
        new AgentInvocationContext(
            context.plan().userId(),
            AgentInvocationMode.USER_ENTRY,
            context.idempotencyKey(),
            null,
            null,
            context.instruction().getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
            true));
  }

  private static boolean personalizationEnabled(LearningPlan plan) {
    return Boolean.TRUE.equals(plan.plan().metadata().get(LearningPlanDraftMetadataKeys.PERSONALIZATION_ENABLED));
  }

  private AgentWorkStatusProfile learningPlanProfile() {
    return new AgentWorkStatusProfile(
        LearningPlanStreamConstants.SCENARIO,
        "开始生成学习计划扩展",
        "正在生成扩展",
        Map.of(
            LearningPlanAgentToolNames.LIST_PROBLEM_FILTERS, "正在查询题库标签",
            LearningPlanAgentToolNames.SEARCH_PROBLEMS, "正在搜索候选题"),
        24,
        Duration.ofMillis(500),
        true);
  }

  private record SubscriptionRevisionContext(
      LearningPlan plan,
      long proposalGroupId,
      String instruction,
      List<PracticeProgress> progress,
      LearningPlanExtensionDraft previousExtension,
      String idempotencyKey,
      LearningPlanExtensionRevision revision
  ) {
  }

  @FunctionalInterface
  private interface RevisionContextFactory {
    SubscriptionRevisionContext create();
  }

  private final class StreamSubscriber implements Flow.Subscriber<AgentStreamEvent> {

    private final SingleSubscriberSynchronousPublisher<LearningPlanProposalStreamEvent> publisher;
    private final AgentWorkStatusProjector projector;
    private final LearningPlanExtensionRevision revision;
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private final AtomicBoolean terminalProposalEmitted = new AtomicBoolean(false);
    private final StringBuilder stepContent = new StringBuilder();
    private String finalContent;

    private StreamSubscriber(
        SingleSubscriberSynchronousPublisher<LearningPlanProposalStreamEvent> publisher,
        AgentWorkStatusProjector projector,
        LearningPlanExtensionRevision revision
    ) {
      this.publisher = publisher;
      this.projector = projector;
      this.revision = revision;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
      this.subscription.set(subscription);
      subscription.request(1);
    }

    @Override
    public void onNext(AgentStreamEvent event) {
      captureContent(event);
      projector.project(event)
          .map(LearningPlanProposalStreamEvent.Work::new)
          .ifPresent(publisher::emit);
      if (event instanceof AgentStreamEvent.AgentRunEnd) {
        completeWithRevision();
        return;
      }
      if (event instanceof AgentStreamEvent.AgentError error) {
        failRevisionAndEmit(
            error.error().code().name(),
            "学习计划扩展生成失败，请稍后重试。",
            error.error().retryable(),
            error.error());
        return;
      }
      subscription.get().request(1);
    }

    @Override
    public void onError(Throwable throwable) {
      failRevisionAndEmit("LEARNING_PLAN_EXTENSION_STREAM_FAILED", "学习计划扩展生成失败，请稍后重试。", false, throwable);
    }

    @Override
    public void onComplete() {
      if (terminalProposalEmitted.get()) {
        publisher.complete();
        return;
      }
      failRevisionAndEmit(
          "LEARNING_PLAN_EXTENSION_STREAM_FAILED",
          "学习计划扩展生成失败，请稍后重试。",
          false,
          null);
    }

    private void completeWithRevision() {
      try {
        if (finalContent == null || finalContent.isBlank()) {
          throw new LearningPlanException("LEARNING_PLAN_EXTENSION_FINAL_OUTPUT_MISSING", "模型未返回扩展提案。");
        }
        LearningPlanExtensionDraft extension = outputMapper.map(
            objectMapper.readTree(finalContent),
            revision.basePlan().contentLocale());
        emitTerminalEvent(transactionOperations.execute(status -> completeReadyTransition(extension)));
      } catch (JsonProcessingException exception) {
        failRevisionAndEmit("LEARNING_PLAN_EXTENSION_STRUCTURED_OUTPUT_INVALID", "扩展提案结构化结果解析失败。", true, exception);
      } catch (LearningPlanException exception) {
        failRevisionAndEmit(exception.code(), exception.getMessage(), true, exception);
      } catch (RuntimeException exception) {
        failRevisionAndEmit(
            "LEARNING_PLAN_EXTENSION_REVISION_FAILED",
            "学习计划扩展生成失败，请稍后重试。",
            false,
            exception);
      }
    }

    private LearningPlanProposalEvent completeReadyTransition(LearningPlanExtensionDraft extension) {
      LearningPlan lockedPlan = lockActivePlan(revision.userId(), revision.planId());
      List<PracticeProgress> progress = practiceSessionRepository.findProgressByPlan(revision.userId(), revision.planId());
      validator.validate(extension, lockedPlan, progress);
      LearningPlanProposalGroup lockedGroup = proposalRepository.findGroupForUserForUpdate(
              revision.proposalGroupId(),
              lockedPlan.userId())
          .orElseThrow(() -> new LearningPlanException(
              "LEARNING_PLAN_PROPOSAL_GROUP_NOT_FOUND",
              "学习计划提案分组不存在。"));
      validateExtensionGroup(lockedGroup, lockedPlan.id());
      int nextRevisionNo = proposalRepository.nextRevisionNo(lockedGroup.id());
      Optional<LearningPlanProposalGroup> latestActiveGroup = latestActiveGroup(lockedPlan.userId(), lockedPlan.id());
      if (nextRevisionNo > revision.revisionNo() + 1
          || latestActiveGroup.isEmpty()
          || !Objects.equals(latestActiveGroup.get().id(), lockedGroup.id())) {
        return staleProposalError();
      }
      LearningPlanExtensionRevision readyRevision = proposalRepository.saveExtensionRevision(
          revision.withReady(revision.previousExtension(), extension, clock.instant()));
      List<Long> superseded = proposalRepository.markReadyExtensionRevisionsSuperseded(
          readyRevision.proposalGroupId(),
          readyRevision.id());
      proposalRepository.saveGroup(lockedGroup.withLatestProposalId(readyRevision.id(), clock.instant()));
      return new LearningPlanProposalEvent.PlanExtensionReady(
          LearningPlanExtensionResult.fromRevision(readyRevision, superseded));
    }

    private LearningPlanProposalEvent staleProposalError() {
      LearningPlanExtensionRevision staleRevision = proposalRepository.saveExtensionRevision(revision.withFailure(
          "LEARNING_PLAN_EXTENSION_REVISION_SUPERSEDED",
          "学习计划扩展结果已被更新的请求取代。",
          clock.instant()));
      return new LearningPlanProposalEvent.ProposalError(
          staleRevision.errorCode(),
          staleRevision.errorMessage(),
          false);
    }

    private void captureContent(AgentStreamEvent event) {
      if (event instanceof AgentStreamEvent.AgentStepStart) {
        stepContent.setLength(0);
        return;
      }
      if (event instanceof AgentStreamEvent.Llm llm && llm.event() instanceof LlmStreamEvent.ContentDelta delta) {
        stepContent.append(delta.content());
        return;
      }
      if (event instanceof AgentStreamEvent.AgentStepEnd end && end.toolCallCount() == 0) {
        finalContent = stepContent.toString();
      }
    }

    private void failRevisionAndEmit(String code, String message, boolean retryable, Throwable cause) {
      if (!terminalProposalEmitted.compareAndSet(false, true)) {
        return;
      }
      log.warn(
          "Learning plan extension stream failed: code={}, retryable={}, message={}, causeMessage={}",
          code,
          retryable,
          message,
          cause == null ? null : cause.getMessage(),
          cause);
      try {
        emitError(
            transactionOperations.execute(status -> failureTransition(revision, code, message, retryable)),
            code,
            message,
            retryable,
            cause);
      } catch (RuntimeException persistenceFailure) {
        publisher.fail(persistenceFailure);
      }
    }

    private void emitTerminalEvent(LearningPlanProposalEvent event) {
      if (!terminalProposalEmitted.compareAndSet(false, true)) {
        return;
      }
      publisher.emit(new LearningPlanProposalStreamEvent.Proposal(PROFILE, event));
      publisher.complete();
    }

    private void emitError(
        LearningPlanProposalEvent event,
        String code,
        String message,
        boolean retryable,
        Throwable cause
    ) {
      if (cause != null) {
        log.warn(
            "Learning plan extension stream emitted error: code={}, retryable={}, message={}, causeMessage={}",
            code,
            retryable,
            message,
            cause.getMessage(),
            cause);
      }
      publisher.emit(new LearningPlanProposalStreamEvent.Proposal(PROFILE, event));
      publisher.complete();
    }
  }
}
