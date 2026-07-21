package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.ai.governance.completion.AiCompletionContext;
import org.congcong.algomentor.ai.governance.completion.AiCompletionGateway;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.api.profile.mapper.LearnerProfileMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerProfileRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileContentPolicy;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdatePromptBuilder;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateRequest;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateResult;
import org.junit.jupiter.api.Test;

class DeclaredProfileUpdateIT extends PostgresIntegrationTestSupport {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void commitsMultipleDeclaredDimensionsTogetherOnPostgresql() throws Exception {
    migrateLatest();
    long userId = insertUser();

    DeclaredProfileUpdateResult result = service(decisions("Java 后端求职", "每天可以学习两小时")).update(
        userId,
        request(),
        context(userId));

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.UPDATED);
    assertThat(count("learner_profile_entry")).isEqualTo(2L);
    assertThat(queryLong("SELECT COUNT(*) FROM learner_profile_entry WHERE status = 'ACTIVE'")).isEqualTo(2L);
  }

  @Test
  void rollsBackTheWholeBatchWhenTheSecondReplacementViolatesContentPolicy() throws Exception {
    migrateLatest();
    long userId = insertUser();

    DeclaredProfileUpdateResult result = service(decisions("Java 后端求职", "Authorization: tokenabcdefgh")).update(
        userId,
        request(),
        context(userId));

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.FAILED);
    assertThat(count("learner_profile_entry")).isZero();
  }

  private DeclaredProfileUpdateService service(JsonNode output) throws Exception {
    MyBatisLearnerProfileRepository repository = new MyBatisLearnerProfileRepository(
        sqlSessionTemplate("mapper/profile/LearnerProfileMapper.xml").getMapper(LearnerProfileMapper.class));
    return new DeclaredProfileUpdateService(
        new LearnerProfileQueryService(repository),
        new LearnerProfileUpdateService(repository, new LearnerProfileContentPolicy(200), transactionTemplate()),
        new FixedCompletionGateway(output),
        new DeclaredProfileUpdatePromptBuilder(),
        1,
        300);
  }

  private DeclaredProfileUpdateRequest request() {
    return new DeclaredProfileUpdateRequest(List.of(
        new DeclaredProfileUpdateRequest.Item(
            LearnerProfileDimension.GOALS_AND_INTENTS, "我要找 Java 后端工作", DeclaredProfileUpdateIntent.DECLARE),
        new DeclaredProfileUpdateRequest.Item(
            LearnerProfileDimension.TIME_AND_RESOURCE_CONSTRAINTS, "每天学习两小时", DeclaredProfileUpdateIntent.DECLARE)));
  }

  private AiCompletionContext context(long userId) {
    return AiCompletionContext.parentRun(
        userId, "declared-profile-it", AiPurpose.LEARNING_CHAT, AiRunSource.LEARNER_PROFILE_DECLARED_UPDATE, 1);
  }

  private JsonNode decisions(String goals, String timeConstraint) throws Exception {
    return objectMapper.readTree("""
        {"decisions":[
          {"dimension":"GOALS_AND_INTENTS","action":"REPLACE","content":"%s"},
          {"dimension":"TIME_AND_RESOURCE_CONSTRAINTS","action":"REPLACE","content":"%s"}
        ]}
        """.formatted(goals, timeConstraint));
  }

  private static final class FixedCompletionGateway implements AiCompletionGateway {
    private final JsonNode output;

    private FixedCompletionGateway(JsonNode output) {
      this.output = output;
    }

    @Override
    public boolean isAllowed(AiCompletionContext context) {
      return true;
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request, AiCompletionContext context) {
      return new LlmCompletionResult(
          LlmMessage.assistant("{}"),
          List.of(),
          output,
          LlmFinishReason.STOP,
          LlmUsage.empty(),
          LlmProviderId.of("test-provider"),
          LlmModelId.of("test-model"),
          Map.of());
    }
  }
}
