package org.congcong.algomentor.api.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.repository.MyBatisPracticeCodeReviewRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitResult;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewCommitService;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.congcong.algomentor.mentor.application.profile.review.CodeReviewProfileQueueContracts;
import org.congcong.algomentor.queue.config.PersistentQueueProperties;
import org.congcong.algomentor.queue.postgres.MyBatisQueueMessageRepository;
import org.congcong.algomentor.queue.postgres.QueueMessageMapper;
import org.congcong.algomentor.queue.publisher.PostgresQueuePublisher;
import org.congcong.algomentor.queue.publisher.QueuePublisher;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewProfilePublishIT extends PostgresIntegrationTestSupport {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void commitsReviewTagsAndMinimalQueueEventAtomicallyAndOnlyOnceForConcurrentReplay() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long sessionId = insertPracticeSession(userId, "two-sum");
    long firstMessageId = insertUserMessage(userId);
    long arrayTagId = insertCatalog("array", "Array", "数组", true);
    long hashTagId = insertCatalog("hash-table", "Hash Table", "哈希表", true);
    PracticeCodeReviewCommitService service = commitService();

    PracticeCodeReviewCommitResult created = transactionTemplate().execute(
        status -> service.commit(draft(userId, sessionId, firstMessageId, List.of(arrayTagId, hashTagId))));

    assertThat(created.created()).isTrue();
    assertThat(queryLong("SELECT COUNT(*) FROM practice_code_review WHERE id = ?", created.review().id())).isEqualTo(1L);
    assertThat(queryLong("SELECT COUNT(*) FROM practice_code_review_tag WHERE review_id = ?", created.review().id())).isEqualTo(2L);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE id = ? AND status = 'PENDING'", created.queueMessageId()))
        .isEqualTo(1L);
    assertThat(queryString("SELECT topic FROM queue_message WHERE id = ?", created.queueMessageId()))
        .isEqualTo(CodeReviewProfileQueueContracts.TOPIC);
    assertThat(queryString("SELECT message_key FROM queue_message WHERE id = ?", created.queueMessageId()))
        .isEqualTo(Long.toString(userId));
    JsonNode payload = objectMapper.readTree(queryString("SELECT message_value FROM queue_message WHERE id = ?", created.queueMessageId()));
    assertThat(payload.fieldNames()).toIterable().containsExactly(CodeReviewProfileQueueContracts.JSON_REVIEW_ID);
    assertThat(payload.path(CodeReviewProfileQueueContracts.JSON_REVIEW_ID).asLong()).isEqualTo(created.review().id());

    long concurrentMessageId = insertUserMessage(userId);
    CompletableFuture<PracticeCodeReviewCommitResult> first = CompletableFuture.supplyAsync(() ->
        transactionTemplate().execute(status -> service.commit(draft(userId, sessionId, concurrentMessageId, List.of(arrayTagId)))));
    CompletableFuture<PracticeCodeReviewCommitResult> second = CompletableFuture.supplyAsync(() ->
        transactionTemplate().execute(status -> service.commit(draft(userId, sessionId, concurrentMessageId, List.of(hashTagId)))));
    List<PracticeCodeReviewCommitResult> concurrent = List.of(
        first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));

    assertThat(concurrent).filteredOn(PracticeCodeReviewCommitResult::created).hasSize(1);
    assertThat(queryLong("SELECT COUNT(*) FROM practice_code_review WHERE user_message_id = ?", concurrentMessageId)).isEqualTo(1L);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE topic = ?", CodeReviewProfileQueueContracts.TOPIC)).isEqualTo(2L);
  }

  @Test
  void queueFailureRollsBackReviewAndTags() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long sessionId = insertPracticeSession(userId, "two-sum");
    long messageId = insertUserMessage(userId);
    long tagId = insertCatalog("array", "Array", "数组", true);
    MyBatisPracticeCodeReviewRepository repository = reviewRepository();
    QueuePublisher failingPublisher = (topic, key, payload) -> {
      throw new IllegalStateException("queue storage unavailable");
    };
    PracticeCodeReviewCommitService service = new PracticeCodeReviewCommitService(repository, failingPublisher);

    assertThatThrownBy(() -> transactionTemplate().execute(
        status -> service.commit(draft(userId, sessionId, messageId, List.of(tagId)))))
        .isInstanceOf(IllegalStateException.class);

    assertThat(count("practice_code_review")).isZero();
    assertThat(count("practice_code_review_tag")).isZero();
    assertThat(count("queue_message")).isZero();
  }

  private PracticeCodeReviewCommitService commitService() throws Exception {
    return new PracticeCodeReviewCommitService(
        reviewRepository(),
        new PostgresQueuePublisher(objectMapper, queueRepository(), new PersistentQueueProperties()));
  }

  private MyBatisPracticeCodeReviewRepository reviewRepository() throws Exception {
    return new MyBatisPracticeCodeReviewRepository(
        sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml").getMapper(PracticeCodeReviewMapper.class),
        objectMapper);
  }

  private MyBatisQueueMessageRepository queueRepository() throws Exception {
    return new MyBatisQueueMessageRepository(
        sqlSessionTemplate("mapper/queue/QueueMessageMapper.xml").getMapper(QueueMessageMapper.class));
  }

  private PracticeCodeReviewDraft draft(long userId, long sessionId, long messageId, List<Long> tagIds) {
    return new PracticeCodeReviewDraft(
        userId, 1, 1, "two-sum", sessionId, messageId, null, null,
        "class Solution {}", "class Solution {}", "java", List.of(), "",
        new PracticeCodeReviewScore(
            new BigDecimal("4"), new BigDecimal("2"), new BigDecimal("2"), BigDecimal.ONE, BigDecimal.ONE,
            new BigDecimal("10")),
        true, List.of(), List.of(), "OK", tagIds);
  }
}
