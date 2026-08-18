package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
import org.congcong.algomentor.agent.core.AgentOutput;
import org.congcong.algomentor.agent.core.AgentRunResult;
import org.congcong.algomentor.agent.core.AgentStreamEvent;
import org.congcong.algomentor.agent.core.runtime.api.AgentInvocation;
import org.congcong.algomentor.agent.core.runtime.api.AgentRuntime;
import org.congcong.algomentor.agent.core.runtime.model.AgentRuntimeMetadataKeys;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.repository.MyBatisPracticeCodeReviewRepository;
import org.congcong.algomentor.api.profile.mapper.LearnerMemoryMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisCodeReviewHistoryRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryCodeReviewFactRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryClaimRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryEvidenceRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryUpdateRunRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitResult;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimQueryService;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimSnapshotFactory;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimTextHasher;
import org.congcong.algomentor.mentor.application.profile.evidence.service.LearnerMemoryEvidenceGradeCalculator;
import org.congcong.algomentor.mentor.application.profile.evidence.service.LearnerMemoryEvidenceValidator;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryAtomicApplyService;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewBatchConsumer;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewConsumerConstants;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewFactRepository;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewJsonSchema;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewQueueContracts;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewStructuredOutputMapper;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewUpdateAgentInput;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewUpdateResult;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewUpdateService;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistoryRepository;
import org.congcong.algomentor.mentor.application.profile.review.snapshot.LearnerReviewFactSnapshot;
import org.congcong.algomentor.mentor.application.profile.run.repository.LearnerMemoryUpdateRunRepository;
import org.congcong.algomentor.mentor.application.profile.run.service.LearnerMemoryUpdateRunLifecycleService;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
import org.congcong.algomentor.queue.consumer.QueueConsumerRegistry;
import org.congcong.algomentor.queue.dispatch.QueueDequeueService;
import org.congcong.algomentor.queue.dispatch.QueueDispatchOutcome;
import org.congcong.algomentor.queue.dispatch.QueueDispatcher;
import org.congcong.algomentor.queue.postgres.MyBatisQueueMessageRepository;
import org.congcong.algomentor.queue.postgres.QueueMessageMapper;
import org.congcong.algomentor.queue.publisher.PostgresQueuePublisher;
import org.junit.jupiter.api.Test;

class LearnerMemoryEndToEndIT extends PostgresIntegrationTestSupport {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void commitsFiveFormalReviewsThenWritesOneAtomicCodeReviewClaimBatch() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long tagId = insertCatalog("array", "Array", "数组", true);
    insertProblem("two-sum", 1, List.of(), List.of(), List.of());
    assignTag("two-sum", tagId, 0);
    long sessionId = insertPracticeSession(userId, "two-sum");
    enqueueFiveFormalReviews(userId, sessionId, tagId);

    long agentRunId = insertAgentRun(userId);
    FixedRuntime runtime = new FixedRuntime(agentRunId);
    assertThat(dispatcher(runtime).dispatchRound(LearnerMemoryCodeReviewQueueContracts.TOPIC))
        .containsExactly(QueueDispatchOutcome.DISPATCHED);

    assertThat(runtime.calls).isEqualTo(1);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE topic = ? AND status = 'SUCCEEDED'",
        LearnerMemoryCodeReviewQueueContracts.TOPIC)).isEqualTo(5L);
    assertThat(queryLong("SELECT COUNT(*) FROM learner_memory_update_run WHERE trigger_type = 'CODE_REVIEW_BATCH'"))
        .isEqualTo(1L);
    assertThat(queryLong(
        "SELECT COUNT(*) FROM learner_memory_update_run WHERE agent_run_id = ?", agentRunId)).isEqualTo(1L);
    assertThat(queryLong("SELECT COUNT(*) FROM learner_memory_update_run_review")).isEqualTo(5L);
    assertThat(queryLong("SELECT COUNT(*) FROM learner_memory_claim_revision WHERE status = 'ACTIVE'"))
        .isEqualTo(1L);
    assertThat(queryString("SELECT to_regclass('learner_profile_entry')::text")).isNull();
  }

  @Test
  void repairsInvalidEvidenceOnceBeforeQueueRetry() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long tagId = insertCatalog("array", "Array", "数组", true);
    insertProblem("two-sum", 1, List.of(), List.of(), List.of());
    assignTag("two-sum", tagId, 0);
    long sessionId = insertPracticeSession(userId, "two-sum");
    enqueueFiveFormalReviews(userId, sessionId, tagId);

    long initialAgentRunId = insertAgentRun(userId);
    long repairAgentRunId = insertAgentRun(userId);
    FixedRuntime runtime = new FixedRuntime(List.of(initialAgentRunId, repairAgentRunId), true);

    assertThat(dispatcher(runtime).dispatchRound(LearnerMemoryCodeReviewQueueContracts.TOPIC))
        .containsExactly(QueueDispatchOutcome.DISPATCHED);

    assertThat(runtime.calls).isEqualTo(2);
    assertThat(runtime.inputs).hasSize(2);
    assertThat(runtime.inputs.get(0).evidenceRepair()).isFalse();
    assertThat(runtime.inputs.get(1).evidenceRepair()).isTrue();
    assertThat(runtime.inputs.get(1).retryOfRunId()).isEqualTo(initialAgentRunId);
    assertThat(runtime.inputs.get(1).idempotencyKey()).endsWith(":evidence-repair");
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE topic = ? AND status = 'SUCCEEDED'",
        LearnerMemoryCodeReviewQueueContracts.TOPIC)).isEqualTo(5L);
    assertThat(queryLong("SELECT agent_run_id FROM learner_memory_update_run WHERE trigger_type = 'CODE_REVIEW_BATCH'"))
        .isEqualTo(repairAgentRunId);
    assertThat(queryLong("SELECT COUNT(*) FROM learner_memory_claim_revision WHERE status = 'ACTIVE'"))
        .isEqualTo(1L);
  }

  @Test
  void includesAllNineReviewsInTheAgentFactSnapshotWhileKeepingTheFiveReviewBatch() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long tagId = insertCatalog("array", "Array", "数组", true);
    List<SampleReview> sample = List.of(
        new SampleReview("merge-sorted-array", false),
        new SampleReview("merge-sorted-array", true),
        new SampleReview("remove-element", false),
        new SampleReview("remove-element", true),
        new SampleReview("remove-duplicates-from-sorted-array", true),
        new SampleReview("remove-duplicates-from-sorted-array", false),
        new SampleReview("remove-duplicates-from-sorted-array", true),
        new SampleReview("remove-duplicates-from-sorted-array-ii", true),
        new SampleReview("majority-element", true));
    List<String> problemSlugs = sample.stream().map(SampleReview::problemSlug).distinct().toList();
    for (int index = 0; index < problemSlugs.size(); index++) {
      String slug = problemSlugs.get(index);
      insertProblem(slug, index + 1, List.of(), List.of(), List.of());
      assignTag(slug, tagId, 0);
    }

    PracticeCodeReviewCommitService commitService = commitService();
    Map<String, Long> sessions = new java.util.HashMap<>();
    for (SampleReview review : sample) {
      Long existingSessionId = sessions.get(review.problemSlug());
      long sessionId = existingSessionId == null
          ? insertPracticeSession(userId, review.problemSlug())
          : existingSessionId;
      sessions.put(review.problemSlug(), sessionId);
      long messageId = insertUserMessage(userId);
      PracticeCodeReviewCommitResult result = transactionTemplate().execute(status ->
          commitService.commit(draft(userId, sessionId, messageId, tagId, review.problemSlug(), review.passed())));
      assertThat(result.created()).isTrue();
    }

    LearnerMemoryCodeReviewFactRepository facts = new MyBatisLearnerMemoryCodeReviewFactRepository(
        sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml").getMapper(PracticeCodeReviewMapper.class),
        objectMapper);
    List<org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewFact> allFacts =
        facts.findAllForUser(userId);
    FixedRuntime runtime = new FixedRuntime(insertAgentRun(userId));

    assertThat(updateService(facts, runtime).update(userId, allFacts.subList(4, 9)).status())
        .isEqualTo(LearnerMemoryCodeReviewUpdateResult.Status.UPDATED);
    assertThat(runtime.inputs).singleElement().satisfies(input -> {
      LearnerReviewFactSnapshot factSnapshot = input.reviewFactSnapshot();
      assertThat(factSnapshot.coverage().reviewCount()).isEqualTo(9);
      assertThat(factSnapshot.coverage().distinctProblemCount()).isEqualTo(5);
      assertThat(factSnapshot.overall().passedReviewCount()).isEqualTo(6);
      assertThat(factSnapshot.overall().failedReviewCount()).isEqualTo(3);
      assertThat(factSnapshot.overall().latestByProblem()).isEqualTo(new LearnerReviewFactSnapshot.PassCount(5, 5));
      assertThat(factSnapshot.overall().firstAttemptByProblem()).isEqualTo(new LearnerReviewFactSnapshot.PassCount(3, 5));
      assertThat(factSnapshot.overall().recoveredFailureCount()).isEqualTo(3);
      assertThat(factSnapshot.overall().unresolvedFailureCount()).isZero();
    });
  }

  private PracticeCodeReviewCommitService commitService() throws Exception {
    return new PracticeCodeReviewCommitService(
        new MyBatisPracticeCodeReviewRepository(
            sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml").getMapper(PracticeCodeReviewMapper.class),
            objectMapper),
        new PostgresQueuePublisher(objectMapper, queueRepository(), new PersistentQueueProperties()));
  }

  private void enqueueFiveFormalReviews(long userId, long sessionId, long tagId) throws Exception {
    PracticeCodeReviewCommitService commitService = commitService();
    for (int index = 0; index < LearnerMemoryCodeReviewConsumerConstants.BATCH_SIZE; index++) {
      long messageId = insertUserMessage(userId);
      PracticeCodeReviewCommitResult result = transactionTemplate().execute(
          status -> commitService.commit(draft(userId, sessionId, messageId, tagId)));
      assertThat(result.created()).isTrue();
      assertThat(result.queueMessageId()).isPositive();
    }
  }

  private QueueDispatcher dispatcher(AgentRuntime runtime) throws Exception {
    LearnerMemoryCodeReviewFactRepository facts = new MyBatisLearnerMemoryCodeReviewFactRepository(
        sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml").getMapper(PracticeCodeReviewMapper.class),
        objectMapper);
    LearnerMemoryCodeReviewBatchConsumer consumer = new LearnerMemoryCodeReviewBatchConsumer(
        objectMapper, facts, updateService(facts, runtime));
    MyBatisQueueMessageRepository queueRepository = queueRepository();
    return new QueueDispatcher(
        new QueueConsumerRegistry(List.of(), List.of(consumer)),
        queueRepository,
        new QueueDequeueService(queueRepository, transactionTemplate()));
  }

  private LearnerMemoryCodeReviewUpdateService updateService(
      LearnerMemoryCodeReviewFactRepository facts,
      AgentRuntime runtime
  ) throws Exception {
    PracticeCodeReviewMapper practiceMapper = sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml")
        .getMapper(PracticeCodeReviewMapper.class);
    LearnerMemoryMapper memoryMapper = sqlSessionTemplate("mapper/profile/LearnerMemoryMapper.xml")
        .getMapper(LearnerMemoryMapper.class);
    MyBatisLearnerMemoryClaimRepository claims = new MyBatisLearnerMemoryClaimRepository(memoryMapper);
    MyBatisLearnerMemoryEvidenceRepository evidence = new MyBatisLearnerMemoryEvidenceRepository(memoryMapper);
    LearnerMemoryUpdateRunRepository runs = new MyBatisLearnerMemoryUpdateRunRepository(memoryMapper);
    LearnerMemoryClaimSnapshotFactory snapshots = new LearnerMemoryClaimSnapshotFactory();
    LearnerMemoryUpdateRunLifecycleService lifecycle = new LearnerMemoryUpdateRunLifecycleService(runs, transactionTemplate());
    LearnerMemoryAtomicApplyService atomicApply = new LearnerMemoryAtomicApplyService(
        claims,
        evidence,
        runs,
        new LearnerMemoryClaimTextHasher(),
        snapshots,
        new LearnerMemoryEvidenceValidator(),
        new LearnerMemoryEvidenceGradeCalculator(),
        lifecycle,
        transactionTemplate());
    CodeReviewHistoryRepository history = new MyBatisCodeReviewHistoryRepository(practiceMapper, objectMapper);
    return new LearnerMemoryCodeReviewUpdateService(
        facts,
        history,
        new LearnerMemoryClaimQueryService(claims, snapshots),
        evidence,
        runs,
        atomicApply,
        lifecycle,
        runtime,
        new LearnerMemoryCodeReviewStructuredOutputMapper(),
        1);
  }

  private MyBatisQueueMessageRepository queueRepository() throws Exception {
    return new MyBatisQueueMessageRepository(
        sqlSessionTemplate("mapper/queue/QueueMessageMapper.xml").getMapper(QueueMessageMapper.class));
  }

  private PracticeCodeReviewDraft draft(long userId, long sessionId, long messageId, long tagId) {
    return draft(userId, sessionId, messageId, tagId, "two-sum", true);
  }

  private PracticeCodeReviewDraft draft(
      long userId,
      long sessionId,
      long messageId,
      long tagId,
      String problemSlug,
      boolean passed
  ) {
    PracticeCodeReviewScore score = passed
        ? new PracticeCodeReviewScore(
            new BigDecimal("4"), new BigDecimal("2"), new BigDecimal("2"), BigDecimal.ONE, BigDecimal.ONE,
            new BigDecimal("10"))
        : new PracticeCodeReviewScore(BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
            new BigDecimal("5"));
    return new PracticeCodeReviewDraft(
        userId, 1, 1, problemSlug, sessionId, messageId, null, null,
        "class Solution {}", "class Solution {}", "java", List.of(), "",
        score,
        passed, List.of("边界条件遗漏"), List.of("补充边界测试"), "OK", List.of(tagId));
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
        taskId, turnId, UUID.randomUUID().toString(), "code-review-memory-" + UUID.randomUUID());
  }

  private JsonNode decisions(LearnerMemoryCodeReviewUpdateAgentInput input) {
    long lastReviewId = input.scopeReviews().get(input.scopeReviews().size() - 1).reviewId();
    LearnerMemoryClaimScope tagScope = input.allowedScopes().stream()
        .filter(scope -> scope.kind() == LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT)
        .findFirst().orElseThrow();
    ObjectNode root = objectMapper.createObjectNode();
    ArrayNode operations = root.putArray(LearnerMemoryCodeReviewJsonSchema.OPERATIONS);
    ObjectNode tag = operations.addObject();
    tag.put(LearnerMemoryCodeReviewJsonSchema.ACTION, "ADD");
    tag.putObject(LearnerMemoryCodeReviewJsonSchema.SCOPE)
        .put(LearnerMemoryCodeReviewJsonSchema.KIND, "TAG_ASSESSMENT")
        .put(LearnerMemoryCodeReviewJsonSchema.DIMENSION, "TAG_MASTERY")
        .put(LearnerMemoryCodeReviewJsonSchema.TAG_ID, tagScope.tagId());
    tag.put(LearnerMemoryCodeReviewJsonSchema.CLAIM_TEXT, "本窗口的数组题当前提交已通过，并保留边界检查习惯。");
    tag.put(LearnerMemoryCodeReviewJsonSchema.OBSERVATION_TYPE, "CURRENT_STRENGTH");
    tag.put(LearnerMemoryCodeReviewJsonSchema.PATTERN, "SINGLE_REVIEW");
    tag.put(LearnerMemoryCodeReviewJsonSchema.REASON, "当前 Review 包含数组标签和边界问题。");
    tag.putArray(LearnerMemoryCodeReviewJsonSchema.REVIEW_EVIDENCE)
        .addObject().put(LearnerMemoryCodeReviewJsonSchema.REVIEW_ID, lastReviewId)
        .put(LearnerMemoryCodeReviewJsonSchema.ROLE, "RESOLVED");
    return root;
  }

  private final class FixedRuntime implements AgentRuntime {
    private final List<Long> agentRunIds;
    private final boolean rejectFirstEvidence;
    private final List<LearnerMemoryCodeReviewUpdateAgentInput> inputs = new ArrayList<>();
    private int calls;

    private FixedRuntime(long agentRunId) {
      this(List.of(agentRunId), false);
    }

    private FixedRuntime(List<Long> agentRunIds, boolean rejectFirstEvidence) {
      this.agentRunIds = List.copyOf(agentRunIds);
      this.rejectFirstEvidence = rejectFirstEvidence;
    }

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      calls++;
      if (calls > agentRunIds.size()) {
        throw new IllegalStateException("Fixed runtime has no response for the invocation");
      }
      LearnerMemoryCodeReviewUpdateAgentInput input = (LearnerMemoryCodeReviewUpdateAgentInput) invocation.input();
      inputs.add(input);
      JsonNode output = decisions(input);
      if (rejectFirstEvidence && calls == 1) {
        ((ObjectNode) output.path(LearnerMemoryCodeReviewJsonSchema.OPERATIONS).get(0))
            .put(LearnerMemoryCodeReviewJsonSchema.PATTERN, "CROSS_PROBLEM_RECURRENCE");
      }
      return new AgentRunResult(
          1,
          LlmFinishReason.STOP,
          new AgentOutput("", output, LearnerMemoryCodeReviewJsonSchema.SCHEMA_NAME,
              LearnerMemoryCodeReviewConsumerConstants.SCHEMA_VERSION, Map.of()),
          Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, agentRunIds.get(calls - 1)));
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException("stream not used");
    }
  }

  private record SampleReview(String problemSlug, boolean passed) {
  }
}
