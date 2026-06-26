package org.congcong.algomentor.mentor.application.practice;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationCommand;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunCoordinator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;

/**
 * 训练聊天单轮编排器：校验练习会话并启动普通聊天 run。
 */
public class PracticeTurnOrchestrator {

  private static final String DEFAULT_LOCALE = "zh-CN";

  private final PracticeSessionRepository sessionRepository;
  private final AgentConversationRunCoordinator coordinator;

  public PracticeTurnOrchestrator(
      PracticeSessionRepository sessionRepository,
      AgentConversationRunCoordinator coordinator
  ) {
    this.sessionRepository = Objects.requireNonNull(sessionRepository, "sessionRepository must not be null");
    this.coordinator = Objects.requireNonNull(coordinator, "coordinator must not be null");
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
    return coordinator.stream(command(
        session,
        userId,
        message,
        idempotencyKey,
        effectiveLocale,
        governanceMetadata));
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
}
