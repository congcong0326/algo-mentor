package org.congcong.algomentor.api.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.repository.MyBatisPracticeCodeReviewRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewDraft;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewSaveResult;
import org.congcong.algomentor.mentor.application.practice.PracticeCodeReviewScore;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewTagIT extends PostgresIntegrationTestSupport {

  @Test
  void savesReviewAndTrustedTagRelationsAtomicallyThenReusesTheOriginalRelations() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long sessionId = insertPracticeSession(userId, "two-sum");
    long messageId = insertUserMessage(userId);
    long array = insertCatalog("array", "Array", "数组", true);
    long hash = insertCatalog("hash-table", "Hash Table", "哈希表", true);
    MyBatisPracticeCodeReviewRepository repository = repository();

    PracticeCodeReviewSaveResult created = transactionTemplate().execute(
        status -> repository.saveResult(draft(userId, sessionId, messageId, List.of(hash, array))));
    PracticeCodeReviewSaveResult reused = transactionTemplate().execute(
        status -> repository.saveResult(draft(userId, sessionId, messageId, List.of(array))));

    assertThat(created.created()).isTrue();
    assertThat(created.review().affectedTagIds()).containsExactly(array, hash);
    assertThat(reused.created()).isFalse();
    assertThat(reused.review().affectedTagIds()).containsExactly(array, hash);
    assertThat(queryLong("SELECT COUNT(*) FROM practice_code_review_tag WHERE review_id = ?", created.review().id()))
        .isEqualTo(2L);
    execute("DELETE FROM practice_code_review WHERE id = ?", created.review().id());
    assertThat(count("practice_code_review_tag")).isZero();
  }

  @Test
  void rollsBackTheFormalReviewWhenTagAssociationFails() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long sessionId = insertPracticeSession(userId, "two-sum");
    long messageId = insertUserMessage(userId);
    MyBatisPracticeCodeReviewRepository repository = repository();

    assertThatThrownBy(() -> transactionTemplate().execute(
        status -> repository.saveResult(draft(userId, sessionId, messageId, List.of(999_999L)))))
        .isInstanceOf(Exception.class);
    assertThat(count("practice_code_review")).isZero();
    assertThat(count("practice_code_review_tag")).isZero();
  }

  private MyBatisPracticeCodeReviewRepository repository() throws Exception {
    return new MyBatisPracticeCodeReviewRepository(
        sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml").getMapper(PracticeCodeReviewMapper.class),
        new ObjectMapper());
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
