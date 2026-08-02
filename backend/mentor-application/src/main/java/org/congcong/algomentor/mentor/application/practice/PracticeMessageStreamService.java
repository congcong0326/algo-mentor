package org.congcong.algomentor.mentor.application.practice;

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
  private static final String DEFAULT_LOCALE = "zh-CN";

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
      int requestSize
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
    UserAiPreference preference = preferenceService.get(userId);
    Flow.Publisher<AgentStreamEvent> delegate = orchestrator.stream(new PracticeChatAgentInput(
        userId,
        session.id(),
        session.agentTaskId(),
        session.planId(),
        session.phaseIndex(),
        session.problemSlug(),
        message,
        idempotencyKey,
        effectiveLocale,
        preference.coachStyle(),
        PracticeResponseLanguage.fromLocale(effectiveLocale),
        requestSize));
    return new TouchingPublisher(delegate, sessionRepository, session.id());
  }

  private String effectiveLocale(String sessionLocale, String requestedLocale) {
    if (requestedLocale != null && !requestedLocale.isBlank()) {
      return requestedLocale.trim();
    }
    if (sessionLocale != null && !sessionLocale.isBlank()) {
      return sessionLocale.trim();
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
