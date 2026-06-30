package org.congcong.algomentor.mentor.api.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentLoopRunner;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.runtime.model.AgentActiveRun;
import org.congcong.algomentor.agent.core.runtime.model.AgentAssistantSeedMessageRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskCreationRequest;
import org.congcong.algomentor.agent.core.runtime.model.AgentTaskRef;
import org.congcong.algomentor.agent.core.runtime.repository.AgentConversationRepository;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTaskMessageRepository;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.agent.core.runlock.AgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.InMemoryAgentRunLockManager;
import org.congcong.algomentor.agent.core.runlock.LocalAgentRunLockOwnerProvider;
import org.congcong.algomentor.agent.core.runtime.context.ContextAssembler;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.practice.MicrometerPracticeCodeReviewMetrics;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemCatalog;
import org.congcong.algomentor.mentor.application.practice.PracticeCompletionGate;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReview;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentTool;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewMetrics;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewMetricStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewPermissionHook;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewSummary;
import org.congcong.algomentor.mentor.application.practice.PracticeMessageStreamService;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionService;
import org.congcong.algomentor.mentor.application.practice.PracticeTurnOrchestrator;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.OpsStatus;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
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

  @Test
  void practiceStreamServiceExistsWithoutReviewInfrastructure() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AgentConversationApiAutoConfiguration.class))
        .withUserConfiguration(PracticeStreamWithoutReviewDependencies.class)
        .run(context -> {
          assertThat(context).doesNotHaveBean(PracticeCodeReviewRepository.class);
          assertThat(context).doesNotHaveBean(LlmGateway.class);
          assertThat(context).hasSingleBean(PracticeTurnOrchestrator.class);
          assertThat(context).hasSingleBean(PracticeMessageStreamService.class);
        });
  }

  @Test
  void reviewInfrastructureDoesNotRegisterPostRunCapability() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AgentConversationApiAutoConfiguration.class))
        .withUserConfiguration(PracticeStreamWithReviewDependencies.class)
        .run(context -> {
          assertThat(context).hasSingleBean(PracticeCodeReviewService.class);
          assertThat(context).hasSingleBean(PracticeTurnOrchestrator.class);
        });
  }

  @Test
  void registersPracticeCodeReviewToolAndPermissionHookWhenDependenciesExist() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            AgentConversationApiAutoConfiguration.class))
        .withUserConfiguration(PracticeReviewToolDependencies.class)
        .run(context -> {
          assertThat(context).hasSingleBean(PracticeCodeReviewService.class);
          assertThat(context).hasSingleBean(PracticeCodeReviewAgentTool.class);
          assertThat(context).hasSingleBean(PracticeCodeReviewPermissionHook.class);
        });
  }

  @Test
  void missingPracticeCodeReviewServiceDoesNotRegisterToolOrBreakContext() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            AgentConversationApiAutoConfiguration.class))
        .withUserConfiguration(PracticeReviewHookOnlyDependencies.class)
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(PracticeCodeReviewService.class);
          assertThat(context).doesNotHaveBean(PracticeCodeReviewAgentTool.class);
          assertThat(context).hasSingleBean(PracticeCodeReviewPermissionHook.class);
        });
  }

  @Test
  void practiceReviewMetricsUsesMeterRegistryWhenAvailable() {
    contextRunner.withBean(MeterRegistry.class, SimpleMeterRegistry::new)
        .run(context -> {
          assertThat(context).hasSingleBean(PracticeCodeReviewMetrics.class);
          assertThat(context.getBean(PracticeCodeReviewMetrics.class))
              .isInstanceOf(MicrometerPracticeCodeReviewMetrics.class);
        });
  }

  @Test
  void practiceReviewMetricsUsesLearningOpsRecorderWhenAvailable() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    contextRunner
        .withBean(MeterRegistry.class, () -> registry)
        .withBean(LearningOpsRecorder.class, RecordingLearningOpsRecorder::new)
        .run(context -> {
          assertThat(context).hasSingleBean(PracticeCodeReviewMetrics.class);
          PracticeCodeReviewMetrics metrics = context.getBean(PracticeCodeReviewMetrics.class);
          RecordingLearningOpsRecorder recorder = context.getBean(RecordingLearningOpsRecorder.class);

          metrics.recordReview(PracticeCodeReviewMetricStatus.COMPLETED);
          metrics.recordReview(PracticeCodeReviewMetricStatus.FAILED);
          metrics.recordReview(PracticeCodeReviewMetricStatus.UNREVIEWABLE);

          assertThat(recorder.practiceCodeReviewStatuses)
              .containsExactly(OpsStatus.COMPLETED, OpsStatus.FAILED, OpsStatus.UNREVIEWABLE);
          metrics.recordCompletionGate(new PracticeCompletionGate(
              true,
              PracticeCompletionGate.ReasonCode.PASSED,
              "标记为已完成",
              Optional.of(BigDecimal.TEN),
              BigDecimal.TEN));
          assertThat(registry.get("practice.completion_gate.evaluations")
              .tag("canComplete", "true")
              .tag("reason", PracticeCompletionGate.ReasonCode.PASSED.name())
              .counter()
              .count())
              .isEqualTo(1.0);
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

  @Configuration(proxyBeanMethods = false)
  static class PracticeStreamWithoutReviewDependencies {

    @Bean
    PracticeSessionRepository practiceSessionRepository() {
      return new EmptyPracticeSessionRepository();
    }

    @Bean
    AgentConversationRepository agentConversationRepository() {
      return new EmptyAgentConversationRepository();
    }

    @Bean
    ContextAssembler contextAssembler() {
      return new ContextAssembler();
    }

    @Bean
    AgentLoopRunner agentLoopRunner() {
      return new EmptyAgentLoopRunner();
    }

    @Bean
    AgentRunLockManager agentRunLockManager() {
      return new InMemoryAgentRunLockManager();
    }

    @Bean
    LocalAgentRunLockOwnerProvider agentRunLockOwnerProvider() {
      return new LocalAgentRunLockOwnerProvider("test-owner");
    }

    @Bean
    PracticeChatProblemCatalog practiceChatProblemCatalog() {
      return (slug, locale) -> Optional.empty();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class PracticeStreamWithReviewDependencies extends PracticeStreamWithoutReviewDependencies {

    @Bean
    PracticeCodeReviewRepository practiceCodeReviewRepository() {
      return PracticeCodeReviewRepository.empty();
    }

    @Bean
    LlmGateway llmGateway() {
      return new EmptyLlmGateway();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class PracticeReviewToolDependencies extends PracticeStreamWithReviewDependencies {

    @Bean
    ObjectMapper objectMapper() {
      return new ObjectMapper();
    }

    @Bean
    AgentTurnMessageLookupRepository agentTurnMessageLookupRepository() {
      return new EmptyAgentTurnMessageLookupRepository();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class PracticeReviewHookOnlyDependencies {

    @Bean
    PracticeSessionRepository practiceSessionRepository() {
      return new EmptyPracticeSessionRepository();
    }

    @Bean
    AgentTurnMessageLookupRepository agentTurnMessageLookupRepository() {
      return new EmptyAgentTurnMessageLookupRepository();
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

  static final class RecordingLearningOpsRecorder implements LearningOpsRecorder {

    private final List<OpsStatus> learningPlanDraftStatuses = new java.util.ArrayList<>();
    private final List<OpsStatus> practiceMessageStreamStatuses = new java.util.ArrayList<>();
    private final List<OpsStatus> practiceCodeReviewStatuses = new java.util.ArrayList<>();

    @Override
    public void learningPlanDraft(OpsStatus status) {
      learningPlanDraftStatuses.add(status);
    }

    @Override
    public void practiceMessageStream(OpsStatus status) {
      practiceMessageStreamStatuses.add(status);
    }

    @Override
    public void practiceCodeReview(OpsStatus status) {
      practiceCodeReviewStatuses.add(status);
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

  private static final class EmptyAgentTurnMessageLookupRepository implements AgentTurnMessageLookupRepository {

    @Override
    public Optional<org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages> findByRunId(long runId) {
      return Optional.empty();
    }
  }

  private static final class EmptyAgentConversationRepository implements AgentConversationRepository {
    @Override
    public org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun createOrReuseRun(
        org.congcong.algomentor.agent.core.runtime.model.AgentRunPreparationRequest request
    ) {
      throw new UnsupportedOperationException("create run not used");
    }

    @Override
    public Optional<org.congcong.algomentor.agent.core.runtime.model.PreparedAgentRun> findRunByIdempotencyKey(
        String idempotencyKey
    ) {
      return Optional.empty();
    }

    @Override
    public List<AgentMessage> recentMessages(long taskId, int messageLimit) {
      return List.of();
    }
  }

  private static final class EmptyAgentLoopRunner extends AgentLoopRunner {
    private EmptyAgentLoopRunner() {
      super(new EmptyLlmGateway(), "stub-model", AgentToolRegistry.empty(), 1);
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentRequest request) {
      throw new UnsupportedOperationException("agent stream not used");
    }
  }

  private static final class EmptyLlmGateway implements LlmGateway {
    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("complete not used");
    }

    @Override
    public Flow.Publisher<LlmStreamEvent> stream(LlmCompletionRequest request) {
      throw new UnsupportedOperationException("llm stream not used");
    }
  }
}
