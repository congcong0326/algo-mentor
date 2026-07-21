package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.ai.governance.completion.AiCompletionContext;
import org.congcong.algomentor.ai.governance.completion.AiCompletionGateway;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.repository.MyBatisPracticeCodeReviewRepository;
import org.congcong.algomentor.api.profile.mapper.LearnerProfileMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisCodeReviewProfileFactRepository;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerProfileRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.llm.core.model.LlmModelId;
import org.congcong.algomentor.llm.core.provider.LlmProviderId;
import org.congcong.algomentor.llm.core.request.LlmCompletionRequest;
import org.congcong.algomentor.llm.core.request.LlmMessage;
import org.congcong.algomentor.llm.core.response.LlmCompletionResult;
import org.congcong.algomentor.llm.core.response.LlmFinishReason;
import org.congcong.algomentor.llm.core.response.LlmUsage;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileContentPolicy;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileBatchConsumer;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileConsumerConstants;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileFactRepository;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileJsonSchema;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfilePromptBuilder;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileQueueContracts;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileStructuredOutputMapper;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileUpdateService;
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
  void marksTheBatchSucceededWhenTheProfileCallbackFailsAndNeverReplaysIt() throws Exception {
    migrateLatest();
    Fixture fixture = fixture();
    publishReviews(fixture, CodeReviewProfileConsumerConstants.BATCH_SIZE);
    FailingCompletionGateway gateway = new FailingCompletionGateway();
    QueueDispatcher dispatcher = dispatcher(gateway, fixture.tagId());

    assertThat(dispatcher.dispatchRound(CodeReviewProfileQueueContracts.TOPIC))
        .containsExactly(QueueDispatchOutcome.DISPATCHED);
    assertThat(gateway.calls).isEqualTo(1);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'SUCCEEDED'")).isEqualTo(5L);
    assertThat(count("learner_profile_entry")).isZero();

    assertThat(dispatcher.dispatchRound(CodeReviewProfileQueueContracts.TOPIC))
        .containsExactly(QueueDispatchOutcome.NO_ELIGIBLE_KEY);
    assertThat(gateway.calls).isEqualTo(1);
  }

  @Test
  void retainsPendingMessagesUntilANewWorkerCanFinishTheFullBatch() throws Exception {
    migrateLatest();
    Fixture fixture = fixture();
    publishReviews(fixture, CodeReviewProfileConsumerConstants.BATCH_SIZE - 1);
    FixedCompletionGateway gateway = new FixedCompletionGateway(decisions(fixture.tagId()));

    assertThat(dispatcher(gateway, fixture.tagId()).dispatchRound(CodeReviewProfileQueueContracts.TOPIC))
        .containsExactly(QueueDispatchOutcome.NO_ELIGIBLE_KEY);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'PENDING'")).isEqualTo(4L);
    assertThat(gateway.calls).isZero();

    publishReviews(fixture, 1);
    QueueDispatcher restartedDispatcher = dispatcher(gateway, fixture.tagId());
    assertThat(restartedDispatcher.dispatchRound(CodeReviewProfileQueueContracts.TOPIC))
        .containsExactly(QueueDispatchOutcome.DISPATCHED);
    assertThat(gateway.calls).isEqualTo(1);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE status = 'SUCCEEDED'")).isEqualTo(5L);
    assertThat(queryLong("SELECT COUNT(*) FROM learner_profile_entry WHERE status = 'ACTIVE'")).isEqualTo(3L);
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

  private QueueDispatcher dispatcher(AiCompletionGateway gateway, long tagId) throws Exception {
    CodeReviewProfileFactRepository factRepository = new MyBatisCodeReviewProfileFactRepository(
        sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml").getMapper(PracticeCodeReviewMapper.class),
        objectMapper);
    MyBatisLearnerProfileRepository profileRepository = new MyBatisLearnerProfileRepository(
        sqlSessionTemplate("mapper/profile/LearnerProfileMapper.xml").getMapper(LearnerProfileMapper.class));
    CodeReviewProfileBatchConsumer consumer = new CodeReviewProfileBatchConsumer(
        objectMapper,
        factRepository,
        new CodeReviewProfileUpdateService(
            factRepository,
            new LearnerProfileQueryService(profileRepository),
            new LearnerProfileUpdateService(profileRepository, new LearnerProfileContentPolicy(4000), transactionTemplate()),
            gateway,
            new CodeReviewProfilePromptBuilder(),
            new CodeReviewProfileStructuredOutputMapper(),
            1));
    MyBatisQueueMessageRepository queueRepository = queueRepository();
    return new QueueDispatcher(
        new QueueConsumerRegistry(List.of(), List.of(consumer)),
        queueRepository,
        new QueueDequeueService(queueRepository, transactionTemplate()));
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

  private JsonNode decisions(long tagId) {
    ObjectNode root = objectMapper.createObjectNode();
    ArrayNode general = root.putArray(CodeReviewProfileJsonSchema.GENERAL_OBSERVATIONS);
    general.addObject()
        .put(CodeReviewProfileJsonSchema.DIMENSION, "PROBLEM_SOLVING_APPROACH")
        .put(CodeReviewProfileJsonSchema.ACTION, "REPLACE")
        .put(CodeReviewProfileJsonSchema.CONTENT, "先确认算法路径。")
        .put(CodeReviewProfileJsonSchema.REASON, "Review 观察。");
    general.addObject()
        .put(CodeReviewProfileJsonSchema.DIMENSION, "IMPLEMENTATION_AND_ERROR_PATTERN")
        .put(CodeReviewProfileJsonSchema.ACTION, "REPLACE")
        .put(CodeReviewProfileJsonSchema.CONTENT, "提交前复核边界。")
        .put(CodeReviewProfileJsonSchema.REASON, "Review 观察。");
    root.putArray(CodeReviewProfileJsonSchema.TAG_ASSESSMENTS).addObject()
        .put(CodeReviewProfileJsonSchema.TAG_ID, tagId)
        .put(CodeReviewProfileJsonSchema.ACTION, "REPLACE")
        .put(CodeReviewProfileJsonSchema.CONTENT, "数组边界仍需复核。")
        .put(CodeReviewProfileJsonSchema.REASON, "Review 观察。");
    return root;
  }

  private record Fixture(long userId, long tagId, long sessionId, PracticeCodeReviewCommitService commitService) {
  }

  private static final class FailingCompletionGateway implements AiCompletionGateway {
    private int calls;

    @Override
    public boolean isAllowed(AiCompletionContext context) {
      return true;
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request, AiCompletionContext context) {
      calls++;
      throw new IllegalStateException("test callback failure");
    }
  }

  private static final class FixedCompletionGateway implements AiCompletionGateway {
    private final JsonNode output;
    private int calls;

    private FixedCompletionGateway(JsonNode output) {
      this.output = output;
    }

    @Override
    public boolean isAllowed(AiCompletionContext context) {
      return true;
    }

    @Override
    public LlmCompletionResult complete(LlmCompletionRequest request, AiCompletionContext context) {
      calls++;
      return new LlmCompletionResult(
          LlmMessage.assistant("{}"), List.of(), output, LlmFinishReason.STOP, LlmUsage.empty(),
          LlmProviderId.of("test-provider"), LlmModelId.of("test-model"), Map.of());
    }
  }
}
