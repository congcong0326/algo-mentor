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
import java.util.concurrent.SubmissionPublisher;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.agent.core.AgentExecutionOptions;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentStructuredOutputOptions;
import org.congcong.algomentor.agent.core.StructuredOutputStrategy;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusProfile;
import org.congcong.algomentor.agent.core.work.AgentWorkStatusProjector;
import org.congcong.algomentor.llm.core.request.LlmGenerationOptions;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.request.LlmResponseFormat;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftResult;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftStatus;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanDraftValidator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemCatalog;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevision;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanDraftRevisionResult;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroup;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalGroupService;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRepository;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalRevisionStatus;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalTargetType;
import org.congcong.algomentor.mentor.application.learningplan.proposal.LearningPlanProposalType;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftJsonSchema;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftPromptBuilder;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftStructuredOutputMapper;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanStreamConstants;
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
  private final AgentLoopRunner agentLoopRunner;
  private final LearningPlanDraftPromptBuilder promptBuilder;
  private final LearningPlanDraftStructuredOutputMapper outputMapper;
  private final ObjectMapper objectMapper;
  private final TransactionOperations transactionOperations;
  private final Clock clock;

  public LearningPlanDraftRevisionStreamService(
      LearningPlanDraftRepository draftRepository,
      LearningPlanProposalRepository proposalRepository,
      LearningPlanProposalGroupService groupService,
      LearningPlanDraftValidator validator,
      AgentLoopRunner agentLoopRunner,
      LearningPlanDraftPromptBuilder promptBuilder,
      ObjectMapper objectMapper,
      LearningPlanProblemCatalog problemCatalog,
      TransactionOperations transactionOperations,
      Clock clock
  ) {
    this.draftRepository = Objects.requireNonNull(draftRepository, "draftRepository");
    this.proposalRepository = Objects.requireNonNull(proposalRepository, "proposalRepository");
    this.groupService = Objects.requireNonNull(groupService, "groupService");
    this.validator = Objects.requireNonNull(validator, "validator");
    this.agentLoopRunner = Objects.requireNonNull(agentLoopRunner, "agentLoopRunner");
    this.promptBuilder = Objects.requireNonNull(promptBuilder, "promptBuilder");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.outputMapper = new LearningPlanDraftStructuredOutputMapper(objectMapper, problemCatalog);
    this.transactionOperations = Objects.requireNonNull(transactionOperations, "transactionOperations");
    this.clock = Objects.requireNonNull(clock, "clock");
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
      SubmissionPublisher<LearningPlanProposalStreamEvent> publisher = new SubmissionPublisher<>();
      publisher.subscribe(subscriber);
      try {
        SubscriptionRevisionContext context = transactionOperations.execute(status -> createSubscriptionRevision(
            userId,
            draftId,
            normalizedInstruction,
            runId,
            metadata));
        AgentWorkStatusProjector projector = new AgentWorkStatusProjector(learningPlanProfile(), clock);
        agentLoopRunner.stream(context.request()).subscribe(new StreamSubscriber(
            publisher,
            projector,
            context.draft(),
            context.revision()));
      } catch (RuntimeException exception) {
        publisher.closeExceptionally(exception);
      }
    };
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
    AgentRequest request = new AgentRequest(
        runId,
        null,
        buildRevisionPrompt(lockedDraft, instruction),
        metadata,
        executionOptions());
    return new SubscriptionRevisionContext(lockedDraft, revision, request);
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

  private List<LlmMessage> buildRevisionPrompt(LearningPlanDraft draft, String instruction) {
    List<LlmMessage> messages = new ArrayList<>(promptBuilder.build(draft.command()));
    messages.add(LlmMessage.assistant("""
        当前学习计划草案 JSON：
        %s
        """.formatted(toJson(draft.draftPlan()))));
    messages.add(LlmMessage.user("""
        请基于当前学习计划草案和用户修订要求，输出一份完整的新学习计划草案 JSON。

        用户修订要求：
        %s
        """.formatted(instruction)));
    return messages;
  }

  private String toJson(LearningPlanDraftPlan plan) {
    try {
      return objectMapper.writeValueAsString(plan);
    } catch (JsonProcessingException exception) {
      throw new LearningPlanException("LEARNING_PLAN_DRAFT_PLAN_INVALID", "学习计划草案内容无法序列化。");
    }
  }

  private AgentExecutionOptions executionOptions() {
    return new AgentExecutionOptions(
        LlmGenerationOptions.defaults(),
        new LlmResponseFormat.JsonSchema(
            LearningPlanStreamConstants.SCHEMA_NAME,
            LearningPlanDraftJsonSchema.schema(),
            true),
        new AgentStructuredOutputOptions(
            StructuredOutputStrategy.PROVIDER_NATIVE,
            LearningPlanStreamConstants.SCHEMA_NAME,
            LearningPlanStreamConstants.SCHEMA_VERSION,
            true));
  }

  private AgentWorkStatusProfile learningPlanProfile() {
    return new AgentWorkStatusProfile(
        LearningPlanStreamConstants.SCENARIO,
        "开始修订学习计划",
        "正在修订",
        Map.of(
            "list_problem_filters", "正在查询题库标签",
            "search_problems", "正在搜索候选题",
            "get_problem_statement", "正在读取题目信息"),
        24,
        Duration.ofMillis(500),
        true);
  }

  private record SubscriptionRevisionContext(
      LearningPlanDraft draft,
      LearningPlanDraftRevision revision,
      AgentRequest request
  ) {
  }

  private final class StreamSubscriber implements Flow.Subscriber<AgentStreamEvent> {

    private final SubmissionPublisher<LearningPlanProposalStreamEvent> publisher;
    private final AgentWorkStatusProjector projector;
    private final LearningPlanDraft draft;
    private final LearningPlanDraftRevision revision;
    private final AtomicReference<Flow.Subscription> subscription = new AtomicReference<>();
    private final StringBuilder stepContent = new StringBuilder();
    private String finalContent;

    private StreamSubscriber(
        SubmissionPublisher<LearningPlanProposalStreamEvent> publisher,
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
          .ifPresent(publisher::submit);
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
      publisher.close();
    }

    private void completeWithRevision() {
      try {
        if (finalContent == null || finalContent.isBlank()) {
          throw new LearningPlanException("LEARNING_PLAN_FINAL_OUTPUT_MISSING", "模型未返回学习计划修订结果。");
        }
        LearningPlanDraftPlan plan = outputMapper.map(objectMapper.readTree(finalContent), draft.command());
        validator.validateGeneratedPlan(plan);
        publisher.submit(new LearningPlanProposalStreamEvent.Proposal(
            PROFILE,
            transactionOperations.execute(status -> completeReadyTransition(plan))));
        publisher.close();
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

    private LearningPlanProposalEvent completeReadyTransition(LearningPlanDraftPlan plan) {
      LearningPlanProposalGroup lockedGroup = proposalRepository.findGroupForUserForUpdate(
              revision.proposalGroupId(),
              draft.userId())
          .orElseThrow(() -> new LearningPlanException("LEARNING_PLAN_PROPOSAL_GROUP_NOT_FOUND", "学习计划提案分组不存在。"));
      int nextRevisionNo = proposalRepository.nextRevisionNo(lockedGroup.id());
      Optional<LearningPlanProposalGroup> latestActiveGroup = latestActiveGroup(draft.userId(), draft.id());
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
      LearningPlanDraft savedDraft = draftRepository.save(draftWithRevisionPlan(plan));
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

    private LearningPlanDraft draftWithRevisionPlan(LearningPlanDraftPlan plan) {
      Instant now = clock.instant();
      List<String> messages = new ArrayList<>(draft.messages());
      messages.add(revision.instruction());
      return new LearningPlanDraft(
          draft.id(),
          draft.userId(),
          LearningPlanDraftStatus.GENERATED,
          draft.command(),
          messages,
          List.of(),
          "已生成学习计划修订草案。",
          plan,
          draft.confirmedPlanId(),
          draft.expiresAt(),
          draft.createdAt(),
          now);
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
          "Learning plan draft revision stream failed: code={}, retryable={}, message={}, causeMessage={}",
          code,
          retryable,
          message,
          cause == null ? null : cause.getMessage(),
          cause);
      proposalRepository.saveDraftRevision(revision.withFailure(code, message, clock.instant()));
      emitError(code, message, retryable, cause);
    }

    private void emitError(String code, String message, boolean retryable, Throwable cause) {
      if (cause != null) {
        log.warn(
            "Learning plan draft revision stream emitted error: code={}, retryable={}, message={}, causeMessage={}",
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
