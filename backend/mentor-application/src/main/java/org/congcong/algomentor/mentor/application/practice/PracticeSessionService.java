package org.congcong.algomentor.mentor.application.practice;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import org.springframework.transaction.annotation.Transactional;

public class PracticeSessionService {

  private static final int MESSAGE_LIMIT = 200;
  private static final String DEFAULT_SYSTEM_PROMPT = "你是 algo-mentor 的算法刷题教练，请基于题目和学习计划进行分层引导。";

  private final LearningPlanRepository learningPlanRepository;
  private final PracticeChatProblemCatalog problemCatalog;
  private final PracticeSessionRepository practiceSessionRepository;
  private final AgentTaskMessageRepository agentTaskMessageRepository;
  private final PracticeCodeReviewRepository reviewRepository;
  private final PracticeCompletionGateService completionGateService;

  public PracticeSessionService(
      LearningPlanRepository learningPlanRepository,
      PracticeChatProblemCatalog problemCatalog,
      PracticeSessionRepository practiceSessionRepository,
      AgentTaskMessageRepository agentTaskMessageRepository) {
    this(learningPlanRepository, problemCatalog, practiceSessionRepository, agentTaskMessageRepository,
        PracticeCodeReviewRepository.empty());
  }

  public PracticeSessionService(
      LearningPlanRepository learningPlanRepository,
      PracticeChatProblemCatalog problemCatalog,
      PracticeSessionRepository practiceSessionRepository,
      AgentTaskMessageRepository agentTaskMessageRepository,
      PracticeCodeReviewRepository reviewRepository) {
    this.learningPlanRepository = learningPlanRepository;
    this.problemCatalog = problemCatalog;
    this.practiceSessionRepository = practiceSessionRepository;
    this.agentTaskMessageRepository = agentTaskMessageRepository;
    this.reviewRepository = reviewRepository;
    this.completionGateService = new PracticeCompletionGateService(reviewRepository);
  }

  @Transactional
  public PracticeSessionResult createOrReuse(long userId, PracticeChatReference reference) {
    PracticeChatContext context = requireContext(userId, reference);
    PracticeProgress progress = practiceSessionRepository.upsertAndAdvanceProgress(
        userId, reference.planId(), reference.phaseIndex(), reference.problemSlug());
    PracticeSession session = practiceSessionRepository.upsertAndLockSession(
        userId, reference.planId(), reference.phaseIndex(), reference.problemSlug(), reference.locale());

    if (session.agentTaskId() == null) {
      AgentTaskCreationRequest request = new AgentTaskCreationRequest(
          userId,
          taskTitle(context.planProblem()),
          DEFAULT_SYSTEM_PROMPT,
          metadata(session.id(), reference));
      session = practiceSessionRepository.attachAgentTask(
          session.id(), agentTaskMessageRepository.createTask(request).taskId());
    }

    if (session.problemStatementMessageId() == null) {
      AgentMessage seedMessage = agentTaskMessageRepository.createAssistantSeedMessage(
          new AgentAssistantSeedMessageRequest(
              session.agentTaskId(),
              seedContent(context.problemDetail()),
              messageMetadata(session.id(), reference, PracticeChatPromptConstants.MESSAGE_TYPE_PROBLEM_STATEMENT)));
      session = practiceSessionRepository.attachProblemStatementMessage(session.id(), seedMessage.id());
    }

    return result(withProgressStatus(session, progress.status()), context.problemDetail());
  }

  public PracticeSessionResult get(long userId, long sessionId) {
    return get(userId, sessionId, MESSAGE_LIMIT);
  }

  public PracticeSessionResult get(long userId, long sessionId, int messageLimit) {
    PracticeSession session = practiceSessionRepository.findSessionForUser(sessionId, userId)
        .orElseThrow(() -> new LearningPlanException("PRACTICE_SESSION_NOT_FOUND", "题目练习会话不存在。"));
    PracticeChatReference reference = new PracticeChatReference(
        session.planId(), session.phaseIndex(), session.problemSlug(), session.locale());
    PracticeChatContext context = requireContext(userId, reference);
    return result(session, context.problemDetail(), messageLimit);
  }

  @Transactional
  public PracticeSession updateProgressStatus(long userId, long sessionId, PracticeProgressStatus status) {
    if (status != PracticeProgressStatus.COMPLETED) {
      throw new LearningPlanException("PRACTICE_PROGRESS_STATUS_UNSUPPORTED", "题目聊天页只支持标记完成。");
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

  private PracticeSessionResult result(PracticeSession session, PracticeChatProblemDetail problemDetail) {
    return result(session, problemDetail, MESSAGE_LIMIT);
  }

  private PracticeSessionResult result(PracticeSession session, PracticeChatProblemDetail problemDetail, int messageLimit) {
    int effectiveLimit = messageLimit < 1 ? MESSAGE_LIMIT : Math.min(messageLimit, MESSAGE_LIMIT);
    List<PracticeSessionMessage> messages = session.agentTaskId() == null
        ? List.of()
        : agentTaskMessageRepository.messages(session.agentTaskId(), effectiveLimit).stream()
            .sorted(Comparator.comparingLong(AgentMessage::sequenceNo))
            .map(this::toPracticeSessionMessage)
            .toList();
    Optional<AgentActiveRun> activeRun = session.agentTaskId() == null
        ? Optional.empty()
        : agentTaskMessageRepository.activeRun(session.agentTaskId());
    Optional<PracticeCodeReviewSummary> latestReview = reviewRepository.findLatestSummary(session.userId(), session.id());
    PracticeCompletionGate completionGate = completionGateService.evaluate(session.userId(), session, latestReview);
    return new PracticeSessionResult(session, problemDetail, messages, activeRun, latestReview.orElse(null), completionGate);
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

  private String seedContent(PracticeChatProblemDetail detail) {
    if (detail.contentMarkdown() == null || detail.contentMarkdown().isBlank()) {
      return "题库暂未提供题面 Markdown。";
    }
    return detail.contentMarkdown();
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

  private Map<String, Object> messageMetadata(
      long sessionId,
      PracticeChatReference reference,
      String messageType) {
    Map<String, Object> metadata = new LinkedHashMap<>(metadata(sessionId, reference));
    metadata.put(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, messageType);
    return metadata;
  }

}
