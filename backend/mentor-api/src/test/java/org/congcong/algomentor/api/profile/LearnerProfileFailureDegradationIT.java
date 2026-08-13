package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
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
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
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
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewUpdateService;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistoryRepository;
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

class LearnerProfileFailureDegradationIT extends PostgresIntegrationTestSupport {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void retainsTheQueueBatchForRetryWhenClaimCallbackFails() throws Exception {
    migrateLatest();
    Fixture fixture = fixture();
    publishReviews(fixture, LearnerMemoryCodeReviewConsumerConstants.BATCH_SIZE);
    FailingRuntime runtime = new FailingRuntime();
    QueueDispatcher dispatcher = dispatcher(runtime);

    assertThat(dispatcher.dispatchRound(LearnerMemoryCodeReviewQueueContracts.TOPIC))
        .containsExactly(QueueDispatchOutcome.CALLBACK_RETRY_SCHEDULED);
    assertThat(runtime.calls).isEqualTo(1);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'SUCCEEDED'")).isZero();
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'PENDING'")).isEqualTo(5L);
    assertThat(queryLong("SELECT COUNT(*) FROM learner_memory_update_run WHERE status = 'FAILED'")).isEqualTo(1L);
    assertThat(queryString("SELECT to_regclass('learner_profile_entry')::text")).isNull();

    execute("UPDATE queue_message SET available_at = NOW() WHERE topic = ?", LearnerMemoryCodeReviewQueueContracts.TOPIC);
    assertThat(dispatcher.dispatchRound(LearnerMemoryCodeReviewQueueContracts.TOPIC))
        .containsExactly(QueueDispatchOutcome.CALLBACK_RETRY_SCHEDULED);
    assertThat(runtime.calls).isEqualTo(2);
  }

  @Test
  void retainsPendingMessagesUntilANewWorkerCanFinishTheFullBatch() throws Exception {
    migrateLatest();
    Fixture fixture = fixture();
    publishReviews(fixture, LearnerMemoryCodeReviewConsumerConstants.BATCH_SIZE - 1);
    FixedRuntime runtime = new FixedRuntime(insertAgentRun(fixture.userId()));

    assertThat(dispatcher(runtime).dispatchRound(LearnerMemoryCodeReviewQueueContracts.TOPIC))
        .containsExactly(QueueDispatchOutcome.NO_ELIGIBLE_KEY);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'PENDING'")).isEqualTo(4L);
    assertThat(runtime.calls).isZero();

    publishReviews(fixture, 1);
    assertThat(dispatcher(runtime).dispatchRound(LearnerMemoryCodeReviewQueueContracts.TOPIC))
        .containsExactly(QueueDispatchOutcome.DISPATCHED);
    assertThat(runtime.calls).isEqualTo(1);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'SUCCEEDED'")).isEqualTo(5L);
    assertThat(queryLong("SELECT COUNT(*) FROM learner_memory_update_run WHERE status = 'NO_CHANGE'")).isEqualTo(1L);
  }

  private Fixture fixture() throws Exception {
    long userId = insertUser();
    long tagId = insertCatalog("array", "Array", "数组", true);
    long sessionId = insertPracticeSession(userId, "two-sum");
    return new Fixture(userId, tagId, sessionId, commitService());
  }

  private void publishReviews(Fixture fixture, int count) throws Exception {
    for (int index = 0; index < count; index++) {
      long messageId = insertUserMessage(fixture.userId());
      transactionTemplate().executeWithoutResult(
          status -> fixture.commitService().commit(draft(fixture.userId(), fixture.sessionId(), messageId, fixture.tagId())));
    }
  }

  private PracticeCodeReviewCommitService commitService() throws Exception {
    return new PracticeCodeReviewCommitService(
        new MyBatisPracticeCodeReviewRepository(
            sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml").getMapper(PracticeCodeReviewMapper.class),
            objectMapper),
        new PostgresQueuePublisher(objectMapper, queueRepository(), new PersistentQueueProperties()));
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
        new QueueDequeueService(queueRepository, transactionTemplate(), new PersistentQueueProperties().getConsumer()));
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
    return new PracticeCodeReviewDraft(
        userId, 1, 1, "two-sum", sessionId, messageId, null, null,
        "class Solution {}", "class Solution {}", "java", List.of(), "",
        new PracticeCodeReviewScore(
            new BigDecimal("4"), new BigDecimal("2"), new BigDecimal("2"), BigDecimal.ONE, BigDecimal.ONE,
            new BigDecimal("10")),
        true, List.of("边界条件遗漏"), List.of("补充边界测试"), "OK", List.of(tagId));
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
        taskId, turnId, UUID.randomUUID().toString(), "failure-degradation-" + UUID.randomUUID());
  }

  private record Fixture(long userId, long tagId, long sessionId, PracticeCodeReviewCommitService commitService) {
  }

  private static final class FailingRuntime implements AgentRuntime {
    private int calls;

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      calls++;
      throw new IllegalStateException("test callback failure");
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException("stream not used");
    }
  }

  private final class FixedRuntime implements AgentRuntime {
    private final long agentRunId;
    private int calls;

    private FixedRuntime(long agentRunId) {
      this.agentRunId = agentRunId;
    }

    @Override
    public AgentRunResult execute(AgentInvocation<?> invocation) {
      calls++;
      ObjectNode output = objectMapper.createObjectNode();
      output.putArray(LearnerMemoryCodeReviewJsonSchema.OPERATIONS);
      return new AgentRunResult(
          1,
          LlmFinishReason.STOP,
          new AgentOutput("", output, LearnerMemoryCodeReviewJsonSchema.SCHEMA_NAME,
              LearnerMemoryCodeReviewConsumerConstants.SCHEMA_VERSION, Map.of()),
          Map.of(AgentRuntimeMetadataKeys.RUN_DB_ID, agentRunId));
    }

    @Override
    public Flow.Publisher<AgentStreamEvent> stream(AgentInvocation<?> invocation) {
      throw new UnsupportedOperationException("stream not used");
    }
  }
}
