package org.congcong.algomentor.mentor.application.profile.review;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileSnapshot;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateApplyResult;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateApplyStatus;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateCommand;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateAction;
import org.junit.jupiter.api.Test;

class CodeReviewProfileUpdateServiceTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void appliesAllowedGeneralAndTagReplacementsWithBackgroundRuntime() throws Exception {
    RecordingFactRepository facts = new RecordingFactRepository(List.of(fact(1L, List.of(9L))));
    RecordingQueryService queryService = new RecordingQueryService();
    RecordingUpdateService updateService = new RecordingUpdateService(List.of(List.of(
        applied(ProfileUpdateApplyStatus.APPLIED),
        applied(ProfileUpdateApplyStatus.APPLIED),
        applied(ProfileUpdateApplyStatus.APPLIED))));
    RecordingRuntime runtime = new RecordingRuntime(List.of(result(801L, output(
        "REPLACE", "优先构造解题计划", "REPLACE", "补充边界检查", 9L, "REPLACE", "数组处理稳定"))));

    CodeReviewProfileUpdateResult result = service(facts, queryService, updateService, runtime, 1)
        .update(7L, List.of(fact(1L, List.of(9L))));

    assertThat(result.status()).isEqualTo(CodeReviewProfileUpdateResult.Status.UPDATED);
    assertThat(result.windowProblemCount()).isEqualTo(1);
    assertThat(result.appliedCount()).isEqualTo(3);
    assertThat(updateService.calls).hasSize(1);
    assertThat(updateService.calls.get(0)).extracting(ProfileUpdateCommand::originType)
        .allMatch(origin -> origin == org.congcong.algomentor.mentor.application.profile.LearnerProfileOriginType.SYSTEM_DERIVED);
    assertThat(runtime.invocations).singleElement().satisfies(invocation -> {
      assertThat(invocation.agentKey()).isEqualTo(CodeReviewProfileUpdateAgentDefinition.KEY);
      assertThat(invocation.context().mode()).isEqualTo(AgentInvocationMode.BACKGROUND);
      assertThat(invocation.context().parentRunId()).isNull();
      assertThat(invocation.context().parentStepIndex()).isNull();
      assertThat(invocation.context().requestSize()).isEqualTo(1);
      assertThat(invocation.context().idempotencyKey()).doesNotContain("problem-");
    });
    assertThat(updateService.calls.get(0).get(0).modelProvider()).isEqualTo("test-provider");
    assertThat(updateService.calls.get(0).get(0).modelName()).isEqualTo("test-model");
  }

  @Test
  void keepsNoChangeAsZeroWriteBatch() throws Exception {
    RecordingFactRepository facts = new RecordingFactRepository(List.of(fact(1L, List.of())));
    RecordingUpdateService updateService = new RecordingUpdateService(List.of(List.of(
        applied(ProfileUpdateApplyStatus.NO_CHANGE), applied(ProfileUpdateApplyStatus.NO_CHANGE))));
    RecordingRuntime runtime = new RecordingRuntime(List.of(result(801L, output(
        "NO_CHANGE", "", "NO_CHANGE", "", null, null, null))));

    CodeReviewProfileUpdateResult result = service(facts, new RecordingQueryService(), updateService, runtime, 1)
        .update(7L, List.of(fact(1L, List.of())));

    assertThat(result.status()).isEqualTo(CodeReviewProfileUpdateResult.Status.NO_CHANGE);
    assertThat(result.appliedCount()).isZero();
    assertThat(updateService.calls).singleElement().satisfies(commands ->
        assertThat(commands).extracting(command -> command.decision().action())
            .containsOnly(ProfileUpdateAction.NO_CHANGE));
  }

  @Test
  void rejectsOneOutOfScopeDecisionWithoutAnyProfileWrite() throws Exception {
    RecordingFactRepository facts = new RecordingFactRepository(List.of(fact(1L, List.of(9L))));
    RecordingUpdateService updateService = new RecordingUpdateService(List.of());
    RecordingRuntime runtime = new RecordingRuntime(List.of(result(801L, output(
        "NO_CHANGE", "", "NO_CHANGE", "", 10L, "REPLACE", "not allowed"))));

    CodeReviewProfileUpdateResult result = service(facts, new RecordingQueryService(), updateService, runtime, 1)
        .update(7L, List.of(fact(1L, List.of(9L))));

    assertThat(result.status()).isEqualTo(CodeReviewProfileUpdateResult.Status.FAILED);
    assertThat(updateService.calls).isEmpty();
    assertThat(runtime.invocations).hasSize(1);
  }

  @Test
  void rebuildsSnapshotsAndModelDecisionOnceAfterStale() throws Exception {
    RecordingFactRepository facts = new RecordingFactRepository(List.of(fact(1L, List.of(9L))));
    RecordingQueryService queryService = new RecordingQueryService();
    RecordingUpdateService updateService = new RecordingUpdateService(List.of(
        List.of(applied(ProfileUpdateApplyStatus.STALE), applied(ProfileUpdateApplyStatus.STALE), applied(ProfileUpdateApplyStatus.STALE)),
        List.of(applied(ProfileUpdateApplyStatus.NO_CHANGE), applied(ProfileUpdateApplyStatus.NO_CHANGE), applied(ProfileUpdateApplyStatus.NO_CHANGE))));
    RecordingRuntime runtime = new RecordingRuntime(List.of(
        result(801L, output("NO_CHANGE", "", "NO_CHANGE", "", 9L, "NO_CHANGE", "")),
        result(802L, output("NO_CHANGE", "", "NO_CHANGE", "", 9L, "NO_CHANGE", ""))));

    CodeReviewProfileUpdateResult result = service(facts, queryService, updateService, runtime, 1)
        .update(7L, List.of(fact(1L, List.of(9L))));

    assertThat(result.status()).isEqualTo(CodeReviewProfileUpdateResult.Status.NO_CHANGE);
    assertThat(runtime.invocations).hasSize(2);
    assertThat(updateService.calls).hasSize(2);
    assertThat(queryService.calls).isEqualTo(6);
    assertThat(((CodeReviewProfileUpdateAgentInput) runtime.invocations.get(1).input()).retryOfRunId())
        .isEqualTo(801L);
    assertThat(runtime.invocations.get(1).context().idempotencyKey()).isEqualTo(
        runtime.invocations.get(0).context().idempotencyKey()
            + CodeReviewProfileConsumerConstants.BACKGROUND_RETRY_IDEMPOTENCY_KEY_SEPARATOR + "1");
  }

  @Test
  void stopsAfterSecondStaleOrRuntimeFailureWithoutQueueRetrySignal() throws Exception {
    RecordingFactRepository facts = new RecordingFactRepository(List.of(fact(1L, List.of())));
    RecordingUpdateService staleUpdateService = new RecordingUpdateService(List.of(
        List.of(applied(ProfileUpdateApplyStatus.STALE), applied(ProfileUpdateApplyStatus.STALE)),
        List.of(applied(ProfileUpdateApplyStatus.STALE), applied(ProfileUpdateApplyStatus.STALE))));
    RecordingRuntime staleRuntime = new RecordingRuntime(List.of(
        result(801L, output("NO_CHANGE", "", "NO_CHANGE", "", null, null, null)),
        result(802L, output("NO_CHANGE", "", "NO_CHANGE", "", null, null, null))));

    CodeReviewProfileUpdateResult stale = service(facts, new RecordingQueryService(), staleUpdateService, staleRuntime, 1)
        .update(7L, List.of(fact(1L, List.of())));
    RecordingRuntime deniedRuntime = new RecordingRuntime(List.of());
    deniedRuntime.failure = new IllegalStateException("route unavailable");
    CodeReviewProfileUpdateResult denied = service(facts, new RecordingQueryService(),
        new RecordingUpdateService(List.of()), deniedRuntime, 1)
        .update(7L, List.of(fact(1L, List.of())));

    assertThat(stale.status()).isEqualTo(CodeReviewProfileUpdateResult.Status.FAILED);
    assertThat(staleRuntime.invocations).hasSize(2);
    assertThat(denied.status()).isEqualTo(CodeReviewProfileUpdateResult.Status.FAILED);
  }

  private CodeReviewProfileUpdateService service(
      CodeReviewProfileFactRepository facts,
      LearnerProfileQueryService queryService,
      LearnerProfileUpdateService updateService,
      AgentRuntime runtime,
      int maxStaleRetries
  ) {
    return new CodeReviewProfileUpdateService(
        facts, queryService, updateService, runtime, new CodeReviewProfileStructuredOutputMapper(), maxStaleRetries);
  }

  private JsonNode output(
      String firstAction, String firstContent, String secondAction, String secondContent,
      Long tagId, String tagAction, String tagContent
  ) throws Exception {
    String tags = tagId == null ? "[]" : """
        [{"tagId":%d,"action":"%s","content":"%s","reason":"test"}]
        """.formatted(tagId, tagAction, tagContent);
    return objectMapper.readTree("""
        {"generalObservations":[
          {"dimension":"PROBLEM_SOLVING_APPROACH","action":"%s","content":"%s","reason":"test"},
          {"dimension":"IMPLEMENTATION_AND_ERROR_PATTERN","action":"%s","content":"%s","reason":"test"}
        ],"tagAssessments":%s}
        """.formatted(firstAction, firstContent, secondAction, secondContent, tags));
  }

  private static CodeReviewProfileFact fact(long reviewId, List<Long> tagIds) {
    BigDecimal one = BigDecimal.ONE;
    return new CodeReviewProfileFact(
        reviewId, "problem-" + reviewId, 1, one, one, one, one, one, one, false,
        List.of("deduction"), List.of("improvement"), tagIds, Instant.EPOCH);
  }

  private static AgentRunResult result(long runDbId, JsonNode output) {
    return new AgentRunResult(
        1,
        LlmFinishReason.STOP,
        new AgentOutput("", output, CodeReviewProfileJsonSchema.SCHEMA_NAME,
            CodeReviewProfileConsumerConstants.SCHEMA_VERSION, Map.of()),
        Map.of(
            AgentRuntimeMetadataKeys.RUN_DB_ID, runDbId,
            AgentRuntimeMetadataKeys.RUNTIME_PROVIDER, "test-provider",
            AgentRuntimeMetadataKeys.RUNTIME_MODEL, "test-model"));
  }

  private static ProfileUpdateApplyResult applied(ProfileUpdateApplyStatus status) {
    return new ProfileUpdateApplyResult(status, Optional.empty(), "token");
  }

  private static final class RecordingFactRepository implements CodeReviewProfileFactRepository {
    private final List<CodeReviewProfileFact> window;

    private RecordingFactRepository(List<CodeReviewProfileFact> window) {
      this.window = List.copyOf(window);
    }

    @Override
    public List<CodeReviewProfileFact> findByReviewIds(long userId, List<Long> reviewIds) {
      return List.of();
    }

    @Override
    public List<CodeReviewProfileFact> findLatestForProblemSlugs(long userId, List<String> problemSlugs) {
      return window;
    }

    @Override
    public List<CodeReviewProfileFact> findRecentDistinctProblems(long userId, List<String> excluded, int limit) {
      return List.of();
    }
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
