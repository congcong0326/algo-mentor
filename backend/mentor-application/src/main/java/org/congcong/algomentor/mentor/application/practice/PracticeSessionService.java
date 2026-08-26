package org.congcong.algomentor.mentor.application.practice;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.congcong.algomentor.agent.core.runtime.model.AgentAssistantSeedMessageRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentActiveRun;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskCreationRequest;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanPhaseDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanProblemDraft;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPrompts;
import org.congcong.algomentor.mentor.application.prompt.ResolvedSystemPromptSnapshot;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptMetadataKeys;
import org.congcong.algomentor.mentor.application.prompt.SystemPromptSectionKeys;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryMessageAction;
import org.congcong.algomentor.mentor.application.practice.coachsummary.CoachSummaryProposalService;
import org.springframework.transaction.annotation.Transactional;

public class PracticeSessionService {

  private static final int MESSAGE_LIMIT = 200;

  private final LearningPlanRepository learningPlanRepository;
  private final PracticeChatProblemCatalog problemCatalog;
  private final PracticeSessionRepository practiceSessionRepository;
  private final AgentTaskMessageRepository agentTaskMessageRepository;
  private final PracticeCodeReviewRepository reviewRepository;
  private final PracticeCompletionGateService completionGateService;
  private final ManagedSystemPromptResolver systemPromptResolver;
  private final CoachSummaryProposalService coachSummaryProposalService;

  public PracticeSessionService(
      LearningPlanRepository learningPlanRepository,
      PracticeChatProblemCatalog problemCatalog,
      PracticeSessionRepository practiceSessionRepository,
      AgentTaskMessageRepository agentTaskMessageRepository) {
    this(learningPlanRepository, problemCatalog, practiceSessionRepository, agentTaskMessageRepository,
        PracticeCodeReviewRepository.empty(), PracticeCodeReviewMetrics.NOOP, ManagedSystemPrompts.defaultResolver());
  }

  public PracticeSessionService(
      LearningPlanRepository learningPlanRepository,
      PracticeChatProblemCatalog problemCatalog,
      PracticeSessionRepository practiceSessionRepository,
      AgentTaskMessageRepository agentTaskMessageRepository,
      PracticeCodeReviewRepository reviewRepository) {
    this(
        learningPlanRepository,
        problemCatalog,
        practiceSessionRepository,
        agentTaskMessageRepository,
        reviewRepository,
        PracticeCodeReviewMetrics.NOOP,
        ManagedSystemPrompts.defaultResolver());
  }

  public PracticeSessionService(
      LearningPlanRepository learningPlanRepository,
      PracticeChatProblemCatalog problemCatalog,
      PracticeSessionRepository practiceSessionRepository,
      AgentTaskMessageRepository agentTaskMessageRepository,
      PracticeCodeReviewRepository reviewRepository,
      PracticeCodeReviewMetrics metrics) {
    this(
        learningPlanRepository,
        problemCatalog,
        practiceSessionRepository,
        agentTaskMessageRepository,
        reviewRepository,
        metrics,
        ManagedSystemPrompts.defaultResolver(),
        null);
  }

  public PracticeSessionService(
      LearningPlanRepository learningPlanRepository,
      PracticeChatProblemCatalog problemCatalog,
      PracticeSessionRepository practiceSessionRepository,
      AgentTaskMessageRepository agentTaskMessageRepository,
      PracticeCodeReviewRepository reviewRepository,
      PracticeCodeReviewMetrics metrics,
      ManagedSystemPromptResolver systemPromptResolver) {
    this(
        learningPlanRepository,
        problemCatalog,
        practiceSessionRepository,
        agentTaskMessageRepository,
        reviewRepository,
        metrics,
        systemPromptResolver,
        null);
  }

  public PracticeSessionService(
      LearningPlanRepository learningPlanRepository,
      PracticeChatProblemCatalog problemCatalog,
      PracticeSessionRepository practiceSessionRepository,
      AgentTaskMessageRepository agentTaskMessageRepository,
      PracticeCodeReviewRepository reviewRepository,
      PracticeCodeReviewMetrics metrics,
      ManagedSystemPromptResolver systemPromptResolver,
      CoachSummaryProposalService coachSummaryProposalService) {
    this.learningPlanRepository = learningPlanRepository;
    this.problemCatalog = problemCatalog;
    this.practiceSessionRepository = practiceSessionRepository;
    this.agentTaskMessageRepository = agentTaskMessageRepository;
    this.reviewRepository = reviewRepository;
    this.completionGateService = new PracticeCompletionGateService(reviewRepository, metrics);
    this.systemPromptResolver = systemPromptResolver == null
        ? ManagedSystemPrompts.defaultResolver()
        : systemPromptResolver;
    this.coachSummaryProposalService = coachSummaryProposalService;
  }

  @Transactional
  public PracticeSessionResult createOrReuse(long userId, PracticeChatReference reference) {
    PracticeChatContext context = requireContext(userId, reference);
    PracticeProgress progress = practiceSessionRepository.upsertAndAdvanceProgress(
        userId, reference.planId(), reference.phaseIndex(), reference.problemSlug());
    PracticeSession session = practiceSessionRepository.upsertAndLockSession(
        userId, reference.planId(), reference.phaseIndex(), reference.problemSlug(), reference.locale());

    if (session.agentTaskId() == null) {
      ResolvedSystemPromptSnapshot promptSnapshot = systemPromptResolver.resolve(
          ManagedSystemPromptDefinitions.PRACTICE_CHAT, userId);
      AgentTaskCreationRequest request = new AgentTaskCreationRequest(
          userId,
          taskTitle(context.planProblem()),
          promptSnapshot.requireSection(SystemPromptSectionKeys.PRACTICE_TASK_BOOTSTRAP).text(),
          metadata(session.id(), reference, promptSnapshot));
      session = practiceSessionRepository.attachAgentTask(
          session.id(), agentTaskMessageRepository.createTask(request).taskId());
    }

    if (session.problemStatementMessageId() == null) {
      AgentMessage seedMessage = agentTaskMessageRepository.createAssistantSeedMessage(
              new AgentAssistantSeedMessageRequest(
                  session.agentTaskId(),
                  seedContent(
                      context.problemDetail(),
                      context.plan().plan().programmingLanguage(),
                      reference.locale()),
                  messageMetadata(session.id(), reference, PracticeChatPromptConstants.MESSAGE_TYPE_PROBLEM_STATEMENT)));
      session = practiceSessionRepository.attachProblemStatementMessage(session.id(), seedMessage.id());
    }

    return result(
        withProgressStatus(session, progress.status()),
        context.problemDetail(),
        MESSAGE_LIMIT,
        context.plan().plan().programmingLanguage());
  }

  public PracticeSessionResult get(long userId, long sessionId) {
    return get(userId, sessionId, MESSAGE_LIMIT);
  }

  public PracticeSessionResult get(long userId, long sessionId, int messageLimit) {
    PracticeSession session = requireSession(userId, sessionId);
    PracticeChatReference reference = new PracticeChatReference(
        session.planId(), session.phaseIndex(), session.problemSlug(), session.locale());
    PracticeChatContext context = requireContext(userId, reference);
    return result(session, context.problemDetail(), messageLimit, context.plan().plan().programmingLanguage());
  }

  public PracticeCodeReviewHistory history(long userId, long sessionId) {
    PracticeSession session = requireSession(userId, sessionId);
    List<PracticeCodeReviewSummary> reviews = reviewRepository.findSummaries(userId, session.id());
    Optional<PracticeCodeReviewSummary> latestReview = reviews.stream().findFirst();
    PracticeCompletionGate completionGate = completionGateService.evaluate(userId, session, latestReview);
    return new PracticeCodeReviewHistory(latestReview.orElse(null), reviews, completionGate);
  }

  public PracticeCodeReview detail(long userId, long sessionId, long reviewId) {
    PracticeSession session = requireSession(userId, sessionId);
    return reviewRepository.findById(userId, session.id(), reviewId)
        .orElseThrow(() -> new LearningPlanException("PRACTICE_CODE_REVIEW_NOT_FOUND", "题目练习代码 Review 不存在。"));
  }

  public CoachSummaryMessageAction applyCoachSummaryProposal(
      long userId,
      long sessionId,
      String proposalId
  ) {
    if (coachSummaryProposalService == null) {
      throw new LearningPlanException(
          "COACH_SUMMARY_PROPOSAL_SERVICE_UNAVAILABLE", "教练总结候选服务不可用。");
    }
    return coachSummaryProposalService.apply(userId, sessionId, proposalId);
  }

  @Transactional
  public PracticeSession updateProgressStatus(long userId, long sessionId, PracticeProgressStatus status) {
    if (status != PracticeProgressStatus.COMPLETED && status != PracticeProgressStatus.SKIPPED) {
      throw new LearningPlanException("PRACTICE_PROGRESS_STATUS_UNSUPPORTED", "题目聊天页只支持标记完成或跳过。");
    }
    PracticeSession session = practiceSessionRepository.findSessionForUser(sessionId, userId)
        .orElseThrow(() -> new LearningPlanException("PRACTICE_SESSION_NOT_FOUND", "题目练习会话不存在。"));
    if (status == PracticeProgressStatus.COMPLETED) {
      PracticeCompletionGate gate = completionGateService.evaluate(userId, session);
      if (!gate.canComplete()) {
        throw switch (gate.reasonCode()) {
          case NO_REVIEW -> new LearningPlanException("PRACTICE_COMPLETION_REVIEW_REQUIRED", gate.message());
          case LATEST_REVIEW_FAILED -> new LearningPlanException("PRACTICE_COMPLETION_REVIEW_NOT_PASSED", gate.message());
          case ALREADY_COMPLETED -> new LearningPlanException("PRACTICE_PROGRESS_ALREADY_COMPLETED", gate.message());
          case PASSED -> new IllegalStateException("Passed gate cannot block completion");
        };
      }
    }
    PracticeProgress progress = practiceSessionRepository.updateProgressStatus(sessionId, userId, status);
    return withProgressStatus(session, progress.status());
  }

  private PracticeSession requireSession(long userId, long sessionId) {
    return practiceSessionRepository.findSessionForUser(sessionId, userId)
        .orElseThrow(() -> new LearningPlanException("PRACTICE_SESSION_NOT_FOUND", "题目练习会话不存在。"));
  }

  private PracticeSessionResult result(
      PracticeSession session,
      PracticeChatProblemDetail problemDetail,
      int messageLimit,
      String programmingLanguage
  ) {
    int effectiveLimit = messageLimit < 1 ? MESSAGE_LIMIT : Math.min(messageLimit, MESSAGE_LIMIT);
    List<PracticeSessionMessage> messages = session.agentTaskId() == null
        ? List.of()
        : agentTaskMessageRepository.messages(session.agentTaskId(), effectiveLimit).stream()
            .sorted(Comparator.comparingLong(AgentMessage::sequenceNo))
            .map(this::toPracticeSessionMessage)
            .toList();
    messages = projectCurrentProblemStatement(messages, problemDetail, programmingLanguage, session.locale());
    messages = enrichCoachSummaryActions(session, messages);
    Optional<AgentActiveRun> activeRun = session.agentTaskId() == null
        ? Optional.empty()
        : agentTaskMessageRepository.activeRun(session.agentTaskId());
    Optional<PracticeCodeReviewSummary> latestReview = reviewRepository.findLatestSummary(session.userId(), session.id());
    PracticeCompletionGate completionGate = completionGateService.evaluate(session.userId(), session, latestReview);
    return new PracticeSessionResult(session, problemDetail, messages, activeRun, latestReview.orElse(null), completionGate);
  }

  private List<PracticeSessionMessage> projectCurrentProblemStatement(
      List<PracticeSessionMessage> messages,
      PracticeChatProblemDetail problemDetail,
      String programmingLanguage,
      String locale
  ) {
    String currentContent = seedContent(problemDetail, programmingLanguage, locale);
    return messages.stream().map(message -> {
      if (!PracticeChatPromptConstants.MESSAGE_TYPE_PROBLEM_STATEMENT.equals(message.messageType())) {
        return message;
      }
      return new PracticeSessionMessage(
          message.id(),
          message.role(),
          message.messageType(),
          currentContent,
          message.createdAt(),
          message.coachSummaryAction());
    }).toList();
  }

  private PracticeSession withProgressStatus(PracticeSession session, PracticeProgressStatus status) {
    return new PracticeSession(
        session.id(),
        session.userId(),
        session.planId(),
        session.phaseIndex(),
        session.problemSlug(),
        session.status(),
        session.agentTaskId(),
        session.problemStatementMessageId(),
        status,
        session.lastMessageAt(),
        session.createdAt(),
        session.updatedAt(),
        session.locale());
  }

  private PracticeSessionMessage toPracticeSessionMessage(AgentMessage message) {
    Object messageType = message.metadata().get(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY);
    return new PracticeSessionMessage(
        message.id(),
        message.role().name(),
        messageType instanceof String value ? value : PracticeChatPromptConstants.MESSAGE_TYPE_CHAT,
        message.content(),
        message.createdAt());
  }

  private List<PracticeSessionMessage> enrichCoachSummaryActions(
      PracticeSession session,
      List<PracticeSessionMessage> messages
  ) {
    if (coachSummaryProposalService == null || messages.isEmpty()) {
      return messages;
    }
    List<Long> assistantMessageIds = messages.stream()
        .filter(message -> "ASSISTANT".equals(message.role()))
        .map(PracticeSessionMessage::id)
        .toList();
    Map<Long, CoachSummaryMessageAction> actions = coachSummaryProposalService.findMessageActions(
            session.userId(), session.id(), assistantMessageIds).stream()
        .collect(Collectors.toMap(CoachSummaryMessageAction::assistantMessageId, Function.identity()));
    if (actions.isEmpty()) {
      return messages;
    }
    return messages.stream().map(message -> {
      CoachSummaryMessageAction action = actions.get(message.id());
      if (action == null) {
        return message;
      }
      return new PracticeSessionMessage(
          message.id(),
          message.role(),
          message.messageType(),
          action.summaryMarkdown(),
          message.createdAt(),
          action);
    }).toList();
  }

  private PracticeChatContext requireContext(long userId, PracticeChatReference reference) {
    LearningPlan plan = learningPlanRepository.findPlanByIdForUser(reference.planId(), userId)
        .orElseThrow(() -> new LearningPlanException("PRACTICE_CHAT_PLAN_NOT_FOUND", "学习计划不存在。"));
    LearningPlanPhaseDraft phase = plan.plan().phases().stream()
        .filter(candidate -> candidate.phaseIndex() == reference.phaseIndex())
        .findFirst()
        .orElseThrow(() -> new LearningPlanException("PRACTICE_CHAT_PHASE_NOT_FOUND", "学习计划阶段不存在。"));
    LearningPlanProblemDraft problem = phase.problems().stream()
        .filter(candidate -> reference.problemSlug().equals(candidate.slug()))
        .findFirst()
        .orElseThrow(() -> new LearningPlanException("PRACTICE_CHAT_PROBLEM_NOT_FOUND", "学习计划题目不存在。"));
    PracticeChatProblemDetail problemDetail = problemCatalog.findProblemBySlug(reference.problemSlug(), reference.locale())
        .orElseThrow(() -> new LearningPlanException("PRACTICE_CHAT_PROBLEM_DETAIL_NOT_FOUND", "题库题目不存在。"));
    return new PracticeChatContext(plan, phase, problem, problemDetail, reference.locale());
  }

  private String taskTitle(LearningPlanProblemDraft problem) {
    String title = problem.titleCn() == null || problem.titleCn().isBlank() ? problem.title() : problem.titleCn();
    if (title == null || title.isBlank()) {
      title = problem.slug();
    }
    return "题目练习：" + title;
  }

  private String seedContent(PracticeChatProblemDetail detail, String programmingLanguage, String locale) {
    if (detail.contentMarkdown() == null || detail.contentMarkdown().isBlank()) {
      return appendCodeTemplate("题库暂未提供题面 Markdown。", detail.templateFor(programmingLanguage), locale);
    }
    return appendCodeTemplate(detail.contentMarkdown(), detail.templateFor(programmingLanguage), locale);
  }

  private String appendCodeTemplate(String statement, PracticeCodeTemplate template, String locale) {
    if (template == null) {
      return statement;
    }
    String heading = PracticeResponseLanguage.fromLocale(locale) == PracticeResponseLanguage.EN_US
        ? "## Code Template (%s)"
        : "## 代码模板（%s）";
    return "%s\n\n%s\n\n```%s\n%s\n```"
        .formatted(statement.strip(), heading.formatted(template.languageLabel()), template.languageSlug(), template.code())
        .strip();
  }

  private Map<String, Object> metadata(long sessionId, PracticeChatReference reference) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
    metadata.put(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, sessionId);
    metadata.put(PracticeChatPromptConstants.METADATA_PLAN_ID, reference.planId());
    metadata.put(PracticeChatPromptConstants.METADATA_PHASE_INDEX, reference.phaseIndex());
    metadata.put(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, reference.problemSlug());
    metadata.put(PracticeChatPromptConstants.METADATA_LOCALE, reference.locale());
    return metadata;
  }

  private Map<String, Object> metadata(
      long sessionId,
      PracticeChatReference reference,
      ResolvedSystemPromptSnapshot promptSnapshot
  ) {
    Map<String, Object> metadata = metadata(sessionId, reference);
    metadata.putAll(SystemPromptMetadataKeys.from(promptSnapshot));
    return metadata;
  }

  private Map<String, Object> messageMetadata(
      long sessionId,
      PracticeChatReference reference,
      String messageType) {
    Map<String, Object> metadata = new LinkedHashMap<>(metadata(sessionId, reference));
    metadata.put(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, messageType);
    return metadata;
  }

}
