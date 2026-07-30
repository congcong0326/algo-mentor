package org.congcong.algomentor.mentor.application.profile.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentOutput;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocationMode;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
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
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;
import org.junit.jupiter.api.Test;

class DeclaredProfileUpdateServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void appliesAllDimensionsFromOneChildRuntimeDecision() throws Exception {
    RecordingQueryService queryService = new RecordingQueryService();
    RecordingUpdateService updateService = new RecordingUpdateService(List.of(
        List.of(apply(ProfileUpdateApplyStatus.APPLIED), apply(ProfileUpdateApplyStatus.NO_CHANGE))));
    RecordingRuntime runtime = new RecordingRuntime(List.of(
        result(801L, decisions("REPLACE", "Backend role", "NO_CHANGE", ""))));

    DeclaredProfileUpdateResult result = service(queryService, updateService, runtime, 1).update(
        17L, request(), 401L, 3);

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.UPDATED);
    assertThat(updateService.calls).hasSize(1);
    assertThat(updateService.calls.get(0)).extracting(command -> command.identity().dimension())
        .containsExactly(LearnerProfileDimension.GOALS_AND_INTENTS, LearnerProfileDimension.SELF_ABILITY_ASSESSMENT);
    assertThat(updateService.calls.get(0)).extracting(ProfileUpdateCommand::originType)
        .containsExactly(
            org.congcong.algomentor.mentor.application.profile.LearnerProfileOriginType.USER_EXPLICIT,
            org.congcong.algomentor.mentor.application.profile.LearnerProfileOriginType.USER_CORRECTION);
    assertThat(updateService.calls.get(0).get(0).modelProvider()).isEqualTo("test-provider");
    assertThat(updateService.calls.get(0).get(0).modelName()).isEqualTo("test-model");
    assertThat(runtime.invocations).hasSize(1);
    AgentInvocation<?> invocation = runtime.invocations.get(0);
    assertThat(invocation.agentKey()).isEqualTo(DeclaredProfileUpdateAgentDefinition.KEY);
    assertThat(invocation.context().mode()).isEqualTo(AgentInvocationMode.CHILD);
    assertThat(invocation.context().parentRunId()).isEqualTo("401");
    assertThat(invocation.context().parentStepIndex()).isEqualTo(3);
    assertThat(invocation.context().idempotencyKey()).doesNotContain(request().updates().get(0).statement());
  }

  @Test
  void retriesTheWholeBatchInTheSameChildTurnAfterStale() throws Exception {
    RecordingQueryService queryService = new RecordingQueryService();
    RecordingUpdateService updateService = new RecordingUpdateService(List.of(
        List.of(apply(ProfileUpdateApplyStatus.STALE), apply(ProfileUpdateApplyStatus.STALE)),
        List.of(apply(ProfileUpdateApplyStatus.NO_CHANGE), apply(ProfileUpdateApplyStatus.NO_CHANGE))));
    RecordingRuntime runtime = new RecordingRuntime(List.of(
        result(801L, decisions("NO_CHANGE", "", "NO_CHANGE", "")),
        result(802L, decisions("NO_CHANGE", "", "NO_CHANGE", ""))));

    DeclaredProfileUpdateResult result = service(queryService, updateService, runtime, 1).update(
        17L, request(), 401L, 3);

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.NO_CHANGE);
    assertThat(updateService.calls).hasSize(2);
    assertThat(queryService.calls).isEqualTo(4);
    assertThat(runtime.invocations).hasSize(2);
    AgentInvocation<?> first = runtime.invocations.get(0);
    AgentInvocation<?> retry = runtime.invocations.get(1);
    assertThat(retry.context().parentRunId()).isEqualTo(first.context().parentRunId());
    assertThat(retry.context().parentStepIndex()).isEqualTo(first.context().parentStepIndex());
    assertThat(retry.context().idempotencyKey()).isEqualTo(
        first.context().idempotencyKey() + LearnerDeclaredProfileToolContracts.CHILD_RETRY_IDEMPOTENCY_KEY_SEPARATOR + "1");
    assertThat(((DeclaredProfileUpdateAgentInput) retry.input()).retryOfRunId()).isEqualTo(801L);
  }

  @Test
  void returnsFailedWithoutWritingWhenStructuredOutputIsInvalid() throws Exception {
    RecordingQueryService queryService = new RecordingQueryService();
    RecordingUpdateService updateService = new RecordingUpdateService(List.of());
    RecordingRuntime runtime = new RecordingRuntime(List.of(
        result(801L, objectMapper.readTree("{\"decisions\":[]}"))));

    DeclaredProfileUpdateResult result = service(queryService, updateService, runtime, 1).update(
        17L, request(), 401L, 3);

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.FAILED);
    assertThat(result.items()).allSatisfy(item -> assertThat(item.status())
        .isEqualTo(DeclaredProfileUpdateResult.ItemStatus.FAILED));
    assertThat(updateService.calls).isEmpty();
  }

  @Test
  void returnsFailedWithoutWritingWhenChildRuntimeFails() {
    RecordingQueryService queryService = new RecordingQueryService();
    RecordingUpdateService updateService = new RecordingUpdateService(List.of());
    RecordingRuntime runtime = new RecordingRuntime(List.of());
    runtime.failure = new IllegalStateException("route unavailable");

    DeclaredProfileUpdateResult result = service(queryService, updateService, runtime, 1).update(
        17L, request(), 401L, 3);

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.FAILED);
    assertThat(updateService.calls).isEmpty();
  }

  private DeclaredProfileUpdateService service(
      LearnerProfileQueryService queryService,
      LearnerProfileUpdateService updateService,
      AgentRuntime runtime,
      int maxStaleRetries
  ) {
    return new DeclaredProfileUpdateService(
        queryService, updateService, runtime, new DeclaredProfileUpdatePromptBuilder(), maxStaleRetries, 300);
  }

  private DeclaredProfileUpdateRequest request() {
    return new DeclaredProfileUpdateRequest(List.of(
        new DeclaredProfileUpdateRequest.Item(
            LearnerProfileDimension.GOALS_AND_INTENTS, "Prepare Java interview", DeclaredProfileUpdateIntent.DECLARE),
        new DeclaredProfileUpdateRequest.Item(
            LearnerProfileDimension.SELF_ABILITY_ASSESSMENT, "Not a beginner", DeclaredProfileUpdateIntent.CORRECT)));
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

  private AgentRunResult result(long runDbId, JsonNode output) {
    return new AgentRunResult(
        1,
        LlmFinishReason.STOP,
        new AgentOutput("", output, DeclaredProfileUpdateJsonSchema.SCHEMA_NAME,
            LearnerDeclaredProfileToolContracts.SCHEMA_VERSION, Map.of()),
        Map.of(
            AgentRuntimeMetadataKeys.RUN_DB_ID, runDbId,
            AgentRuntimeMetadataKeys.RUNTIME_PROVIDER, "test-provider",
            AgentRuntimeMetadataKeys.RUNTIME_MODEL, "test-model"));
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

  private static final class RecordingRuntime implements AgentRuntime {
    private final List<AgentRunResult> results;
    private final List<AgentInvocation<?>> invocations = new ArrayList<>();
    private RuntimeException failure;

    private RecordingRuntime(List<AgentRunResult> results) {
      this.results = List.copyOf(results);
    }

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      invocations.add(invocation);
      if (failure != null) {
        throw failure;
      }
      return results.get(invocations.size() - 1);
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException("stream not used");
    }
  }
}
