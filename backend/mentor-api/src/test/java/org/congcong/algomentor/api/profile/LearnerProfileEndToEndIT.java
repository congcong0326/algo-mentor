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
import org.congcong.algomentor.api.practice.service.MyBatisTrustedProblemTagCatalog;
import org.congcong.algomentor.api.problem.mapper.ProblemTagMapper;
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
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitResult;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileContentPolicy;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileEntry;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileQueryService;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileUpdateService;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerProfilePolicyResolver;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerProfileRecallService;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerProfileRecallSnapshot;
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

class LearnerProfileEndToEndIT extends PostgresIntegrationTestSupport {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void commitsFiveFormalReviewsThenDispatchesOneProfileBatchAndRecallsTheActiveEntries() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long tagId = insertCatalog("array", "Array", "数组", true);
    insertProblem("two-sum", 1, List.of(), List.of(), List.of());
    assignTag("two-sum", tagId, 0);
    long sessionId = insertPracticeSession(userId, "two-sum");
    PracticeCodeReviewCommitService commitService = commitService();

    for (int index = 0; index < CodeReviewProfileConsumerConstants.BATCH_SIZE; index++) {
      long messageId = insertUserMessage(userId);
      PracticeCodeReviewCommitResult result = transactionTemplate().execute(
          status -> commitService.commit(draft(userId, sessionId, messageId, tagId)));
      assertThat(result.created()).isTrue();
      assertThat(result.queueMessageId()).isPositive();
    }

    assertThat(queryLong("SELECT COUNT(*) FROM practice_code_review")).isEqualTo(5L);
    assertThat(queryLong("SELECT COUNT(*) FROM practice_code_review_tag")).isEqualTo(5L);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE topic = ? AND status = 'PENDING'",
        CodeReviewProfileQueueContracts.TOPIC)).isEqualTo(5L);

    FixedCompletionGateway gateway = new FixedCompletionGateway(decisions(tagId));
    QueueDispatcher dispatcher = dispatcher(gateway);
    assertThat(dispatcher.dispatchRound(CodeReviewProfileQueueContracts.TOPIC))
        .containsExactly(QueueDispatchOutcome.DISPATCHED);

    assertThat(gateway.calls).isEqualTo(1);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE topic = ? AND status = 'SUCCEEDED'",
        CodeReviewProfileQueueContracts.TOPIC)).isEqualTo(5L);
    assertThat(queryLong("SELECT COUNT(*) FROM learner_profile_entry WHERE status = 'ACTIVE'")).isEqualTo(3L);
    assertThat(queryLong("""
        SELECT COUNT(*) FROM learner_profile_entry
        WHERE user_id = ? AND entry_kind = 'TAG_ASSESSMENT' AND tag_id = ? AND status = 'ACTIVE'
        """, userId, tagId)).isEqualTo(1L);

    LearnerProfileRecallSnapshot snapshot = recallService().recall(userId, "PRACTICE_CHAT", "two-sum");
    assertThat(snapshot.generalObservations()).extracting(LearnerProfileEntry::contentText)
        .containsExactly("优先在编码前明确解题路径。", "继续在提交前检查边界条件。");
    assertThat(snapshot.currentProblemTagAssessments()).hasSize(1);
    assertThat(snapshot.currentProblemTagAssessments().get(0).entry().contentText())
        .isEqualTo("数组题的边界条件检查仍需加强。");
  }

  private PracticeCodeReviewCommitService commitService() throws Exception {
    return new PracticeCodeReviewCommitService(
        new MyBatisPracticeCodeReviewRepository(
            sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml").getMapper(PracticeCodeReviewMapper.class),
            objectMapper),
        new PostgresQueuePublisher(objectMapper, queueRepository(), new PersistentQueueProperties()));
  }

  private QueueDispatcher dispatcher(FixedCompletionGateway gateway) throws Exception {
    CodeReviewProfileFactRepository factRepository = factRepository();
    MyBatisLearnerProfileRepository profileRepository = new MyBatisLearnerProfileRepository(
        sqlSessionTemplate("mapper/profile/LearnerProfileMapper.xml").getMapper(LearnerProfileMapper.class));
    CodeReviewProfileUpdateService updateService = new CodeReviewProfileUpdateService(
        factRepository,
        new LearnerProfileQueryService(profileRepository),
        new LearnerProfileUpdateService(profileRepository, new LearnerProfileContentPolicy(4000), transactionTemplate()),
        gateway,
        new CodeReviewProfilePromptBuilder(),
        new CodeReviewProfileStructuredOutputMapper(),
        1);
    CodeReviewProfileBatchConsumer consumer = new CodeReviewProfileBatchConsumer(objectMapper, factRepository, updateService);
    MyBatisQueueMessageRepository queueRepository = queueRepository();
    return new QueueDispatcher(
        new QueueConsumerRegistry(List.of(), List.of(consumer)),
        queueRepository,
        new QueueDequeueService(queueRepository, transactionTemplate()));
  }

  private LearnerProfileRecallService recallService() throws Exception {
    var sessionTemplate = sqlSessionTemplate(
        "mapper/profile/LearnerProfileMapper.xml",
        "mapper/problem/ProblemTagMapper.xml");
    return new LearnerProfileRecallService(
        new LearnerProfilePolicyResolver(true, 800),
        new LearnerProfileQueryService(
            new MyBatisLearnerProfileRepository(sessionTemplate.getMapper(LearnerProfileMapper.class))),
        new MyBatisTrustedProblemTagCatalog(sessionTemplate.getMapper(ProblemTagMapper.class)));
  }

  private CodeReviewProfileFactRepository factRepository() throws Exception {
    return new MyBatisCodeReviewProfileFactRepository(
        sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml").getMapper(PracticeCodeReviewMapper.class),
        objectMapper);
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
        .put(CodeReviewProfileJsonSchema.CONTENT, "优先在编码前明确解题路径。")
        .put(CodeReviewProfileJsonSchema.REASON, "正式 Review 显示实现前规划不足。");
    general.addObject()
        .put(CodeReviewProfileJsonSchema.DIMENSION, "IMPLEMENTATION_AND_ERROR_PATTERN")
        .put(CodeReviewProfileJsonSchema.ACTION, "REPLACE")
        .put(CodeReviewProfileJsonSchema.CONTENT, "继续在提交前检查边界条件。")
        .put(CodeReviewProfileJsonSchema.REASON, "正式 Review 提到了边界遗漏。");
    root.putArray(CodeReviewProfileJsonSchema.TAG_ASSESSMENTS).addObject()
        .put(CodeReviewProfileJsonSchema.TAG_ID, tagId)
        .put(CodeReviewProfileJsonSchema.ACTION, "REPLACE")
        .put(CodeReviewProfileJsonSchema.CONTENT, "数组题的边界条件检查仍需加强。")
        .put(CodeReviewProfileJsonSchema.REASON, "同一标签的 Review 有稳定改进建议。");
    return root;
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
