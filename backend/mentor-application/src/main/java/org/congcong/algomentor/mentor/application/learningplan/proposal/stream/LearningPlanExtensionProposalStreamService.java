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
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.congcong.algomentor.agent.core.AgentExecutionOptions;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentStructuredOutputOptions;
import org.congcong.algomentor.agent.core.StructuredOutputStrategy;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusProfile;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusProjector;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
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
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanStreamConstants;
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
  private final AgentLoopRunner agentLoopRunner;
  private final LearningPlanProposalPromptBuilder promptBuilder;
  private final ObjectMapper objectMapper;
  private final LearningPlanExtensionStructuredOutputMapper outputMapper;
  private final TransactionOperations transactionOperations;
  private final Clock clock;

  public LearningPlanExtensionProposalStreamService(
      LearningPlanRepository learningPlanRepository,
      LearningPlanProposalRepository proposalRepository,
      LearningPlanProposalGroupService groupService,
      PracticeSessionRepository practiceSessionRepository,
      LearningPlanExtensionValidator validator,
      AgentLoopRunner agentLoopRunner,
      LearningPlanProposalPromptBuilder promptBuilder,
      ObjectMapper objectMapper,
      TransactionOperations transactionOperations,
      Clock clock
  ) {
    this.learningPlanRepository = Objects.requireNonNull(learningPlanRepository, "learningPlanRepository");
    this.proposalRepository = Objects.requireNonNull(proposalRepository, "proposalRepository");
    this.groupService = Objects.requireNonNull(groupService, "groupService");
    this.practiceSessionRepository = Objects.requireNonNull(practiceSessionRepository, "practiceSessionRepository");
    this.validator = Objects.requireNonNull(validator, "validator");
    this.agentLoopRunner = Objects.requireNonNull(agentLoopRunner, "agentLoopRunner");
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.outputMapper = new LearningPlanExtensionStructuredOutputMapper(objectMapper);
    this.transactionOperations = Objects.requireNonNull(transactionOperations, "transactionOperations");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public Flow.Publisher<LearningPlanProposalStreamEvent> streamFirstRevision(
      long userId,
      long planId,
      String instruction,
      String runId,
      Map<String, Object> metadata
  ) {
    String normalizedInstruction = requireInstruction(instruction);
    return singleUsePublisher(() -> createFirstRevision(userId, planId, normalizedInstruction, runId, metadata));
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
    return singleUsePublisher(() -> createNextRevision(
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
      SubmissionPublisher<LearningPlanProposalStreamEvent> publisher = new SubmissionPublisher<>();
      publisher.subscribe(subscriber);
      try {
        SubscriptionRevisionContext context = transactionOperations.execute(status -> factory.create());
        AgentWorkStatusProjector projector = new AgentWorkStatusProjector(learningPlanProfile(), clock);
        agentLoopRunner.stream(context.request()).subscribe(new StreamSubscriber(
            publisher,
            projector,
            context.revision()));
      } catch (RuntimeException exception) {
        publisher.closeExceptionally(exception);
      }
    };
  }

  private String requireInstruction(String instruction) {
    if (instruction == null || instruction.isBlank()) {
      throw new LearningPlanException("LEARNING_PLAN_EXTENSION_INSTRUCTION_REQUIRED", "扩展要求不能为空。");
    }
    return instruction.trim();
  }

  private SubscriptionRevisionContext createFirstRevision(
      long userId,
      long planId,
      String instruction,
      String runId,
      Map<String, Object> metadata
  ) {
    LearningPlan lockedPlan = lockActivePlan(userId, planId);
    List<PracticeProgress> progress = practiceSessionRepository.findProgressByPlan(userId, planId);
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
        revision,
        new AgentRequest(
            runId,
            null,
            promptBuilder.buildExtensionPrompt(instruction, lockedPlan, progress),
            metadata,
            executionOptions()));
  }

  private SubscriptionRevisionContext createNextRevision(
      long userId,
      long planId,
      long proposalGroupId,
      String instruction,
      String runId,
      Map<String, Object> metadata
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
    List<PracticeProgress> progress = practiceSessionRepository.findProgressByPlan(userId, planId);
    LearningPlanExtensionRevision revision = createGeneratingRevision(
        lockedPlan,
        group,
        instruction,
        progress,
        latestReady.proposedExtension());
    return new SubscriptionRevisionContext(
        revision,
        new AgentRequest(
            runId,
            null,
            promptBuilder.buildExtensionRevisionPrompt(instruction, lockedPlan, progress, latestReady.proposedExtension()),
            metadata,
            executionOptions()));
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

  private AgentExecutionOptions executionOptions() {
    return new AgentExecutionOptions(
        LlmGenerationOptions.defaults(),
        new LlmResponseFormat.JsonSchema(
            LearningPlanStreamConstants.EXTENSION_SCHEMA_NAME,
            LearningPlanExtensionJsonSchema.schema(),
            true),
        new AgentStructuredOutputOptions(
            StructuredOutputStrategy.PROVIDER_NATIVE,
            LearningPlanStreamConstants.EXTENSION_SCHEMA_NAME,
            LearningPlanStreamConstants.EXTENSION_SCHEMA_VERSION,
            true));
  }

  private AgentWorkStatusProfile learningPlanProfile() {
    return new AgentWorkStatusProfile(
        LearningPlanStreamConstants.SCENARIO,
        "开始生成学习计划扩展",
        "正在生成扩展",
        Map.of(
            "list_problem_filters", "正在查询题库标签",
            "search_problems", "正在搜索候选题",
            "get_problem_statement", "正在读取题目信息"),
        24,
        Duration.ofMillis(500),
        true);
  }

  private record SubscriptionRevisionContext(
      LearningPlanExtensionRevision revision,
      AgentRequest request
  ) {
  }

  @FunctionalInterface
  private interface RevisionContextFactory {
    SubscriptionRevisionContext create();
  }

  private final class StreamSubscriber implements Flow.Subscriber<AgentStreamEvent> {

    private final SubmissionPublisher<LearningPlanProposalStreamEvent> publisher;
    private final AgentWorkStatusProjector projector;
    private final LearningPlanExtensionRevision revision;
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private final StringBuilder stepContent = new StringBuilder();
    private String finalContent;

    private StreamSubscriber(
        SubmissionPublisher<LearningPlanProposalStreamEvent> publisher,
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
          .ifPresent(publisher::submit);
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
      publisher.close();
    }

    private void completeWithRevision() {
      try {
        if (finalContent == null || finalContent.isBlank()) {
          throw new LearningPlanException("LEARNING_PLAN_EXTENSION_FINAL_OUTPUT_MISSING", "模型未返回扩展提案。");
        }
        LearningPlanExtensionDraft extension = outputMapper.map(objectMapper.readTree(finalContent));
        publisher.submit(new LearningPlanProposalStreamEvent.Proposal(
            PROFILE,
            transactionOperations.execute(status -> completeReadyTransition(extension))));
        publisher.close();
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
      log.warn(
          "Learning plan extension stream failed: code={}, retryable={}, message={}, causeMessage={}",
          code,
          retryable,
          message,
          cause == null ? null : cause.getMessage(),
          cause);
      proposalRepository.saveExtensionRevision(revision.withFailure(code, message, clock.instant()));
      emitError(code, message, retryable, cause);
    }

    private void emitError(String code, String message, boolean retryable, Throwable cause) {
      if (cause != null) {
        log.warn(
            "Learning plan extension stream emitted error: code={}, retryable={}, message={}, causeMessage={}",
            code,
            retryable,
            message,
            cause.getMessage(),
            cause);
      }
      publisher.submit(new LearningPlanProposalStreamEvent.Proposal(
          PROFILE,
          new LearningPlanProposalEvent.ProposalError(code, message, retryable)));
      publisher.close();
    }
  }
}
