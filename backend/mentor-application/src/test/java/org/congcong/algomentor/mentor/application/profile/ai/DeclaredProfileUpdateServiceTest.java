package org.congcong.algomentor.mentor.application.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.ai.governance.completion.AiCompletionContext;
import org.congcong.algomentor.ai.governance.completion.AiCompletionGateway;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileSnapshot;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateApplyResult;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateApplyStatus;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateCommand;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateRequest;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateResult;
import org.junit.jupiter.api.Test;

class DeclaredProfileUpdateServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void appliesAllDimensionsFromOneGovernedModelDecision() throws Exception {
    RecordingQueryService queryService = new RecordingQueryService();
    RecordingUpdateService updateService = new RecordingUpdateService(List.of(
        List.of(apply(ProfileUpdateApplyStatus.APPLIED), apply(ProfileUpdateApplyStatus.NO_CHANGE))));
    RecordingGateway gateway = new RecordingGateway(List.of(
        completion(decisions("REPLACE", "Java 后端转算法面试", "NO_CHANGE", ""))));

    DeclaredProfileUpdateResult result = service(queryService, updateService, gateway, 1).update(
        17L,
        request(),
        context());

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.UPDATED);
    assertThat(result.items()).extracting(DeclaredProfileUpdateResult.Item::status)
        .containsExactly(DeclaredProfileUpdateResult.ItemStatus.APPLIED, DeclaredProfileUpdateResult.ItemStatus.NO_CHANGE);
    assertThat(updateService.calls).hasSize(1);
    assertThat(updateService.calls.get(0)).extracting(command -> command.identity().dimension())
        .containsExactly(LearnerProfileDimension.GOALS_AND_INTENTS, LearnerProfileDimension.SELF_ABILITY_ASSESSMENT);
    assertThat(updateService.calls.get(0)).extracting(ProfileUpdateCommand::originType)
        .containsExactly(
            org.congcong.algomentor.mentor.application.profile.LearnerProfileOriginType.USER_EXPLICIT,
            org.congcong.algomentor.mentor.application.profile.LearnerProfileOriginType.USER_CORRECTION);
    assertThat(updateService.calls.get(0).get(0).modelProvider()).isEqualTo("test-provider");
    assertThat(updateService.calls.get(0).get(0).modelName()).isEqualTo("test-model");
    assertThat(gateway.requests).hasSize(1);
    assertThat(gateway.contexts).containsExactly(context());
  }

  @Test
  void retriesTheWholeBatchOnceAfterStaleAndNeverPartiallyReportsSuccess() throws Exception {
    RecordingQueryService queryService = new RecordingQueryService();
    RecordingUpdateService updateService = new RecordingUpdateService(List.of(
        List.of(apply(ProfileUpdateApplyStatus.STALE), apply(ProfileUpdateApplyStatus.STALE)),
        List.of(apply(ProfileUpdateApplyStatus.NO_CHANGE), apply(ProfileUpdateApplyStatus.NO_CHANGE))));
    RecordingGateway gateway = new RecordingGateway(List.of(
        completion(decisions("NO_CHANGE", "", "NO_CHANGE", "")),
        completion(decisions("NO_CHANGE", "", "NO_CHANGE", ""))));

    DeclaredProfileUpdateResult result = service(queryService, updateService, gateway, 1).update(
        17L,
        request(),
        context());

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.NO_CHANGE);
    assertThat(gateway.requests).hasSize(2);
    assertThat(updateService.calls).hasSize(2);
    assertThat(queryService.calls).isEqualTo(4);
  }

  @Test
  void returnsFailedWithoutWritingWhenStructuredOutputIsInvalid() throws Exception {
    RecordingQueryService queryService = new RecordingQueryService();
    RecordingUpdateService updateService = new RecordingUpdateService(List.of());
    RecordingGateway gateway = new RecordingGateway(List.of(
        completion(objectMapper.readTree("{\"decisions\":[]}"))));

    DeclaredProfileUpdateResult result = service(queryService, updateService, gateway, 1).update(
        17L,
        request(),
        context());

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.FAILED);
    assertThat(result.items()).allSatisfy(item -> assertThat(item.status())
        .isEqualTo(DeclaredProfileUpdateResult.ItemStatus.FAILED));
    assertThat(updateService.calls).isEmpty();
  }

  private DeclaredProfileUpdateService service(
      LearnerProfileQueryService queryService,
      LearnerProfileUpdateService updateService,
      AiCompletionGateway gateway,
      int maxStaleRetries
  ) {
    return new DeclaredProfileUpdateService(
        queryService, updateService, gateway, new DeclaredProfileUpdatePromptBuilder(), maxStaleRetries, 300);
  }

  private DeclaredProfileUpdateRequest request() {
    return new DeclaredProfileUpdateRequest(List.of(
        new DeclaredProfileUpdateRequest.Item(
            LearnerProfileDimension.GOALS_AND_INTENTS, "我要准备 Java 算法面试", DeclaredProfileUpdateIntent.DECLARE),
        new DeclaredProfileUpdateRequest.Item(
            LearnerProfileDimension.SELF_ABILITY_ASSESSMENT, "我不是算法初学者", DeclaredProfileUpdateIntent.CORRECT)));
  }

  private AiCompletionContext context() {
    return AiCompletionContext.parentRun(
        17L, "run-17", AiPurpose.LEARNING_CHAT, AiRunSource.LEARNER_PROFILE_DECLARED_UPDATE, 3);
  }

  private JsonNode decisions(String firstAction, String firstContent, String secondAction, String secondContent)
      throws Exception {
    return objectMapper.readTree("""
        {"decisions":[
          {"dimension":"GOALS_AND_INTENTS","action":"%s","content":"%s"},
          {"dimension":"SELF_ABILITY_ASSESSMENT","action":"%s","content":"%s"}
        ]}
        """.formatted(firstAction, firstContent, secondAction, secondContent));
  }

  private LlmCompletionResult completion(JsonNode output) {
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

  private ProfileUpdateApplyResult apply(ProfileUpdateApplyStatus status) {
    return new ProfileUpdateApplyResult(status, Optional.empty(), "token");
  }

  private static final class RecordingQueryService extends LearnerProfileQueryService {
    private int calls;

    private RecordingQueryService() {
      super(null);
    }

    @Override
    public LearnerProfileSnapshot snapshot(org.congcong.algomentor.mentor.application.profile.LearnerProfileIdentity identity) {
      calls++;
      return LearnerProfileSnapshot.from(identity, null);
    }
  }

  private static final class RecordingUpdateService extends LearnerProfileUpdateService {
    private final List<List<ProfileUpdateApplyResult>> results;
    private final List<List<ProfileUpdateCommand>> calls = new ArrayList<>();

    private RecordingUpdateService(List<List<ProfileUpdateApplyResult>> results) {
      super(null, null, null);
      this.results = List.copyOf(results);
    }

    @Override
    public List<ProfileUpdateApplyResult> applyBatch(List<ProfileUpdateCommand> commands) {
      calls.add(List.copyOf(commands));
      return results.get(calls.size() - 1);
    }
  }

  private static final class RecordingGateway implements AiCompletionGateway {
    private final List<LlmCompletionResult> results;
    private final List<LlmCompletionRequest> requests = new ArrayList<>();
    private final List<AiCompletionContext> contexts = new ArrayList<>();

    private RecordingGateway(List<LlmCompletionResult> results) {
      this.results = List.copyOf(results);
    }

    @Override
    public boolean isAllowed(AiCompletionContext context) {
      return true;
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request, AiCompletionContext context) {
      requests.add(request);
      contexts.add(context);
      return results.get(requests.size() - 1);
    }
  }
}
