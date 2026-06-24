package org.congcong.algomentor.mentor.application.practice;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationCommand;
import org.congcong.algomentor.mentor.application.conversation.AgentConversationRunCoordinator;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;

/**
 * 题目训练会话消息流式编排服务。
 */
public class PracticeMessageStreamService {

  private static final String DEFAULT_LOCALE = "zh-CN";

  private final PracticeSessionRepository sessionRepository;
  private final AgentConversationRunCoordinator coordinator;

  public PracticeMessageStreamService(
      PracticeSessionRepository sessionRepository,
      AgentConversationRunCoordinator coordinator
  ) {
    if (sessionRepository == null) {
      throw new IllegalArgumentException("Practice session repository must not be null");
    }
    if (coordinator == null) {
      throw new IllegalArgumentException("Agent conversation run coordinator must not be null");
    }
    this.sessionRepository = sessionRepository;
    this.coordinator = coordinator;
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
    Map<String, Object> metadata = new HashMap<>(governanceMetadata == null ? Map.of() : governanceMetadata);
    metadata.put(PracticeChatPromptConstants.METADATA_SCENARIO, PracticeChatPromptConstants.SCENARIO);
    metadata.put(PracticeChatPromptConstants.METADATA_PRACTICE_SESSION_ID, session.id());
    metadata.put(PracticeChatPromptConstants.METADATA_PLAN_ID, session.planId());
    metadata.put(PracticeChatPromptConstants.METADATA_PHASE_INDEX, session.phaseIndex());
    metadata.put(PracticeChatPromptConstants.METADATA_PROBLEM_SLUG, session.problemSlug());
    metadata.put(PracticeChatPromptConstants.METADATA_LOCALE, effectiveLocale);
    metadata.put(PracticeChatPromptConstants.MESSAGE_TYPE_METADATA_KEY, PracticeChatPromptConstants.MESSAGE_TYPE_CHAT);

    Flow.Publisher<AgentStreamEvent> delegate = coordinator.stream(new AgentConversationCommand(
        session.agentTaskId(),
        userId,
        message,
        idempotencyKey,
        Map.copyOf(metadata),
        new PracticeChatReference(session.planId(), session.phaseIndex(), session.problemSlug(), effectiveLocale)));
    return new TouchingPublisher(delegate, sessionRepository, session.id());
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

  private record TouchingPublisher(
      Flow.Publisher<AgentStreamEvent> delegate,
      PracticeSessionRepository sessionRepository,
      long sessionId
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
          if (item instanceof AgentStreamEvent.AgentRunEnd) {
            sessionRepository.touchLastMessageAt(sessionId);
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
