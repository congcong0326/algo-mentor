package org.congcong.algomentor.api.practice;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.repository.MyBatisPracticeSubmissionHistoryToolRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

class PracticeSubmissionHistoryToolRepositoryIT extends PostgresIntegrationTestSupport {

  @Test
  void aggregatesCrossPlanHistoryUsesStableKeysetAndNeverReadsAnotherUsersReview() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long otherUserId = insertUser();
    long firstSession = insertPracticeSession(userId, 10L, "two-sum");
    long secondSession = insertPracticeSession(userId, 20L, "two-sum");
    long otherSession = insertPracticeSession(otherUserId, 30L, "two-sum");
    long earliest = insertReview(userId, firstSession, 10L, "two-sum", 1, "2026-08-01T00:00:00Z", false, "first-code");
    long sameTimeFirst = insertReview(userId, secondSession, 20L, "two-sum", 1, "2026-08-03T00:00:00Z", false, "second-code");
    long sameTimeLatest = insertReview(userId, firstSession, 10L, "two-sum", 2, "2026-08-03T00:00:00Z", true, "latest-code");
    long otherReview = insertReview(otherUserId, otherSession, 30L, "two-sum", 1, "2026-08-04T00:00:00Z", true, "other-code");
    PracticeCodeReviewMapper mapper = sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml")
        .getMapper(PracticeCodeReviewMapper.class);
    MyBatisPracticeSubmissionHistoryToolRepository repository = new MyBatisPracticeSubmissionHistoryToolRepository(
        mapper, new ObjectMapper());

    var overview = repository.findOverview(userId, "two-sum").orElseThrow();
    assertThat(overview.formalSubmissionCount()).isEqualTo(3);
    assertThat(overview.passedSubmissionCount()).isEqualTo(1);
    assertThat(overview.firstSubmittedAt()).isEqualTo(Instant.parse("2026-08-01T00:00:00Z"));
    assertThat(overview.latestSubmission().reviewId()).isEqualTo(sameTimeLatest);

    var firstPage = repository.findSubmissions(userId, "two-sum", null, null, 2);
    assertThat(firstPage.hasMore()).isTrue();
    assertThat(firstPage.submissions()).extracting(review -> review.reviewId())
        .containsExactly(sameTimeLatest, sameTimeFirst);
    var last = firstPage.submissions().get(1);
    var secondPage = repository.findSubmissions(userId, "two-sum", last.submittedAt(), last.reviewId(), 2);
    assertThat(secondPage.hasMore()).isFalse();
    assertThat(secondPage.submissions()).extracting(review -> review.reviewId()).containsExactly(earliest);

    assertThat(repository.findSubmissionDetail(userId, "two-sum", sameTimeLatest))
        .hasValueSatisfying(detail -> assertThat(detail.normalizedCode()).isEqualTo("latest-code"));
    assertThat(repository.findSubmissionDetail(userId, "wrong-problem", sameTimeLatest)).isEmpty();
    assertThat(repository.findSubmissionDetail(otherUserId, "two-sum", sameTimeLatest)).isEmpty();
    assertThat(repository.findSubmissionDetail(userId, "two-sum", otherReview)).isEmpty();
  }

  private long insertPracticeSession(long userId, long planId, String problemSlug) throws Exception {
    return queryLong(
        """
        INSERT INTO practice_session (user_id, plan_id, phase_index, problem_slug, status, locale)
        VALUES (?, ?, 1, ?, 'ACTIVE', 'zh-CN')
        RETURNING id
        """,
        userId, planId, problemSlug);
  }

  private long insertReview(
      long userId,
      long sessionId,
      long planId,
      String problemSlug,
      int versionNo,
      String createdAt,
      boolean passed,
      String code
  ) throws Exception {
    long messageId = insertUserMessage(userId);
    return queryLong(
        """
        INSERT INTO practice_code_review (
          user_id, plan_id, phase_index, problem_slug, practice_session_id, version_no, user_message_id,
          raw_code, normalized_code, language, content_locale, detection_evidence_json, context_summary,
          total_score, correctness_score, complexity_score, edge_case_score, code_quality_score, problem_fit_score,
          passed, deduction_reasons_json, improvement_suggestions_json, review_markdown, review_history_summary,
          created_at
        )
        VALUES (?, ?, 1, ?, ?, ?, ?, ?, ?, 'JAVA', 'zh-CN',
          '[]'::jsonb, '', 8, 3, 2, 1, 1, 1, ?, jsonb_build_array('deduction'),
          jsonb_build_array('suggestion'), 'review markdown', '历史摘要', ?::timestamptz)
        RETURNING id
        """,
        userId, planId, problemSlug, sessionId, versionNo, messageId, code, code, passed, createdAt);
  }
}
