package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentOutput;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.api.profile.mapper.LearnerProfileMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerProfileRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileContentPolicy;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateJsonSchema;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdatePromptBuilder;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateRequest;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateResult;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;
import org.junit.jupiter.api.Test;

class DeclaredProfileUpdateIT extends PostgresIntegrationTestSupport {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void commitsMultipleDeclaredDimensionsTogetherOnPostgresql() throws Exception {
    migrateLatest();
    long userId = insertUser();

    DeclaredProfileUpdateResult result = service(decisions("Backend role", "Two hours daily")).update(
        userId, request(), 401L, 1);

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.UPDATED);
    assertThat(count("learner_profile_entry")).isEqualTo(2L);
    assertThat(queryLong("SELECT COUNT(*) FROM learner_profile_entry WHERE status = 'ACTIVE'")).isEqualTo(2L);
  }

  @Test
  void rollsBackTheWholeBatchWhenTheSecondReplacementViolatesContentPolicy() throws Exception {
    migrateLatest();
    long userId = insertUser();

    DeclaredProfileUpdateResult result = service(decisions("Backend role", "Authorization: tokenabcdefgh")).update(
        userId, request(), 401L, 1);

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.FAILED);
    assertThat(count("learner_profile_entry")).isZero();
  }

  private DeclaredProfileUpdateService service(JsonNode output) throws Exception {
    MyBatisLearnerProfileRepository repository = new MyBatisLearnerProfileRepository(
        sqlSessionTemplate("mapper/profile/LearnerProfileMapper.xml").getMapper(LearnerProfileMapper.class));
    return new DeclaredProfileUpdateService(
        new LearnerProfileQueryService(repository),
        new LearnerProfileUpdateService(repository, new LearnerProfileContentPolicy(200), transactionTemplate()),
        new FixedRuntime(output),
        new DeclaredProfileUpdatePromptBuilder(),
        1,
        300);
  }

  private DeclaredProfileUpdateRequest request() {
    return new DeclaredProfileUpdateRequest(List.of(
        new DeclaredProfileUpdateRequest.Item(
            LearnerProfileDimension.GOALS_AND_INTENTS, "Find backend work", DeclaredProfileUpdateIntent.DECLARE),
        new DeclaredProfileUpdateRequest.Item(
            LearnerProfileDimension.TIME_AND_RESOURCE_CONSTRAINTS, "Study daily", DeclaredProfileUpdateIntent.DECLARE)));
  }

  private JsonNode decisions(String goals, String timeConstraint) throws Exception {
    return objectMapper.readTree("""
        {"decisions":[
          {"dimension":"GOALS_AND_INTENTS","action":"REPLACE","content":"%s"},
          {"dimension":"TIME_AND_RESOURCE_CONSTRAINTS","action":"REPLACE","content":"%s"}
        ]}
        """.formatted(goals, timeConstraint));
  }

  private static final class FixedRuntime implements AgentRuntime {
    private final JsonNode output;

    private FixedRuntime(JsonNode output) {
      this.output = output;
    }

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      return new AgentRunResult(
          1,
          LlmFinishReason.STOP,
          new AgentOutput("", output, DeclaredProfileUpdateJsonSchema.SCHEMA_NAME,
              LearnerDeclaredProfileToolContracts.SCHEMA_VERSION, Map.of()),
          Map.of(
              AgentRuntimeMetadataKeys.RUN_DB_ID, 801L,
              AgentRuntimeMetadataKeys.RUNTIME_PROVIDER, "test-provider",
              AgentRuntimeMetadataKeys.RUNTIME_MODEL, "test-model"));
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException("stream not used");
    }
  }
}
