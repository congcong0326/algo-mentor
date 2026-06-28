package org.congcong.algomentor.mentor.application.practice;

import java.util.Map;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.preference.UserAiPreference;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceRepository;
import org.congcong.algomentor.mentor.application.preference.UserAiPreferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 题目训练会话消息流式编排服务。
 */
public class PracticeMessageStreamService {

  private static final Logger log = LoggerFactory.getLogger(PracticeMessageStreamService.class);

  private final PracticeSessionRepository sessionRepository;
  private final PracticeTurnOrchestrator orchestrator;
  private final UserAiPreferenceService preferenceService;

  public PracticeMessageStreamService(
      PracticeSessionRepository sessionRepository,
      PracticeTurnOrchestrator orchestrator
  ) {
    this(sessionRepository, orchestrator, new UserAiPreferenceService(UserAiPreferenceRepository.empty()));
  }

  public PracticeMessageStreamService(
      PracticeSessionRepository sessionRepository,
      PracticeTurnOrchestrator orchestrator,
      UserAiPreferenceService preferenceService
  ) {
    if (sessionRepository == null) {
      throw new IllegalArgumentException("Practice session repository must not be null");
    }
    if (orchestrator == null) {
      throw new IllegalArgumentException("Practice turn orchestrator must not be null");
    }
    this.sessionRepository = sessionRepository;
    this.orchestrator = orchestrator;
    this.preferenceService = preferenceService == null
        ? new UserAiPreferenceService(UserAiPreferenceRepository.empty())
        : preferenceService;
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

    UserAiPreference preference = preferenceService.get(userId);
    Map<String, Object> metadata = new java.util.LinkedHashMap<>(
        governanceMetadata == null ? Map.of() : governanceMetadata);
    metadata.put(PracticeChatPromptConstants.METADATA_COACH_STYLE, preference.coachStyle().name());
    metadata.put(PracticeChatPromptConstants.METADATA_RESPONSE_LANGUAGE, preference.responseLanguage().name());

    Flow.Publisher<AgentStreamEvent> delegate = orchestrator.stream(
        userId,
        sessionId,
        message,
        idempotencyKey,
        locale,
        Map.copyOf(metadata));
    return new TouchingPublisher(delegate, sessionRepository, session.id());
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
          subscriber.onNext(item);
          if (item instanceof AgentStreamEvent.AgentRunEnd) {
            try {
              sessionRepository.touchLastMessageAt(sessionId);
            } catch (RuntimeException exception) {
              log.warn("Failed to touch practice session last message time. sessionId={}", sessionId, exception);
            }
          }
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
