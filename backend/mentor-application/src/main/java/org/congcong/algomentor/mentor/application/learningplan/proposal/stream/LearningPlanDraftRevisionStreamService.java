package org.congcong.algomentor.mentor.application.learningplan.proposal.stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusProfile;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusProjector;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationContext;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanBrief;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanCoveragePolicy;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanLoadService;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevisionResult;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroup;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalTargetType;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalType;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanAgentToolNames;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStructuredOutputMapper;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanStreamConstants;
import org.congcong.algomentor.mentor.application.learningplan.stream.SingleSubscriberSynchronousPublisher;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationContextService;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationSnapshot;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionOperations;

/**
 * 学习计划草案修订流式生成编排服务。
 */
public class LearningPlanDraftRevisionStreamService {

  private static final Logger log = LoggerFactory.getLogger(LearningPlanDraftRevisionStreamService.class);
  private static final LearningPlanProposalStreamEvent.ProposalProfile PROFILE =
      LearningPlanProposalStreamEvent.ProposalProfile.DRAFT_REVISION;

  private final LearningPlanDraftRepository draftRepository;
  private final LearningPlanProposalRepository proposalRepository;
  private final LearningPlanProposalGroupService groupService;
  private final LearningPlanDraftValidator validator;
  private final AgentRuntime agentRuntime;
  private final LearningPlanDraftRevisionStructuredOutputMapper outputMapper;
  private final LearningPlanLoadService loadService;
  private final ObjectMapper objectMapper;
  private final TransactionOperations transactionOperations;
  private final Clock clock;
  private final LearningPlanPersonalizationContextService personalizationContextService;

  public LearningPlanDraftRevisionStreamService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanProposalRepository proposalRepository,
      LearningPlanProposalGroupService groupService,
      LearningPlanDraftValidator validator,
      AgentRuntime agentRuntime,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      LearningPlanLoadService loadService,
      TransactionOperations transactionOperations,
      Clock clock
  ) {
    this(
        draftRepository,
        proposalRepository,
        groupService,
        validator,
        agentRuntime,
        objectMapper,
        problemCatalog,
        loadService,
        transactionOperations,
        clock,
        new LearningPlanPersonalizationContextService(null));
  }

  public LearningPlanDraftRevisionStreamService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanProposalRepository proposalRepository,
      LearningPlanProposalGroupService groupService,
      LearningPlanDraftValidator validator,
      AgentRuntime agentRuntime,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      LearningPlanLoadService loadService,
      TransactionOperations transactionOperations,
      Clock clock,
      LearningPlanPersonalizationContextService personalizationContextService
  ) {
    this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository");
    this.proposalRepository = Objects.requireNonNull(proposalRepository, "proposalRepository");
    this.groupService = Objects.requireNonNull(groupService, "groupService");
    this.validator = Objects.requireNonNull(validator, "validator");
    this.agentRuntime = Objects.requireNonNull(agentRuntime, "agentRuntime");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    LearningPlanDraftStructuredOutputMapper generatedContentMapper =
        new LearningPlanDraftStructuredOutputMapper(objectMapper, problemCatalog);
    this.outputMapper = new LearningPlanDraftRevisionStructuredOutputMapper(
        objectMapper, generatedContentMapper, validator);
    this.loadService = Objects.requireNonNull(loadService, "loadService");
    this.transactionOperations = Objects.requireNonNull(transactionOperations, "transactionOperations");
    this.clock = Objects.requireNonNull(clock, "clock");
    this.personalizationContextService = Objects.requireNonNull(
        personalizationContextService, "personalizationContextService");
  }

  public Flow.Publisher<LearningPlanProposalStreamEvent> stream(
      long userId,
      long draftId,
      String instruction,
      String runId,
      Map<String, Object> metadata
  ) {
    String normalizedInstruction = requireInstruction(instruction);
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
        subscriber.onError(new IllegalStateException("Learning plan draft revision stream publisher is single-use"));
        return;
      }
      SingleSubscriberSynchronousPublisher<LearningPlanProposalStreamEvent> publisher =
          new SingleSubscriberSynchronousPublisher<>();
      publisher.subscribe(subscriber);
      SubscriptionRevisionContext context = null;
      try {
        context = transactionOperations.execute(status -> createSubscriptionRevision(
            userId,
            draftId,
            normalizedInstruction,
            runId,
            metadata));
        AgentWorkStatusProjector projector = new AgentWorkStatusProjector(learningPlanProfile(), clock);
        LearningPlanPersonalizationSnapshot personalizationSnapshot = personalizationContextService.snapshot(
            context.draft().userId(),
            context.draft().brief().personalizationEnabled(),
            LearningPlanPersonalizationScenario.REVISION);
        agentRuntime.stream(invocation(
            context.draft(), context.revision().instruction(), runId, personalizationSnapshot)).subscribe(new StreamSubscriber(
            publisher,
            projector,
            context.draft(),
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
      LearningPlanDraftRevision revision,
      RuntimeException exception
  ) {
    String code = "LEARNING_PLAN_DRAFT_REVISION_STREAM_FAILED";
    String message = "学习计划修订失败，请稍后重试。";
    log.warn(
        "Learning plan draft revision stream startup failed after revision creation: revisionId={}, code={}, causeMessage={}",
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
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_REVISION_INSTRUCTION_REQUIRED", "修订要求不能为空。");
    }
    return instruction.trim();
  }

  private Optional<LearningPlanProposalGroup> latestActiveGroup(long userId, long draftId) {
    return proposalRepository.findLatestActiveGroup(
        userId,
        LearningPlanProposalType.DRAFT_REVISION,
        LearningPlanProposalTargetType.DRAFT,
        draftId);
  }

  private SubscriptionRevisionContext createSubscriptionRevision(
      long userId,
      long draftId,
      String instruction,
      String runId,
      Map<String, Object> metadata
  ) {
    LearningPlanDraft lockedDraft = draftRepository.findDraftByIdForUserForUpdate(draftId, userId)
        .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_DRAFT_NOT_FOUND", "学习计划草案不存在。"));
    validateRevisionDraft(lockedDraft);
    LearningPlanProposalGroup group = latestActiveGroup(userId, draftId)
        .orElseGet(() -> groupService.createGroup(
            userId,
            LearningPlanProposalType.DRAFT_REVISION,
            LearningPlanProposalTargetType.DRAFT,
            draftId,
            instruction));
    LearningPlanDraftRevision revision = createGeneratingRevision(lockedDraft, group, instruction);
    return new SubscriptionRevisionContext(
        lockedDraft,
        revision);
  }

  private void validateRevisionDraft(LearningPlanDraft draft) {
    if (draft.status() == LearningPlanDraftStatus.CONFIRMED) {
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_REVISION_NOT_ALLOWED", "已确认的学习计划草案不能继续修订。");
    }
    if (draft.draftPlan() == null) {
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_PLAN_MISSING", "学习计划草案缺少可修订的计划内容。");
    }
  }

  private LearningPlanDraftRevision createGeneratingRevision(
      LearningPlanDraft draft,
      LearningPlanProposalGroup group,
      String instruction
  ) {
    Instant now = clock.instant();
    return proposalRepository.saveDraftRevision(new LearningPlanDraftRevision(
        null,
        group.id(),
        draft.id(),
        draft.userId(),
        proposalRepository.nextRevisionNo(group.id()),
        LearningPlanProposalRevisionStatus.GENERATING,
        instruction,
        draft.draftPlan(),
        null,
        null,
        null,
        now,
        now));
  }

  private LearningPlanProposalEvent failureTransition(
      LearningPlanDraftRevision revision,
      String code,
      String message,
      boolean retryable
  ) {
    LearningPlanDraftRevision failedRevision = proposalRepository.saveDraftRevision(revision.withFailure(
        code,
        message,
        clock.instant()));
    return new LearningPlanProposalEvent.ProposalError(
        failedRevision.errorCode(),
        failedRevision.errorMessage(),
        retryable);
  }

  private AgentInvocation<LearningPlanDraftRevisionAgentInput> invocation(
      LearningPlanDraft draft,
      String instruction,
      String idempotencyKey,
      LearningPlanPersonalizationSnapshot personalizationSnapshot
  ) {
    return new AgentInvocation<>(
        LearningPlanDraftRevisionAgentDefinition.KEY,
        new LearningPlanDraftRevisionAgentInput(
            draft.userId(),
            draft.id(),
            instruction,
            draft.brief(),
            draft.draftPlan(),
            idempotencyKey,
            personalizationSnapshot),
        new AgentInvocationContext(
            draft.userId(),
            AgentInvocationMode.USER_ENTRY,
            idempotencyKey,
            null,
            null,
            instruction.getBytes(java.nio.charset.StandardCharsets.UTF_8).length,
            true));
  }

  private AgentWorkStatusProfile learningPlanProfile() {
    return new AgentWorkStatusProfile(
        LearningPlanStreamConstants.SCENARIO,
        "开始修订学习计划",
        "正在修订",
        Map.of(
            LearningPlanAgentToolNames.LIST_PROBLEM_FILTERS, "正在查询题库标签",
            LearningPlanAgentToolNames.SEARCH_PROBLEMS, "正在搜索候选题"),
        24,
        Duration.ofMillis(500),
        true);
  }

  private record SubscriptionRevisionContext(
      LearningPlanDraft draft,
      LearningPlanDraftRevision revision
  ) {
  }

  private final class StreamSubscriber implements Flow.Subscriber<AgentStreamEvent> {

    private final SingleSubscriberSynchronousPublisher<LearningPlanProposalStreamEvent> publisher;
    private final AgentWorkStatusProjector projector;
    private final LearningPlanDraft draft;
    private final LearningPlanDraftRevision revision;
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private final AtomicBoolean terminalProposalEmitted = new AtomicBoolean(false);
    private final StringBuilder stepContent = new StringBuilder();
    private String finalContent;

    private StreamSubscriber(
        SingleSubscriberSynchronousPublisher<LearningPlanProposalStreamEvent> publisher,
        AgentWorkStatusProjector projector,
        LearningPlanDraft draft,
        LearningPlanDraftRevision revision
    ) {
      this.publisher = publisher;
      this.projector = projector;
      this.draft = draft;
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
            "学习计划修订失败，请稍后重试。",
            error.error().retryable(),
            error.error());
        return;
      }
      subscription.get().request(1);
    }

    @Override
    public void onError(Throwable throwable) {
      failRevisionAndEmit("LEARNING_PLAN_DRAFT_REVISION_STREAM_FAILED", "学习计划修订失败，请稍后重试。", false, throwable);
    }

    @Override
    public void onComplete() {
      if (terminalProposalEmitted.get()) {
        publisher.complete();
        return;
      }
      failRevisionAndEmit(
          "LEARNING_PLAN_DRAFT_REVISION_STREAM_FAILED",
          "学习计划修订失败，请稍后重试。",
          false,
          null);
    }

    private void completeWithRevision() {
      try {
        if (finalContent == null || finalContent.isBlank()) {
          throw new LearningPlanException("LEARNING_PLAN_FINAL_OUTPUT_MISSING", "模型未返回学习计划修订结果。");
        }
        LearningPlanDraftRevisionOutput output = outputMapper.map(
            objectMapper.readTree(finalContent), draft.brief());
        LearningPlanDraftPlan plan = loadService.withLoadMetadata(
            output.generatedPlan(),
            LearningPlanCoveragePolicy.FIT_USER_BUDGET);
        validator.validateGeneratedPlan(plan);
        emitTerminalEvent(transactionOperations.execute(status -> completeReadyTransition(output.resolvedBrief(), plan)));
      } catch (JsonProcessingException exception) {
        failRevisionAndEmit("LEARNING_PLAN_STRUCTURED_OUTPUT_INVALID", "学习计划修订结构化结果解析失败。", true, exception);
      } catch (LearningPlanException exception) {
        failRevisionAndEmit(exception.code(), exception.getMessage(), true, exception);
      } catch (RuntimeException exception) {
        failRevisionAndEmit(
            "LEARNING_PLAN_DRAFT_REVISION_FAILED",
            "学习计划修订失败，请稍后重试。",
            false,
            exception);
      }
    }

    private LearningPlanProposalEvent completeReadyTransition(
        LearningPlanBrief resolvedBrief,
        LearningPlanDraftPlan plan
    ) {
      LearningPlanDraft lockedDraft = draftRepository.findDraftByIdForUserForUpdate(draft.id(), draft.userId())
          .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_DRAFT_NOT_FOUND", "学习计划草案不存在。"));
      validateRevisionDraft(lockedDraft);
      LearningPlanProposalGroup lockedGroup = proposalRepository.findGroupForUserForUpdate(
              revision.proposalGroupId(),
              lockedDraft.userId())
          .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_PROPOSAL_GROUP_NOT_FOUND", "学习计划提案分组不存在。"));
      int nextRevisionNo = proposalRepository.nextRevisionNo(lockedGroup.id());
      Optional<LearningPlanProposalGroup> latestActiveGroup = latestActiveGroup(lockedDraft.userId(), lockedDraft.id());
      if (nextRevisionNo > revision.revisionNo() + 1
          || latestActiveGroup.isEmpty()
          || !Objects.equals(latestActiveGroup.get().id(), lockedGroup.id())) {
        return staleProposalError();
      }
      LearningPlanDraftRevision readyRevision = proposalRepository.saveDraftRevision(
          revision.withReady(plan, clock.instant()));
      List<Long> superseded = proposalRepository.markReadyDraftRevisionsSuperseded(
          readyRevision.proposalGroupId(),
          readyRevision.id());
      proposalRepository.saveGroup(lockedGroup.withLatestProposalId(readyRevision.id(), clock.instant()));
      LearningPlanDraft savedDraft = draftRepository.save(draftWithRevisionPlan(lockedDraft, resolvedBrief, plan));
      return new LearningPlanProposalEvent.DraftRevisionReady(LearningPlanDraftRevisionResult.fromRevision(
          readyRevision,
          superseded,
          LearningPlanDraftResult.fromDraft(savedDraft)));
    }

    private LearningPlanProposalEvent staleProposalError() {
      LearningPlanDraftRevision staleRevision = proposalRepository.saveDraftRevision(revision.withFailure(
          "LEARNING_PLAN_DRAFT_REVISION_SUPERSEDED",
          "学习计划修订结果已被更新的请求取代。",
          clock.instant()));
      return new LearningPlanProposalEvent.ProposalError(
          staleRevision.errorCode(),
          staleRevision.errorMessage(),
          false);
    }

    private LearningPlanDraft draftWithRevisionPlan(
        LearningPlanDraft currentDraft,
        LearningPlanBrief resolvedBrief,
        LearningPlanDraftPlan plan
    ) {
      Instant now = clock.instant();
      List<String> messages = new ArrayList<>(currentDraft.messages());
      messages.add(revision.instruction());
      return currentDraft.withGeneratedRevision(resolvedBrief, messages, plan, now);
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
          "Learning plan draft revision stream failed: code={}, retryable={}, message={}, causeMessage={}",
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
            "Learning plan draft revision stream emitted error: code={}, retryable={}, message={}, causeMessage={}",
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
