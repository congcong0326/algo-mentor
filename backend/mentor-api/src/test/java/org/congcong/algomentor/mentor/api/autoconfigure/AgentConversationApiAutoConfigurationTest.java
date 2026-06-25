package org.congcong.algomentor.mentor.api.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.agent.core.runtime.model.AgentActiveRun;
import org.congcong.algomentor.agent.core.runtime.model.AgentAssistantSeedMessageRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskCreationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskRef;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemCatalog;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewSummary;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AgentConversationApiAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(AgentConversationApiAutoConfiguration.class))
      .withUserConfiguration(PracticeSessionServiceDependencies.class);

  @Test
  void practiceSessionServiceUsesAvailableReviewRepository() throws Exception {
    contextRunner.run(context -> {
      PracticeSessionService service = context.getBean(PracticeSessionService.class);
      PracticeCodeReviewRepository reviewRepository = context.getBean(PracticeCodeReviewRepository.class);

      assertThat(reviewRepositoryField(service)).isSameAs(reviewRepository);
    });
  }

  private static PracticeCodeReviewRepository reviewRepositoryField(PracticeSessionService service) throws Exception {
    Field field = PracticeSessionService.class.getDeclaredField("reviewRepository");
    field.setAccessible(true);
    return (PracticeCodeReviewRepository) field.get(service);
  }

  @Configuration(proxyBeanMethods = false)
  static class PracticeSessionServiceDependencies {

    @Bean
    LearningPlanRepository learningPlanRepository() {
      return new EmptyLearningPlanRepository();
    }

    @Bean
    PracticeChatProblemCatalog practiceChatProblemCatalog() {
      return (slug, locale) -> Optional.empty();
    }

    @Bean
    PracticeSessionRepository practiceSessionRepository() {
      return new EmptyPracticeSessionRepository();
    }

    @Bean
    AgentTaskMessageRepository agentTaskMessageRepository() {
      return new EmptyAgentTaskMessageRepository();
    }

    @Bean
    PracticeCodeReviewRepository practiceCodeReviewRepository() {
      return PracticeCodeReviewRepository.empty();
    }
  }

  private static final class EmptyLearningPlanRepository implements LearningPlanRepository {

    @Override
    public LearningPlan save(LearningPlan plan) {
      throw new UnsupportedOperationException("save not used");
    }

    @Override
    public List<LearningPlan> findByUserId(long userId) {
      return List.of();
    }

    @Override
    public Optional<LearningPlan> findPlanByIdForUser(long planId, long userId) {
      return Optional.empty();
    }
  }

  private static final class EmptyPracticeSessionRepository implements PracticeSessionRepository {

    @Override
    public PracticeProgress upsertAndAdvanceProgress(long userId, long planId, int phaseIndex, String problemSlug) {
      throw new UnsupportedOperationException("upsert progress not used");
    }

    @Override
    public PracticeSession upsertAndLockSession(
        long userId,
        long planId,
        int phaseIndex,
        String problemSlug,
        String locale) {
      throw new UnsupportedOperationException("upsert session not used");
    }

    @Override
    public Optional<PracticeSession> findSessionForUser(long sessionId, long userId) {
      return Optional.empty();
    }

    @Override
    public PracticeSession attachAgentTask(long sessionId, long agentTaskId) {
      throw new UnsupportedOperationException("attach task not used");
    }

    @Override
    public PracticeSession attachProblemStatementMessage(long sessionId, long messageId) {
      throw new UnsupportedOperationException("attach seed not used");
    }

    @Override
    public PracticeProgress updateProgressStatus(long sessionId, long userId, PracticeProgressStatus status) {
      throw new UnsupportedOperationException("update progress not used");
    }

    @Override
    public void touchLastMessageAt(long sessionId) {
    }
  }

  private static final class EmptyAgentTaskMessageRepository implements AgentTaskMessageRepository {

    @Override
    public AgentTaskRef createTask(AgentTaskCreationRequest request) {
      throw new UnsupportedOperationException("create task not used");
    }

    @Override
    public AgentMessage createAssistantSeedMessage(AgentAssistantSeedMessageRequest request) {
      throw new UnsupportedOperationException("seed message not used");
    }

    @Override
    public List<AgentMessage> messages(long taskId, int messageLimit) {
      return List.of();
    }

    @Override
    public Optional<AgentActiveRun> activeRun(long taskId) {
      return Optional.empty();
    }
  }
}
