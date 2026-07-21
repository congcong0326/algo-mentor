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
import org.congcong.algomentor.ai.governance.completion.AiCompletionContext;
import org.congcong.algomentor.ai.governance.completion.AiCompletionGateway;
import org.congcong.algomentor.ai.governance.completion.AiCompletionMode;
import org.congcong.algomentor.ai.governance.model.AiPurpose;
import org.congcong.algomentor.ai.governance.model.AiRunSource;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
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
  void appliesAllowedGeneralAndTagReplacementsWithBackgroundGovernance() throws Exception {
    RecordingFactRepository facts = new RecordingFactRepository(List.of(fact(1L, List.of(9L))));
    RecordingQueryService queryService = new RecordingQueryService();
    RecordingUpdateService updateService = new RecordingUpdateService(List.of(List.of(
        applied(ProfileUpdateApplyStatus.APPLIED),
        applied(ProfileUpdateApplyStatus.APPLIED),
        applied(ProfileUpdateApplyStatus.APPLIED))));
    RecordingGateway gateway = new RecordingGateway(true, List.of(completion(output(
        "REPLACE", "优先构造解题计划", "REPLACE", "补充边界检查", 9L, "REPLACE", "数组处理稳定"))));

    CodeReviewProfileUpdateResult result = service(facts, queryService, updateService, gateway, 1)
        .update(7L, List.of(fact(1L, List.of(9L))));

    assertThat(result.status()).isEqualTo(CodeReviewProfileUpdateResult.Status.UPDATED);
    assertThat(result.windowProblemCount()).isEqualTo(1);
    assertThat(result.appliedCount()).isEqualTo(3);
    assertThat(updateService.calls).hasSize(1);
    assertThat(updateService.calls.get(0)).extracting(ProfileUpdateCommand::originType)
        .allMatch(origin -> origin == org.congcong.algomentor.mentor.application.profile.LearnerProfileOriginType.SYSTEM_DERIVED);
    assertThat(gateway.contexts).singleElement().satisfies(context -> {
      assertThat(context.mode()).isEqualTo(AiCompletionMode.BACKGROUND);
      assertThat(context.purpose()).isEqualTo(AiPurpose.LEARNING_CHAT);
      assertThat(context.source()).isEqualTo(AiRunSource.LEARNER_PROFILE_CODE_REVIEW_BATCH);
      assertThat(context.requestSize()).isEqualTo(1);
    });
    assertThat(gateway.requests).singleElement().satisfies(request ->
        assertThat(request.responseFormat()).isInstanceOf(org.congcong.algomentor.llm.core.request.LlmResponseFormat.JsonSchema.class));
  }

  @Test
  void keepsNoChangeAsZeroWriteBatch() throws Exception {
    RecordingFactRepository facts = new RecordingFactRepository(List.of(fact(1L, List.of())));
    RecordingUpdateService updateService = new RecordingUpdateService(List.of(List.of(
        applied(ProfileUpdateApplyStatus.NO_CHANGE), applied(ProfileUpdateApplyStatus.NO_CHANGE))));
    RecordingGateway gateway = new RecordingGateway(true, List.of(completion(output(
        "NO_CHANGE", "", "NO_CHANGE", "", null, null, null))));

    CodeReviewProfileUpdateResult result = service(facts, new RecordingQueryService(), updateService, gateway, 1)
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
    RecordingGateway gateway = new RecordingGateway(true, List.of(completion(output(
        "NO_CHANGE", "", "NO_CHANGE", "", 10L, "REPLACE", "not allowed"))));

    CodeReviewProfileUpdateResult result = service(facts, new RecordingQueryService(), updateService, gateway, 1)
        .update(7L, List.of(fact(1L, List.of(9L))));

    assertThat(result.status()).isEqualTo(CodeReviewProfileUpdateResult.Status.FAILED);
    assertThat(updateService.calls).isEmpty();
    assertThat(gateway.requests).hasSize(1);
  }

  @Test
  void rebuildsSnapshotsAndModelDecisionOnceAfterStale() throws Exception {
    RecordingFactRepository facts = new RecordingFactRepository(List.of(fact(1L, List.of(9L))));
    RecordingQueryService queryService = new RecordingQueryService();
    RecordingUpdateService updateService = new RecordingUpdateService(List.of(
        List.of(applied(ProfileUpdateApplyStatus.STALE), applied(ProfileUpdateApplyStatus.STALE), applied(ProfileUpdateApplyStatus.STALE)),
        List.of(applied(ProfileUpdateApplyStatus.NO_CHANGE), applied(ProfileUpdateApplyStatus.NO_CHANGE), applied(ProfileUpdateApplyStatus.NO_CHANGE))));
    RecordingGateway gateway = new RecordingGateway(true, List.of(
        completion(output("NO_CHANGE", "", "NO_CHANGE", "", 9L, "NO_CHANGE", "")),
        completion(output("NO_CHANGE", "", "NO_CHANGE", "", 9L, "NO_CHANGE", ""))));

    CodeReviewProfileUpdateResult result = service(facts, queryService, updateService, gateway, 1)
        .update(7L, List.of(fact(1L, List.of(9L))));

    assertThat(result.status()).isEqualTo(CodeReviewProfileUpdateResult.Status.NO_CHANGE);
    assertThat(gateway.requests).hasSize(2);
    assertThat(updateService.calls).hasSize(2);
    assertThat(queryService.calls).isEqualTo(6);
  }

  @Test
  void stopsAfterSecondStaleOrGovernanceDenialWithoutQueueRetrySignal() throws Exception {
    RecordingFactRepository facts = new RecordingFactRepository(List.of(fact(1L, List.of())));
    RecordingUpdateService staleUpdateService = new RecordingUpdateService(List.of(
        List.of(applied(ProfileUpdateApplyStatus.STALE), applied(ProfileUpdateApplyStatus.STALE)),
        List.of(applied(ProfileUpdateApplyStatus.STALE), applied(ProfileUpdateApplyStatus.STALE))));
    RecordingGateway staleGateway = new RecordingGateway(true, List.of(
        completion(output("NO_CHANGE", "", "NO_CHANGE", "", null, null, null)),
        completion(output("NO_CHANGE", "", "NO_CHANGE", "", null, null, null))));

    CodeReviewProfileUpdateResult stale = service(facts, new RecordingQueryService(), staleUpdateService, staleGateway, 1)
        .update(7L, List.of(fact(1L, List.of())));
    CodeReviewProfileUpdateResult denied = service(facts, new RecordingQueryService(),
        new RecordingUpdateService(List.of()), new RecordingGateway(false, List.of()), 1)
        .update(7L, List.of(fact(1L, List.of())));

    assertThat(stale.status()).isEqualTo(CodeReviewProfileUpdateResult.Status.FAILED);
    assertThat(staleGateway.requests).hasSize(2);
    assertThat(denied.status()).isEqualTo(CodeReviewProfileUpdateResult.Status.FAILED);
  }

  private CodeReviewProfileUpdateService service(
      CodeReviewProfileFactRepository facts,
      LearnerProfileQueryService queryService,
      LearnerProfileUpdateService updateService,
      AiCompletionGateway gateway,
      int maxStaleRetries
  ) {
    return new CodeReviewProfileUpdateService(
        facts, queryService, updateService, gateway, new CodeReviewProfilePromptBuilder(),
        new CodeReviewProfileStructuredOutputMapper(), maxStaleRetries);
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

  private static LlmCompletionResult completion(JsonNode output) {
    return new LlmCompletionResult(
        LlmMessage.assistant("{}"), List.of(), output, LlmFinishReason.STOP, LlmUsage.empty(),
        LlmProviderId.of("test-provider"), LlmModelId.of("test-model"), Map.of());
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

  private static final class RecordingGateway implements AiCompletionGateway {
    private final boolean allowed;
    private final List<LlmCompletionResult> completions;
    private final List<LlmCompletionRequest> requests = new ArrayList<>();
    private final List<AiCompletionContext> contexts = new ArrayList<>();

    private RecordingGateway(boolean allowed, List<LlmCompletionResult> completions) {
      this.allowed = allowed;
      this.completions = List.copyOf(completions);
    }

    @Override
    public boolean isAllowed(AiCompletionContext context) {
      return allowed;
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request, AiCompletionContext context) {
      requests.add(request);
      contexts.add(context);
      return completions.get(requests.size() - 1);
    }
  }
}
