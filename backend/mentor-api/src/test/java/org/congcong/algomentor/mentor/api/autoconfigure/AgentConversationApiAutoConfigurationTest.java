package org.congcong.algomentor.mentor.api.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentRequest;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.agent.core.AgentTool;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.toolresult.ToolResultReadGuard;
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
import org.congcong.algomentor.agent.runtime.definition.AgentDefinitionRegistry;
import org.congcong.algomentor.api.config.MentorAiConfiguration;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlan;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanRepository;
import org.congcong.algomentor.mentor.application.practice.MicrometerPracticeCodeReviewMetrics;
import org.congcong.algomentor.mentor.application.practice.PracticeChatProblemCatalog;
import org.congcong.algomentor.mentor.application.practice.PracticeCompletionGate;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentTool;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentDefinition;
import org.congcong.algomentor.mentor.application.practice.PracticeChatAgentDefinition;
import org.congcong.algomentor.mentor.application.practice.PracticeChatRunAdapter;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewMetrics;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewMetricStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewPermissionHook;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewService;
import org.congcong.algomentor.mentor.application.practice.PracticeMessageStreamService;
import org.congcong.algomentor.mentor.application.practice.PracticeProgress;
import org.congcong.algomentor.mentor.application.practice.PracticeProgressStatus;
import org.congcong.algomentor.mentor.application.practice.PracticeSession;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionRepository;
import org.congcong.algomentor.mentor.application.practice.PracticeSessionService;
import org.congcong.algomentor.mentor.application.practice.PracticeTurnOrchestrator;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTagCatalog;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateAgentDefinition;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallPromptSectionProvider;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallService;
import org.congcong.algomentor.mentor.application.profile.claim.repository.LearnerMemoryClaimRepository;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimQueryService;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimSnapshotFactory;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimTextHasher;
import org.congcong.algomentor.mentor.application.profile.evidence.repository.LearnerMemoryEvidenceRepository;
import org.congcong.algomentor.mentor.application.profile.evidence.service.LearnerMemoryEvidenceGradeCalculator;
import org.congcong.algomentor.mentor.application.profile.evidence.service.LearnerMemoryEvidenceValidator;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryAtomicApplyService;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewBatchConsumer;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewFactRepository;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewUpdateAgentDefinition;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewUpdateService;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistoryRepository;
import org.congcong.algomentor.mentor.application.profile.run.repository.LearnerMemoryUpdateRunRepository;
import org.congcong.algomentor.mentor.application.profile.run.service.LearnerMemoryUpdateRunLifecycleService;
import org.congcong.algomentor.mentor.application.profile.tool.UpdateLearnerDeclaredProfileAgentTool;
import org.congcong.algomentor.mentor.application.profile.tool.GetLearnerMemoryEvidenceAgentTool;
import org.congcong.algomentor.mentor.application.profile.tool.ReadLearnerMemorySectionAgentTool;
import org.congcong.algomentor.mentor.application.profile.tool.SearchLearnerMemoryAgentTool;
import org.congcong.algomentor.llm.core.gateway.LlmGateway;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.stream.LlmStreamEvent;
import org.congcong.algomentor.llm.core.tool.LlmToolSpec;
import org.congcong.algomentor.ops.observability.LearningOpsRecorder;
import org.congcong.algomentor.ops.observability.OpsStatus;
import org.congcong.algomentor.ops.observability.autoconfigure.OpsObservabilityAutoConfiguration;
import org.congcong.algomentor.queue.config.PersistentQueueAutoConfiguration;
import org.congcong.algomentor.queue.postgres.QueueMessageMapper;
import org.congcong.algomentor.queue.repository.QueueMessageRepository;
import org.congcong.algomentor.queue.runtime.PersistentQueueWorkerManager;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class AgentConversationApiAutoConfigurationTest {

  private static final String TEST_PRACTICE_TOOL_NAME = "test_practice_tool";

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
  void registersEnabledDeclaredProfileCapabilityInTheRuntimeRegistry() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            AgentConversationApiAutoConfiguration.class))
        .withUserConfiguration(
            PracticeStreamWithoutReviewDependencies.class,
            MentorAiConfiguration.class,
            LearnerMemoryUpdateDependencies.class)
        .withPropertyValues("algo-mentor.learner-memory.declared-update.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(DeclaredProfileUpdateAgentDefinition.class);
          assertThat(context).hasSingleBean(DeclaredProfileUpdateService.class);
          assertThat(context).hasSingleBean(UpdateLearnerDeclaredProfileAgentTool.class);
          assertThat(context.getBean(AgentDefinitionRegistry.class)
              .resolve(DeclaredProfileUpdateAgentDefinition.KEY))
              .isSameAs(context.getBean(DeclaredProfileUpdateAgentDefinition.class));
        });
  }

  @Test
  void doesNotRegisterDeclaredProfileToolWhenRuntimeIsUnavailable() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            AgentConversationApiAutoConfiguration.class))
        .withPropertyValues("algo-mentor.learner-memory.declared-update.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(DeclaredProfileUpdateAgentDefinition.class);
          assertThat(context).doesNotHaveBean(DeclaredProfileUpdateService.class);
          assertThat(context).doesNotHaveBean(UpdateLearnerDeclaredProfileAgentTool.class);
        });
  }

  @Test
  void doesNotRegisterDeclaredProfileToolWhenDefinitionIsDisabled() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            AgentConversationApiAutoConfiguration.class))
        .withBean(AgentRuntime.class, () -> mock(AgentRuntime.class))
        .run(context -> {
          assertThat(context).doesNotHaveBean(DeclaredProfileUpdateAgentDefinition.class);
          assertThat(context).doesNotHaveBean(DeclaredProfileUpdateService.class);
          assertThat(context).doesNotHaveBean(UpdateLearnerDeclaredProfileAgentTool.class);
        });
  }

  @Test
  void registersEnabledLearnerMemoryCodeReviewDefinitionButNotConsumerWithoutQueueWorker() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            AgentConversationApiAutoConfiguration.class))
        .withUserConfiguration(
            PracticeStreamWithoutReviewDependencies.class,
            MentorAiConfiguration.class,
            LearnerMemoryUpdateDependencies.class)
        .withBean(LearnerMemoryCodeReviewFactRepository.class, () -> mock(LearnerMemoryCodeReviewFactRepository.class))
        .withBean(CodeReviewHistoryRepository.class, () -> mock(CodeReviewHistoryRepository.class))
        .withPropertyValues("algo-mentor.learner-memory.code-review-consumer.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(LearnerMemoryCodeReviewUpdateAgentDefinition.class);
          assertThat(context).hasSingleBean(LearnerMemoryCodeReviewUpdateService.class);
          assertThat(context).doesNotHaveBean(LearnerMemoryCodeReviewBatchConsumer.class);
          assertThat(context.getBean(AgentDefinitionRegistry.class)
              .resolve(LearnerMemoryCodeReviewUpdateAgentDefinition.KEY))
              .isSameAs(context.getBean(LearnerMemoryCodeReviewUpdateAgentDefinition.class));
        });
  }

  @Test
  void registersEnabledLearnerMemoryCodeReviewConsumerWhenQueueWorkerIsAvailable() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            AgentConversationApiAutoConfiguration.class))
        .withUserConfiguration(
            PracticeStreamWithoutReviewDependencies.class,
            MentorAiConfiguration.class,
            LearnerMemoryUpdateDependencies.class)
        .withBean(LearnerMemoryCodeReviewFactRepository.class, () -> mock(LearnerMemoryCodeReviewFactRepository.class))
        .withBean(CodeReviewHistoryRepository.class, () -> mock(CodeReviewHistoryRepository.class))
        .withBean(QueueMessageRepository.class, () -> mock(QueueMessageRepository.class))
        .withBean(PersistentQueueWorkerManager.class, () -> mock(PersistentQueueWorkerManager.class))
        .withPropertyValues("algo-mentor.learner-memory.code-review-consumer.enabled=true")
        .run(context -> assertThat(context).hasSingleBean(LearnerMemoryCodeReviewBatchConsumer.class));
  }

  @Test
  void doesNotRegisterLearnerMemoryCodeReviewConsumerWhenRuntimeIsUnavailable() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            AgentConversationApiAutoConfiguration.class))
        .withBean(LearnerMemoryCodeReviewFactRepository.class, () -> mock(LearnerMemoryCodeReviewFactRepository.class))
        .withBean(CodeReviewHistoryRepository.class, () -> mock(CodeReviewHistoryRepository.class))
        .withPropertyValues("algo-mentor.learner-memory.code-review-consumer.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(LearnerMemoryCodeReviewUpdateAgentDefinition.class);
          assertThat(context).doesNotHaveBean(LearnerMemoryCodeReviewUpdateService.class);
          assertThat(context).doesNotHaveBean(LearnerMemoryCodeReviewBatchConsumer.class);
        });
  }

  @Test
  void registersCompletePracticeCodeReviewCapabilityFromRealQueueAutoConfiguration() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            PersistentQueueAutoConfiguration.class,
            AgentConversationApiAutoConfiguration.class,
            MentorAiConfiguration.class))
        .withUserConfiguration(
            PracticeReviewToolDependencies.class,
            PersistentQueueStorageDependencies.class)
        .withPropertyValues("algo-mentor.practice.code-review.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(org.congcong.algomentor.queue.publisher.QueuePublisher.class);
          assertThat(context).hasSingleBean(PracticeCodeReviewService.class);
          assertThat(context).hasSingleBean(PracticeCodeReviewAgentTool.class);
          assertThat(context).hasSingleBean(PracticeCodeReviewPermissionHook.class);
          assertThat(context.getBean(AgentDefinitionRegistry.class)
              .resolve(PracticeCodeReviewAgentDefinition.KEY))
              .isSameAs(context.getBean(PracticeCodeReviewAgentDefinition.class));
        });
  }

  @Test
  void disabledPracticeCodeReviewDoesNotRegisterPartialCapability() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            AgentConversationApiAutoConfiguration.class))
        .withUserConfiguration(PracticeReviewHookOnlyDependencies.class)
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(PracticeCodeReviewService.class);
          assertThat(context).doesNotHaveBean(PracticeCodeReviewAgentTool.class);
          assertThat(context).doesNotHaveBean(PracticeCodeReviewPermissionHook.class);
        });
  }

  @Test
  void enabledPracticeCodeReviewFailsStartupWithoutQueuePublisher() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            JacksonAutoConfiguration.class,
            AgentConversationApiAutoConfiguration.class))
        .withUserConfiguration(PracticeReviewToolDependencies.class)
        .withPropertyValues("algo-mentor.practice.code-review.enabled=true")
        .run(context -> {
          assertThat(context).hasFailed();
          assertThat(context.getStartupFailure())
              .hasMessageContaining(org.congcong.algomentor.queue.publisher.QueuePublisher.class.getName());
        });
  }

  @Test
  void createsLearnerMemoryRecallServiceWithClaimInfrastructureAndCatalog() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AgentConversationApiAutoConfiguration.class))
        .withUserConfiguration(LearnerMemoryUpdateDependencies.class)
        .withBean(ProblemTagMapper.class, () -> mock(ProblemTagMapper.class))
        .withPropertyValues("algo-mentor.learner-memory.recall.practice-chat.enabled=true")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).hasSingleBean(TrustedProblemTagCatalog.class);
          assertThat(context).hasSingleBean(LearnerMemoryRecallService.class);
          assertThat(context).hasSingleBean(LearnerMemoryRecallPromptSectionProvider.class);
          assertThat(context).hasSingleBean(SearchLearnerMemoryAgentTool.class);
          assertThat(context).hasSingleBean(ReadLearnerMemorySectionAgentTool.class);
          assertThat(context).hasSingleBean(GetLearnerMemoryEvidenceAgentTool.class);
          assertThat(context).hasSingleBean(ToolResultReadGuard.class);
        });
  }

  @Test
  void keepsLearnerMemoryRecallAndItsToolsDisabledByDefault() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AgentConversationApiAutoConfiguration.class))
        .withUserConfiguration(LearnerMemoryUpdateDependencies.class)
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(LearnerMemoryRecallService.class);
          assertThat(context).doesNotHaveBean(SearchLearnerMemoryAgentTool.class);
          assertThat(context).doesNotHaveBean(ReadLearnerMemorySectionAgentTool.class);
          assertThat(context).doesNotHaveBean(GetLearnerMemoryEvidenceAgentTool.class);
        });
  }

  @Test
  void createsLearnerMemoryRecallServiceWithoutTagCatalogUsingAnEmptyTrustedCatalog() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AgentConversationApiAutoConfiguration.class))
        .withUserConfiguration(LearnerMemoryUpdateDependencies.class)
        .withPropertyValues("algo-mentor.learner-memory.recall.practice-chat.enabled=true")
        .run(context -> {
          assertThat(context).hasNotFailed();
          assertThat(context).doesNotHaveBean(TrustedProblemTagCatalog.class);
          assertThat(context).hasSingleBean(LearnerMemoryRecallService.class);
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

  @Test
  void practiceReviewMetricsUsesOpsRecorderWithRealAutoConfigurationOrder() {
    SimpleMeterRegistry registry = new SimpleMeterRegistry();
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(
            AgentConversationApiAutoConfiguration.class,
            OpsObservabilityAutoConfiguration.class))
        .withUserConfiguration(PracticeSessionServiceDependencies.class)
        .withBean(MeterRegistry.class, () -> registry)
        .run(context -> {
          assertThat(context).hasSingleBean(PracticeCodeReviewMetrics.class);

          context.getBean(PracticeCodeReviewMetrics.class)
              .recordReview(PracticeCodeReviewMetricStatus.COMPLETED);

          assertThat(registry.get("algo.mentor.practice.code_reviews")
              .tag("status", "completed")
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
    AgentRuntime agentRuntime() {
      return mock(AgentRuntime.class);
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

    @Bean
    PracticeChatAgentDefinition practiceChatAgentDefinition(
        org.congcong.algomentor.mentor.application.conversation.AgentConversationService conversationService,
        AgentRunLockManager lockManager,
        LocalAgentRunLockOwnerProvider lockOwnerProvider
    ) {
      return new PracticeChatAgentDefinition(
          new PracticeChatRunAdapter(conversationService, lockManager, lockOwnerProvider),
          List.of(TEST_PRACTICE_TOOL_NAME));
    }

    @Bean
    AgentTool testPracticeAgentTool() {
      return new AgentTool() {
        private final LlmToolSpec spec = new LlmToolSpec(
            TEST_PRACTICE_TOOL_NAME,
            "test practice tool",
            JsonNodeFactory.instance.objectNode(),
            true);

        @Override
        public LlmToolSpec spec() {
          return spec;
        }

        @Override
        public JsonNode execute(JsonNode arguments, AgentExecutionContext context) {
          throw new UnsupportedOperationException("test tool is not executed");
        }
      };
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

    @Bean
    TrustedProblemTagCatalog trustedProblemTagCatalog() {
      return TrustedProblemTagCatalog.empty();
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

  @Configuration(proxyBeanMethods = false)
  static class PersistentQueueStorageDependencies {

    @Bean
    SqlSessionTemplate sqlSessionTemplate() {
      SqlSessionTemplate template = mock(SqlSessionTemplate.class);
      when(template.getMapper(QueueMessageMapper.class)).thenReturn(mock(QueueMessageMapper.class));
      return template;
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class LearnerMemoryUpdateDependencies {

    @Bean
    LearnerMemoryClaimRepository learnerMemoryClaimRepository() {
      return mock(LearnerMemoryClaimRepository.class);
    }

    @Bean
    LearnerMemoryEvidenceRepository learnerMemoryEvidenceRepository() {
      return mock(LearnerMemoryEvidenceRepository.class);
    }

    @Bean
    LearnerMemoryUpdateRunRepository learnerMemoryUpdateRunRepository() {
      return mock(LearnerMemoryUpdateRunRepository.class);
    }

    @Bean
    TransactionTemplate learnerMemoryTransactionTemplate() {
      return new TransactionTemplate(mock(PlatformTransactionManager.class));
    }

    @Bean
    LearnerMemoryClaimQueryService learnerMemoryClaimQueryService(
        LearnerMemoryClaimRepository claimRepository
    ) {
      return new LearnerMemoryClaimQueryService(claimRepository, new LearnerMemoryClaimSnapshotFactory());
    }

    @Bean
    LearnerMemoryUpdateRunLifecycleService learnerMemoryUpdateRunLifecycleService(
        LearnerMemoryUpdateRunRepository updateRunRepository,
        TransactionTemplate learnerMemoryTransactionTemplate
    ) {
      return new LearnerMemoryUpdateRunLifecycleService(updateRunRepository, learnerMemoryTransactionTemplate);
    }

    @Bean
    LearnerMemoryAtomicApplyService learnerMemoryAtomicApplyService(
        LearnerMemoryClaimRepository claimRepository,
        LearnerMemoryEvidenceRepository evidenceRepository,
        LearnerMemoryUpdateRunRepository updateRunRepository,
        LearnerMemoryUpdateRunLifecycleService runLifecycleService,
        TransactionTemplate learnerMemoryTransactionTemplate
    ) {
      return new LearnerMemoryAtomicApplyService(
          claimRepository,
          evidenceRepository,
          updateRunRepository,
          new LearnerMemoryClaimTextHasher(),
          new LearnerMemoryClaimSnapshotFactory(),
          new LearnerMemoryEvidenceValidator(),
          new LearnerMemoryEvidenceGradeCalculator(),
          runLifecycleService,
          learnerMemoryTransactionTemplate);
    }

    @Bean
    AgentTurnMessageLookupRepository learnerMemoryTurnMessageLookupRepository() {
      return mock(AgentTurnMessageLookupRepository.class);
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
