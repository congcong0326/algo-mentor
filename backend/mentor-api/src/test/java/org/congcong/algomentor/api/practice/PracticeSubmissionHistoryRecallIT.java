package org.congcong.algomentor.api.practice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.practice.repository.MyBatisPracticeCodeReviewHistoryRepository;
import org.congcong.algomentor.api.practice.repository.MyBatisPracticeSubmissionHistoryRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

class PracticeSubmissionHistoryRecallIT extends PostgresIntegrationTestSupport {

  @Test
  void readsCrossPlanHistoryByCreatedTimeAndKeepsOnlyEachProblemsLatestReviewForPromptIndexes() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long earlierSession = insertPracticeSession(userId, 10L, "two-sum");
    long laterSession = insertPracticeSession(userId, 20L, "two-sum");
    long otherSession = insertPracticeSession(userId, 30L, "subarray-sum-equals-k");

    long laterCreatedFirst = insertReview(
        userId, laterSession, 20L, "two-sum", 1, "2026-02-02T00:00:00Z", true,
        "当前版本通过。", "此前问题已修正。");
    long earlierCreatedSecond = insertReview(
        userId, earlierSession, 10L, "two-sum", 1, "2026-02-01T00:00:00Z", false,
        "遗漏空前缀初始化", "此前遗漏初始化。");
    long otherReview = insertReview(
        userId, otherSession, 30L, "subarray-sum-equals-k", 1, "2026-02-03T00:00:00Z", false,
        "哈希表计数错误", "当前提交仍有计数错误。");
    PracticeCodeReviewMapper mapper = sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml")
        .getMapper(PracticeCodeReviewMapper.class);
    MyBatisPracticeCodeReviewHistoryRepository historyRepository = new MyBatisPracticeCodeReviewHistoryRepository(mapper);
    MyBatisPracticeSubmissionHistoryRepository indexRepository = new MyBatisPracticeSubmissionHistoryRepository(mapper);

    assertThat(historyRepository.findRecentForProblem(userId, "two-sum", 4))
        .extracting(fact -> fact.reviewId())
        .containsExactly(earlierCreatedSecond, laterCreatedFirst);
    assertThat(historyRepository.findRecentForProblem(userId, "two-sum", 4))
        .extracting(fact -> fact.primaryFinding())
        .containsExactly("遗漏空前缀初始化", "当前版本通过。");
    assertThat(indexRepository.findRecentDistinctProblems(userId, 5))
        .extracting(problem -> problem.reviewId())
        .containsExactly(otherReview, laterCreatedFirst);
    assertThat(indexRepository.findRecentDistinctProblemsForSlugs(userId, List.of("two-sum"), 3))
        .extracting(problem -> problem.reviewId())
        .containsExactly(laterCreatedFirst);
    assertThatThrownBy(() -> insertReview(
        userId, laterSession, 20L, "two-sum", 2, "2026-02-04T00:00:00Z", true,
        "当前版本通过。", "x".repeat(201)))
        .isInstanceOf(Exception.class);
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
      String primaryFinding,
      String historySummary
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
        VALUES (?, ?, 1, ?, ?, ?, ?, 'class Solution {}', 'class Solution {}', 'java', 'zh-CN',
          '[]'::jsonb, '', 8, 3, 2, 1, 1, 1, ?, jsonb_build_array('', ?), '[]'::jsonb, 'review', ?, ?::timestamptz)
        RETURNING id
        """,
        userId, planId, problemSlug, sessionId, versionNo, messageId, passed, primaryFinding, historySummary,
        createdAt);
  }
}
