package org.congcong.algomentor.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.congcong.algomentor.agent.core.AgentLoopDefaults;
import org.congcong.algomentor.agent.core.runtime.definition.AgentDefinition;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.AgentToolRegistry;
import org.congcong.algomentor.agent.core.tool.ReadToolResultTool;
import org.congcong.algomentor.agent.runtime.DefaultAgentRuntime;
import org.congcong.algomentor.agent.runtime.definition.AgentDefinitionRegistry;
import org.congcong.algomentor.ai.governance.model.AiBusinessScenario;
import org.congcong.algomentor.api.config.MentorConfigurationKeys;
import org.congcong.algomentor.identity.controller.AdminUserController;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanDraftRevisionAgentInput;
import org.congcong.algomentor.mentor.application.learningplan.proposal.stream.LearningPlanExtensionAgentInput;
import org.congcong.algomentor.mentor.application.learningplan.proposal.revision.LearningPlanRevisionToolContracts;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanAgentToolNames;
import org.congcong.algomentor.mentor.application.learningplan.stream.LearningPlanDraftAgentInput;
import org.congcong.algomentor.mentor.application.practice.AppendCurrentProblemNoteAgentToolContracts;
import org.congcong.algomentor.mentor.application.practice.PracticeChatAgentInput;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentInput;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentTool;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewAgentToolNames;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewService;
import org.congcong.algomentor.mentor.application.practice.PracticeLearningStateAgentToolContracts;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateAgentInput;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewUpdateAgentInput;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryAgentToolContracts;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryRecallToolContracts;
import org.congcong.algomentor.queue.publisher.QueuePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost/algo_mentor_test",
    MentorConfigurationKeys.AGENT_RUNTIME_ENABLED + "=true",
    "algo-mentor.practice.code-review.enabled=true",
    "algo-mentor.learner-memory.declared-update.enabled=true",
    "algo-mentor.learner-memory.code-review-consumer.enabled=true",
    "algo-mentor.learner-memory.recall.practice-chat.enabled=true"
})
class MentorApiApplicationTest {

  private static final Map<AiBusinessScenario, DefinitionExpectation> DEFINITION_EXPECTATIONS = Map.of(
      AiBusinessScenario.PRACTICE_CHAT,
      new DefinitionExpectation(
          PracticeChatAgentInput.class,
          8,
          List.of(
              PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW,
              LearnerDeclaredProfileToolContracts.TOOL_NAME,
              LearnerMemoryRecallToolContracts.SEARCH_LEARNER_MEMORY,
              LearnerMemoryRecallToolContracts.READ_LEARNER_MEMORY_SECTION,
              LearnerMemoryRecallToolContracts.GET_LEARNER_MEMORY_EVIDENCE,
              PracticeLearningStateAgentToolContracts.TOOL_NAME,
              AppendCurrentProblemNoteAgentToolContracts.TOOL_NAME,
              LearnerMemoryAgentToolContracts.GET_PROBLEM_REVIEW_TRAJECTORY,
              ReadToolResultTool.NAME)),
      AiBusinessScenario.LEARNING_PLAN_DRAFT,
      new DefinitionExpectation(LearningPlanDraftAgentInput.class, 24, LearningPlanAgentToolNames.PLANNING_TOOLS),
      AiBusinessScenario.LEARNING_PLAN_REVISION,
      new DefinitionExpectation(
          LearningPlanDraftRevisionAgentInput.class,
          24,
          LearningPlanRevisionToolContracts.AGENT_TOOLS),
      AiBusinessScenario.LEARNING_PLAN_EXTENSION,
      new DefinitionExpectation(LearningPlanExtensionAgentInput.class, 24, LearningPlanAgentToolNames.PLANNING_TOOLS),
      AiBusinessScenario.PRACTICE_CODE_REVIEW,
      new DefinitionExpectation(PracticeCodeReviewAgentInput.class, 1, List.of()),
      AiBusinessScenario.LEARNER_DECLARED_PROFILE_UPDATE,
      new DefinitionExpectation(DeclaredProfileUpdateAgentInput.class, 1, List.of()),
      AiBusinessScenario.CODE_REVIEW_PROFILE_UPDATE,
      new DefinitionExpectation(
          LearnerMemoryCodeReviewUpdateAgentInput.class,
          4,
          List.of(
              LearnerMemoryAgentToolContracts.GET_PROBLEM_REVIEW_TRAJECTORY,
              LearnerMemoryAgentToolContracts.GET_CODE_REVIEW_EVIDENCE,
              LearnerMemoryAgentToolContracts.COMPARE_SUBMISSION_VERSIONS)));

  @Autowired
  private ClientRegistrationRepository clientRegistrationRepository;

  @Autowired
  private AdminUserController adminUserController;

  @Autowired
  private QueuePublisher queuePublisher;

  @Autowired
  private PracticeCodeReviewCommitService practiceCodeReviewCommitService;

  @Autowired
  private PracticeCodeReviewService practiceCodeReviewService;

  @Autowired
  private PracticeCodeReviewAgentTool practiceCodeReviewAgentTool;

  @Autowired
  private AgentToolRegistry agentToolRegistry;

  @Autowired
  private AgentDefinitionRegistry agentDefinitionRegistry;

  @Autowired
  private AgentRuntime agentRuntime;

  @Test
  void contextLoads() {
    assertThat(agentRuntime).isInstanceOf(DefaultAgentRuntime.class);
  }

  @Test
  void applicationContextLoadsAdminUserControllerFromIdentity() {
    assertThat(adminUserController).isNotNull();
  }

  @Test
  void applicationContextLoadsCompletePracticeCodeReviewCapability() {
    assertThat(queuePublisher).isNotNull();
    assertThat(practiceCodeReviewCommitService).isNotNull();
    assertThat(practiceCodeReviewService).isNotNull();
    assertThat(practiceCodeReviewAgentTool).isNotNull();
    assertThat(agentToolRegistry.find(PracticeCodeReviewAgentToolNames.SUBMIT_PRACTICE_CODE_REVIEW)).isPresent();
  }

  @Test
  void applicationContextRegistersAllRuntimeDefinitionsWithApprovedExecutionBounds() {
    Map<String, AgentDefinition<?>> definitionsByKey = agentDefinitionRegistry.definitions().stream()
        .collect(java.util.stream.Collectors.toUnmodifiableMap(
            definition -> definition.key().value(),
            definition -> definition));

    assertThat(definitionsByKey).hasSize(AiBusinessScenario.values().length);
    assertThat(definitionsByKey.keySet()).containsExactlyInAnyOrder(
        java.util.Arrays.stream(AiBusinessScenario.values()).map(AiBusinessScenario::code).toArray(String[]::new));

    DEFINITION_EXPECTATIONS.forEach((scenario, expectation) -> {
      AgentDefinition<?> definition = definitionsByKey.get(scenario.code());
      assertThat(definition).as(scenario.name()).isNotNull();
      assertThat(definition.key().value()).isEqualTo(scenario.code());
      assertThat(definition.key().inputType()).isEqualTo(expectation.inputType());
      assertThat(definition.loopPolicy().maxSteps()).isEqualTo(expectation.maxSteps());
      assertThat(definition.loopPolicy().maxSteps()).isLessThanOrEqualTo(AgentLoopDefaults.DEFAULT_MAX_STEPS);
      assertThat(definition.allowedToolNames()).containsExactlyElementsOf(expectation.allowedToolNames());
      definition.allowedToolNames().forEach(toolName ->
          assertThat(agentToolRegistry.find(toolName)).as(scenario.name() + "/" + toolName).isPresent());
    });

    assertOneShot(AiBusinessScenario.PRACTICE_CODE_REVIEW, definitionsByKey);
    assertOneShot(AiBusinessScenario.LEARNER_DECLARED_PROFILE_UPDATE, definitionsByKey);
  }

  @Test
  void googleOidcProviderHasJwkSetUri() {
    ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");

    assertThat(google).isNotNull();
    assertThat(google.getProviderDetails().getTokenUri())
        .isEqualTo("https://oauth2.googleapis.com/token");
    assertThat(google.getProviderDetails().getJwkSetUri())
        .isEqualTo("https://www.googleapis.com/oauth2/v3/certs");
  }

  private static void assertOneShot(
      AiBusinessScenario scenario,
      Map<String, AgentDefinition<?>> definitionsByKey
  ) {
    AgentDefinition<?> definition = definitionsByKey.get(scenario.code());
    assertThat(definition.allowedToolNames()).isEmpty();
    assertThat(definition.loopPolicy().maxSteps()).isEqualTo(1);
  }

  private record DefinitionExpectation(Class<?> inputType, int maxSteps, List<String> allowedToolNames) {
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    DataSource dataSource() throws SQLException {
      DataSource dataSource = mock(DataSource.class);
      Connection connection = mock(Connection.class);
      DatabaseMetaData metaData = mock(DatabaseMetaData.class);
      when(dataSource.getConnection()).thenReturn(connection);
      when(connection.getMetaData()).thenReturn(metaData);
      when(metaData.getDatabaseProductName()).thenReturn("PostgreSQL");
      return dataSource;
    }
  }
}
