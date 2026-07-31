package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentOutput;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.model.AgentMessage;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.agent.core.runtime.model.AgentTurnMessages;
import org.congcong.algomentor.agent.core.runtime.repository.AgentTurnMessageLookupRepository;
import org.congcong.algomentor.api.profile.mapper.LearnerMemoryMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryClaimRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryEvidenceRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryUpdateRunRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.mentor.application.profile.LearnerMemoryClaimDimension;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateAgentInput;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateJsonSchema;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdatePromptBuilder;
import org.congcong.algomentor.mentor.application.profile.ai.DeclaredProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimQueryService;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimSnapshotFactory;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimTextHasher;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.evidence.service.LearnerMemoryEvidenceGradeCalculator;
import org.congcong.algomentor.mentor.application.profile.evidence.service.LearnerMemoryEvidenceValidator;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryAtomicApplyService;
import org.congcong.algomentor.mentor.application.profile.run.model.LearnerMemoryRunContract;
import org.congcong.algomentor.mentor.application.profile.run.service.LearnerMemoryUpdateRunLifecycleService;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateIntent;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateRequest;
import org.congcong.algomentor.mentor.application.profile.tool.DeclaredProfileUpdateResult;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerDeclaredProfileToolContracts;
import org.junit.jupiter.api.Test;

class LearnerMemoryDeclaredEndToEndIT extends PostgresIntegrationTestSupport {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void writesDeclaredClaimWithTheTrustedCurrentMessageRatherThanToolStatement() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long messageId = insertUserMessage(userId);
    Fixture fixture = fixture(userId, messageId, addOutput());

    DeclaredProfileUpdateResult result = fixture.service().update(userId, request(DeclaredProfileUpdateIntent.DECLARE), 401L, 1);

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.UPDATED);
    assertThat(fixture.claims().findActiveByUser(userId)).singleElement().satisfies(claim ->
        assertThat(claim.claimText()).isEqualTo("目标是后端岗位。"));
    long revisionId = fixture.claims().findActiveByUser(userId).get(0).id();
    assertThat(fixture.evidence().findMessageEvidenceByRevisionIds(userId, List.of(revisionId)))
        .extracting(evidence -> evidence.messageId()).containsExactly(messageId);
    assertThat(fixture.runtime().lastInvocation).isNotNull();
    assertThat(((DeclaredProfileUpdateAgentInput) fixture.runtime().lastInvocation.input())
        .candidates().get(0).statement()).isEqualTo("trusted current user message");
    assertThat(queryLong("SELECT COUNT(*) FROM learner_memory_update_run WHERE status = 'SUCCEEDED'"))
        .isEqualTo(1L);
  }

  @Test
  void revisesOnlyTheTargetClaimAndCarriesForwardPriorMessageEvidence() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long firstMessage = insertUserMessage(userId);
    Fixture addFixture = fixture(userId, firstMessage, addOutput());
    addFixture.service().update(userId, request(DeclaredProfileUpdateIntent.DECLARE), 401L, 1);
    long targetId = addFixture.claims().findActiveByUser(userId).get(0).id();

    long correctionMessage = insertUserMessage(userId);
    Fixture reviseFixture = fixture(userId, correctionMessage, reviseOutput(targetId));
    DeclaredProfileUpdateResult result = reviseFixture.service().update(
        userId, request(DeclaredProfileUpdateIntent.CORRECT), 401L, 2);

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.UPDATED);
    assertThat(reviseFixture.claims().findActiveByUser(userId)).singleElement().satisfies(claim ->
        assertThat(claim.claimText()).isEqualTo("目标转为分布式系统岗位。"));
    long revisedId = reviseFixture.claims().findActiveByUser(userId).get(0).id();
    assertThat(reviseFixture.evidence().findMessageEvidenceByRevisionIds(userId, List.of(revisedId)))
        .extracting(evidence -> evidence.messageId())
        .containsExactlyInAnyOrder(firstMessage, correctionMessage);
    assertThat(reviseFixture.evidence().findMessageEvidenceByRevisionIds(userId, List.of(revisedId)))
        .extracting(evidence -> evidence.role())
        .contains(LearnerMemoryEvidenceContract.MessageRole.DECLARED,
            LearnerMemoryEvidenceContract.MessageRole.CORRECTED);
  }

  @Test
  void completesAnEmptyOperationBatchWithoutChangingClaims() throws Exception {
    migrateLatest();
    long userId = insertUser();
    Fixture fixture = fixture(userId, insertUserMessage(userId), emptyOutput());

    DeclaredProfileUpdateResult result = fixture.service().update(
        userId, request(DeclaredProfileUpdateIntent.DECLARE), 401L, 1);

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.NO_CHANGE);
    assertThat(fixture.claims().findActiveByUser(userId)).isEmpty();
    assertThat(queryLong("SELECT COUNT(*) FROM learner_memory_update_run WHERE status = 'NO_CHANGE'"))
        .isEqualTo(1L);
  }

  @Test
  void rejectsForgedAndDisallowedAgentOperationsWithoutClaimWrites() throws Exception {
    migrateLatest();
    long userId = insertUser();
    List<JsonNode> invalidOutputs = List.of(
        objectMapper.readTree("""
            {"operations":[{"action":"REVISE","targetRevisionId":999999,"claimText":"伪造目标。"}]}
            """),
        objectMapper.readTree("""
            {"operations":[{"action":"CONFIRM","targetRevisionId":999999}]}
            """),
        objectMapper.readTree("""
            {"operations":[{"action":"ADD","dimension":"LEARNER_BACKGROUND","claimText":"越权维度。"}]}
            """));

    for (int index = 0; index < invalidOutputs.size(); index++) {
      Fixture fixture = fixture(userId, insertUserMessage(userId), invalidOutputs.get(index));

      DeclaredProfileUpdateResult result = fixture.service().update(
          userId, request(DeclaredProfileUpdateIntent.DECLARE), 401L, index + 1);

      assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.FAILED);
      assertThat(fixture.claims().findActiveByUser(userId)).isEmpty();
      assertThat(fixture.runtime().lastInvocation).isNotNull();
      assertThat(queryLong(
          "SELECT COUNT(*) FROM learner_memory_update_run WHERE status = 'FAILED' AND agent_run_id = ?",
          fixture.runtime().agentRunId)).isEqualTo(1L);
    }
  }

  @Test
  void rejectsDuplicateTargetsWithoutPartiallyRevisingTheClaim() throws Exception {
    migrateLatest();
    long userId = insertUser();
    Fixture addFixture = fixture(userId, insertUserMessage(userId), addOutput());
    addFixture.service().update(userId, request(DeclaredProfileUpdateIntent.DECLARE), 401L, 1);
    long targetId = addFixture.claims().findActiveByUser(userId).get(0).id();
    Fixture duplicateFixture = fixture(userId, insertUserMessage(userId), objectMapper.readTree("""
        {"operations":[
          {"action":"REVISE","targetRevisionId":%d,"claimText":"目标改为平台工程岗位。"},
          {"action":"REVISE","targetRevisionId":%d,"claimText":"目标改为数据工程岗位。"}
        ]}
        """.formatted(targetId, targetId)));

    DeclaredProfileUpdateResult result = duplicateFixture.service().update(
        userId, request(DeclaredProfileUpdateIntent.CORRECT), 401L, 2);

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.FAILED);
    assertThat(duplicateFixture.claims().findActiveByUser(userId)).singleElement().satisfies(claim ->
        assertThat(claim.claimText()).isEqualTo("目标是后端岗位。"));
    assertThat(queryLong("SELECT COUNT(*) FROM learner_memory_update_run WHERE status = 'FAILED'"))
        .isEqualTo(1L);
  }

  @Test
  void failsBeforeAgentExecutionWhenTheCurrentMessageIsMissingOrForeign() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long foreignUserId = insertUser();
    Fixture foreignMessage = fixture(userId, insertUserMessage(foreignUserId), addOutput());
    Fixture missingMessage = fixture(userId, addOutput(), runId -> Optional.empty());

    DeclaredProfileUpdateResult foreignResult = foreignMessage.service().update(
        userId, request(DeclaredProfileUpdateIntent.DECLARE), 401L, 1);
    DeclaredProfileUpdateResult missingResult = missingMessage.service().update(
        userId, request(DeclaredProfileUpdateIntent.DECLARE), 401L, 2);

    assertThat(foreignResult.status()).isEqualTo(DeclaredProfileUpdateResult.Status.FAILED);
    assertThat(missingResult.status()).isEqualTo(DeclaredProfileUpdateResult.Status.FAILED);
    assertThat(foreignMessage.runtime().lastInvocation).isNull();
    assertThat(missingMessage.runtime().lastInvocation).isNull();
    assertThat(foreignMessage.claims().findActiveByUser(userId)).isEmpty();
    assertThat(queryLong("SELECT COUNT(*) FROM learner_memory_update_run WHERE status = 'FAILED'"))
        .isEqualTo(2L);
  }

  @Test
  void retriesOnceWithTheFreshRequestedScopeAfterAStaleSnapshot() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long messageId = insertUserMessage(userId);
    Fixture mutation = fixtureForAnyParent(userId, messageId, addOutput("并发目标。"));
    MutatingRuntime runtime = new MutatingRuntime(
        userId,
        request(DeclaredProfileUpdateIntent.DECLARE),
        List.of(mutation.service()),
        List.of(addOutput("最终目标。"), addOutput("最终目标。")),
        List.of(insertAgentRun(userId), insertAgentRun(userId)));
    StaleFixture fixture = staleFixture(userId, messageId, runtime);

    DeclaredProfileUpdateResult result = fixture.service().update(
        userId, request(DeclaredProfileUpdateIntent.DECLARE), 401L, 1);

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.UPDATED);
    assertThat(runtime.invocations).hasSize(2);
    DeclaredProfileUpdateAgentInput retryInput = (DeclaredProfileUpdateAgentInput) runtime.invocations.get(1).input();
    assertThat(retryInput.retryOfRunId()).isEqualTo(runtime.agentRunIds.get(0));
    assertThat(retryInput.candidates().get(0).activeClaims())
        .extracting(DeclaredProfileUpdateAgentInput.ActiveClaim::claimText)
        .containsExactly("并发目标。");
    assertThat(fixture.claims().findActiveByUser(userId)).extracting(claim -> claim.claimText())
        .containsExactlyInAnyOrder("并发目标。", "最终目标。");
  }

  @Test
  void failsAfterTheSecondStaleSnapshotWithoutWritingTheOriginalBatch() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long messageId = insertUserMessage(userId);
    Fixture firstMutation = fixtureForAnyParent(userId, messageId, addOutput("第一条并发目标。"));
    Fixture secondMutation = fixtureForAnyParent(userId, messageId, addOutput("第二条并发目标。"));
    MutatingRuntime runtime = new MutatingRuntime(
        userId,
        request(DeclaredProfileUpdateIntent.DECLARE),
        List.of(firstMutation.service(), secondMutation.service()),
        List.of(addOutput("原始目标。"), addOutput("原始目标。")),
        List.of(insertAgentRun(userId), insertAgentRun(userId)));
    StaleFixture fixture = staleFixture(userId, messageId, runtime);

    DeclaredProfileUpdateResult result = fixture.service().update(
        userId, request(DeclaredProfileUpdateIntent.DECLARE), 401L, 1);

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.FAILED);
    assertThat(runtime.invocations).hasSize(2);
    assertThat(fixture.claims().findActiveByUser(userId)).extracting(claim -> claim.claimText())
        .containsExactlyInAnyOrder("第一条并发目标。", "第二条并发目标。");
    assertThat(queryLong(
        """
        SELECT COUNT(*) FROM learner_memory_claim_revision
        WHERE update_run_id IN (
          SELECT id FROM learner_memory_update_run
          WHERE status = 'FAILED' AND agent_run_id = ? AND operation_count = 0
        )
        """,
        runtime.agentRunIds.get(1))).isZero();
  }

  @Test
  void recordsAnAgentRuntimeFailureWithoutWritingClaims() throws Exception {
    migrateLatest();
    long userId = insertUser();
    StaleFixture fixture = staleFixture(userId, insertUserMessage(userId), new FailingRuntime());

    DeclaredProfileUpdateResult result = fixture.service().update(
        userId, request(DeclaredProfileUpdateIntent.DECLARE), 401L, 1);

    assertThat(result.status()).isEqualTo(DeclaredProfileUpdateResult.Status.FAILED);
    assertThat(fixture.claims().findActiveByUser(userId)).isEmpty();
    assertThat(queryLong(
        "SELECT COUNT(*) FROM learner_memory_update_run WHERE status = 'FAILED' AND failure_code = 'AGENT_FAILURE'"))
        .isEqualTo(1L);
  }

  private Fixture fixture(long userId, long messageId, JsonNode output) throws Exception {
    return fixture(userId, output, runId -> runId == 401L
        ? Optional.of(new AgentTurnMessages(
            runId,
            1L,
            new AgentMessage(messageId, 1L, 1L, AgentMessage.Role.USER,
                "trusted current user message", Instant.parse("2026-07-30T00:00:00Z")),
            null))
        : Optional.empty());
  }

  private Fixture fixtureForAnyParent(long userId, long messageId, JsonNode output) throws Exception {
    return fixture(userId, output, trustedMessageForAnyParent(messageId));
  }

  private StaleFixture staleFixture(long userId, long messageId, AgentRuntime runtime) throws Exception {
    LearnerMemoryMapper mapper = sqlSessionTemplate("mapper/profile/LearnerMemoryMapper.xml")
        .getMapper(LearnerMemoryMapper.class);
    MyBatisLearnerMemoryClaimRepository claims = new MyBatisLearnerMemoryClaimRepository(mapper);
    MyBatisLearnerMemoryEvidenceRepository evidence = new MyBatisLearnerMemoryEvidenceRepository(mapper);
    MyBatisLearnerMemoryUpdateRunRepository runs = new MyBatisLearnerMemoryUpdateRunRepository(mapper);
    LearnerMemoryClaimSnapshotFactory snapshots = new LearnerMemoryClaimSnapshotFactory();
    LearnerMemoryUpdateRunLifecycleService lifecycle = new LearnerMemoryUpdateRunLifecycleService(
        runs, transactionTemplate());
    LearnerMemoryAtomicApplyService applyService = new LearnerMemoryAtomicApplyService(
        claims, evidence, runs, new LearnerMemoryClaimTextHasher(), snapshots,
        new LearnerMemoryEvidenceValidator(), new LearnerMemoryEvidenceGradeCalculator(), lifecycle,
        transactionTemplate());
    return new StaleFixture(
        new DeclaredProfileUpdateService(
            new LearnerMemoryClaimQueryService(claims, snapshots), evidence, runs, applyService, lifecycle,
            trustedMessageForAnyParent(messageId), runtime, new DeclaredProfileUpdatePromptBuilder(), 1, 300),
        claims);
  }

  private AgentTurnMessageLookupRepository trustedMessageForAnyParent(long messageId) {
    return runId -> Optional.of(new AgentTurnMessages(
        runId,
        1L,
        new AgentMessage(messageId, 1L, 1L, AgentMessage.Role.USER,
            "trusted current user message", Instant.parse("2026-07-30T00:00:00Z")),
        null));
  }

  private Fixture fixture(
      long userId,
      JsonNode output,
      AgentTurnMessageLookupRepository messages) throws Exception {
    LearnerMemoryMapper mapper = sqlSessionTemplate("mapper/profile/LearnerMemoryMapper.xml")
        .getMapper(LearnerMemoryMapper.class);
    MyBatisLearnerMemoryClaimRepository claims = new MyBatisLearnerMemoryClaimRepository(mapper);
    MyBatisLearnerMemoryEvidenceRepository evidence = new MyBatisLearnerMemoryEvidenceRepository(mapper);
    MyBatisLearnerMemoryUpdateRunRepository runs = new MyBatisLearnerMemoryUpdateRunRepository(mapper);
    LearnerMemoryClaimSnapshotFactory snapshots = new LearnerMemoryClaimSnapshotFactory();
    LearnerMemoryUpdateRunLifecycleService lifecycle = new LearnerMemoryUpdateRunLifecycleService(
        runs, transactionTemplate());
    LearnerMemoryAtomicApplyService applyService = new LearnerMemoryAtomicApplyService(
        claims, evidence, runs, new LearnerMemoryClaimTextHasher(), snapshots,
        new LearnerMemoryEvidenceValidator(), new LearnerMemoryEvidenceGradeCalculator(), lifecycle,
        transactionTemplate());
    FixedRuntime runtime = new FixedRuntime(output, insertAgentRun(userId));
    return new Fixture(
        new DeclaredProfileUpdateService(
            new LearnerMemoryClaimQueryService(claims, snapshots), evidence, runs, applyService, lifecycle,
            messages, runtime, new DeclaredProfileUpdatePromptBuilder(), 1, 300),
        claims, evidence, runtime);
  }

  private DeclaredProfileUpdateRequest request(DeclaredProfileUpdateIntent intent) {
    return new DeclaredProfileUpdateRequest(List.of(new DeclaredProfileUpdateRequest.Item(
        LearnerMemoryClaimDimension.GOALS_AND_INTENTS, "untrusted tool statement", intent)));
  }

  private JsonNode addOutput() throws Exception {
    return addOutput("目标是后端岗位。");
  }

  private JsonNode addOutput(String claimText) throws Exception {
    return objectMapper.readTree("""
        {"operations":[{"action":"ADD","dimension":"GOALS_AND_INTENTS","claimText":"%s"}]}
        """.formatted(claimText));
  }

  private JsonNode emptyOutput() throws Exception {
    return objectMapper.readTree("{\"operations\":[]}");
  }

  private JsonNode reviseOutput(long targetRevisionId) throws Exception {
    return objectMapper.readTree("""
        {"operations":[{"action":"REVISE","targetRevisionId":%d,"claimText":"目标转为分布式系统岗位。"}]}
        """.formatted(targetRevisionId));
  }

  private long insertAgentRun(long userId) throws Exception {
    long taskId = queryLong(
        """
        INSERT INTO agent_task (user_id, status, context_policy, metadata, created_at, updated_at)
        VALUES (?, 'ACTIVE', '{}'::jsonb, '{}'::jsonb, NOW(), NOW())
        RETURNING id
        """,
        userId);
    long turnId = queryLong(
        """
        INSERT INTO agent_turn (task_id, sequence_no, status, created_at, updated_at)
        VALUES (?, 1, 'COMPLETED', NOW(), NOW())
        RETURNING id
        """,
        taskId);
    return queryLong(
        """
        INSERT INTO agent_run (
          task_id, turn_id, run_uuid, attempt_no, idempotency_key, trigger_type, status, max_steps,
          usage, error, started_at, ended_at)
        VALUES (?, ?, ?, 1, ?, 'BACKGROUND', 'COMPLETED', 1, '{}'::jsonb, '{}'::jsonb, NOW(), NOW())
        RETURNING id
        """,
        taskId, turnId, UUID.randomUUID().toString(), "declared-child-" + UUID.randomUUID());
  }

  private record Fixture(
      DeclaredProfileUpdateService service,
      MyBatisLearnerMemoryClaimRepository claims,
      MyBatisLearnerMemoryEvidenceRepository evidence,
      FixedRuntime runtime) {
  }

  private record StaleFixture(
      DeclaredProfileUpdateService service,
      MyBatisLearnerMemoryClaimRepository claims) {
  }

  private static final class FixedRuntime implements AgentRuntime {
    private final JsonNode output;
    private final long agentRunId;
    private AgentInvocation<?> lastInvocation;

    private FixedRuntime(JsonNode output, long agentRunId) {
      this.output = output;
      this.agentRunId = agentRunId;
    }

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      lastInvocation = invocation;
      return new AgentRunResult(
          1,
          LlmFinishReason.STOP,
          new AgentOutput("", output, DeclaredProfileUpdateJsonSchema.SCHEMA_NAME,
              LearnerDeclaredProfileToolContracts.SCHEMA_VERSION, Map.of()),
          Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, agentRunId));
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException("stream not used");
    }
  }

  private static final class MutatingRuntime implements AgentRuntime {
    private final long userId;
    private final DeclaredProfileUpdateRequest request;
    private final List<DeclaredProfileUpdateService> mutations;
    private final List<JsonNode> outputs;
    private final List<Long> agentRunIds;
    private final List<AgentInvocation<?>> invocations = new java.util.ArrayList<>();

    private MutatingRuntime(
        long userId,
        DeclaredProfileUpdateRequest request,
        List<DeclaredProfileUpdateService> mutations,
        List<JsonNode> outputs,
        List<Long> agentRunIds) {
      this.userId = userId;
      this.request = request;
      this.mutations = List.copyOf(mutations);
      this.outputs = List.copyOf(outputs);
      this.agentRunIds = List.copyOf(agentRunIds);
    }

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      invocations.add(invocation);
      int attempt = invocations.size();
      if (attempt <= mutations.size() && mutations.get(attempt - 1)
          .update(userId, request, 501L + attempt, 1).status() != DeclaredProfileUpdateResult.Status.UPDATED) {
        throw new IllegalStateException("concurrent declared mutation must succeed");
      }
      return new AgentRunResult(
          1,
          LlmFinishReason.STOP,
          new AgentOutput("", outputs.get(attempt - 1), DeclaredProfileUpdateJsonSchema.SCHEMA_NAME,
              LearnerDeclaredProfileToolContracts.SCHEMA_VERSION, Map.of()),
          Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, agentRunIds.get(attempt - 1)));
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException("stream not used");
    }
  }

  private static final class FailingRuntime implements AgentRuntime {

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      throw new IllegalStateException("test runtime failure");
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException("stream not used");
    }
  }
}
