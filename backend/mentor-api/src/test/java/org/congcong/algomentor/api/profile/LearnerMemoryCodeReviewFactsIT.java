package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerMemoryCodeReviewFactRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewFact;
import org.congcong.algomentor.mentor.application.profile.review.LearnerMemoryCodeReviewFactRepository;
import org.junit.jupiter.api.Test;

class LearnerMemoryCodeReviewFactsIT extends PostgresIntegrationTestSupport {

  private final Map<String, Long> sessionIds = new HashMap<>();

  @Test
  void returnsCurrentBatchProblemsNewestVersionThenAtMostTenDistinctProblems() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long tagId = insertCatalog("array", "Array", "数组", true);
    long batchOldVersion = insertReview(userId, "batch-problem", 1, Instant.parse("2026-01-01T00:00:00Z"), tagId);
    long batchLatestVersion = insertReview(userId, "batch-problem", 2, Instant.parse("2026-01-02T00:00:00Z"), tagId);
    List<Long> otherReviews = new ArrayList<>();
    for (int index = 0; index < 11; index++) {
      otherReviews.add(insertReview(
          userId,
          "other-" + index,
          1,
          Instant.parse("2026-01-03T00:00:00Z").plusSeconds(index),
          tagId));
    }

    LearnerMemoryCodeReviewFactRepository repository = repository();
    List<LearnerMemoryCodeReviewFact> batchFacts = repository.findByReviewIds(userId, List.of(batchOldVersion));
    List<LearnerMemoryCodeReviewFact> currentBatchProblem = repository.findLatestForProblemSlugs(
        userId, batchFacts.stream().map(LearnerMemoryCodeReviewFact::problemSlug).toList());
    List<LearnerMemoryCodeReviewFact> supplement = repository.findRecentDistinctProblems(
        userId,
        currentBatchProblem.stream().map(LearnerMemoryCodeReviewFact::problemSlug).toList(),
        9);

    assertThat(batchFacts).extracting(LearnerMemoryCodeReviewFact::reviewId).containsExactly(batchOldVersion);
    assertThat(repository.findAllForUser(userId)).extracting(LearnerMemoryCodeReviewFact::reviewId)
        .containsExactly(batchOldVersion, batchLatestVersion, otherReviews.get(0), otherReviews.get(1), otherReviews.get(2),
            otherReviews.get(3), otherReviews.get(4), otherReviews.get(5), otherReviews.get(6), otherReviews.get(7),
            otherReviews.get(8), otherReviews.get(9), otherReviews.get(10));
    assertThat(currentBatchProblem).extracting(LearnerMemoryCodeReviewFact::reviewId).containsExactly(batchLatestVersion);
    assertThat(currentBatchProblem.get(0).affectedTagIds()).containsExactly(tagId);
    assertThat(supplement).hasSize(9);
    assertThat(supplement).extracting(LearnerMemoryCodeReviewFact::problemSlug)
        .doesNotContain("batch-problem").doesNotHaveDuplicates();
    assertThat(currentBatchProblem.size() + supplement.size()).isEqualTo(10);
    assertThat(otherReviews).hasSize(11);
  }

  @Test
  void selectsTheHighestVersionWhenReviewTimestampsAreEqual() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long tagId = insertCatalog("array", "Array", "数组", true);
    Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");
    long versionTwo = insertReview(userId, "same-time", 2, createdAt, tagId);
    insertReview(userId, "same-time", 1, createdAt, tagId);

    assertThat(repository().findLatestForProblemSlugs(userId, List.of("same-time")))
        .extracting(LearnerMemoryCodeReviewFact::reviewId)
        .containsExactly(versionTwo);
  }

  @Test
  void resolvesTheChineseProblemTitleForTheProfileUpdatePrompt() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long tagId = insertCatalog("array", "Array", "数组", true);
    insertProblem("two-sum", 1, List.of(), List.of(), List.of());
    execute("UPDATE problem SET title_en = ?, title_zh = ? WHERE slug = ?", "Two Sum", "两数之和", "two-sum");
    long reviewId = insertReview(userId, "two-sum", 1, Instant.parse("2026-01-01T00:00:00Z"), tagId);

    assertThat(repository().findByReviewIds(userId, List.of(reviewId)))
        .extracting(LearnerMemoryCodeReviewFact::problemTitle)
        .containsExactly("两数之和");
  }

  private LearnerMemoryCodeReviewFactRepository repository() throws Exception {
    return new MyBatisLearnerMemoryCodeReviewFactRepository(
        sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml").getMapper(PracticeCodeReviewMapper.class),
        new ObjectMapper());
  }

  private long insertReview(long userId, String problemSlug, int versionNo, Instant createdAt, long tagId) throws Exception {
    Long existingSessionId = sessionIds.get(problemSlug);
    long sessionId = existingSessionId == null ? insertPracticeSession(userId, problemSlug) : existingSessionId;
    sessionIds.put(problemSlug, sessionId);
    long messageId = insertUserMessage(userId);
    long reviewId = queryLong(
        """
        INSERT INTO practice_code_review (
          user_id, plan_id, phase_index, problem_slug, practice_session_id, version_no, user_message_id,
          raw_code, normalized_code, language, context_summary,
          total_score, correctness_score, complexity_score, edge_case_score, code_quality_score, problem_fit_score,
          passed, deduction_reasons_json, improvement_suggestions_json, review_markdown, created_at
        ) VALUES (?, 1, 1, ?, ?, ?, ?, 'private raw code', 'private normalized code', 'java', '',
          8, 3, 2, 1, 1, 1, true, '["deduction"]'::jsonb, '["improvement"]'::jsonb, 'private markdown', ?)
        RETURNING id
        """,
        userId, problemSlug, sessionId, versionNo, messageId, java.sql.Timestamp.from(createdAt));
    execute("INSERT INTO practice_code_review_tag (review_id, tag_id) VALUES (?, ?)", reviewId, tagId);
    return reviewId;
  }
}
