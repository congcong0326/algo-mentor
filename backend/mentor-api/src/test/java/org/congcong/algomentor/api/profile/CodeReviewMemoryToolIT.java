package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.congcong.algomentor.agent.core.AgentExecutionContext;
import org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisCodeReviewHistoryRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistory;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewHistoryRepository;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification;
import org.congcong.algomentor.mentor.application.profile.review.history.SubmissionVersionDiffService;
import org.congcong.algomentor.mentor.application.profile.review.history.ReviewTrajectoryService;
import org.congcong.algomentor.mentor.application.profile.tool.CompareSubmissionVersionsAgentTool;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryAgentToolContracts;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryRunScopeRegistry;
import org.junit.jupiter.api.Test;

class CodeReviewMemoryToolIT extends PostgresIntegrationTestSupport {

  private final Map<String, Long> sessionIds = new HashMap<>();

  @Test
  void readsOnlyUserScopedHistoryAndFeedsAConstrainedDiffTool() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long otherUserId = insertUser();
    long tagId = insertCatalog("array", "Array", "数组", true);
    long first = insertReview(userId, "two-sum", 1, "return 0;", Instant.parse("2026-01-01T00:00:00Z"), tagId);
    long second = insertReview(userId, "two-sum", 2, "return 1;", Instant.parse("2026-01-02T00:00:00Z"), tagId);
    long foreign = insertReview(otherUserId, "two-sum", 1, "return 9;", Instant.parse("2026-01-03T00:00:00Z"), tagId);

    CodeReviewHistoryRepository repository = repository();
    List<CodeReviewHistory> history = repository.findLatestForProblem(userId, "two-sum", 5);
    List<CodeReviewVerification> verification = repository.verifyReviews(userId, List.of(first, second, foreign));

    assertThat(history).extracting(CodeReviewHistory::reviewId).containsExactly(first, second);
    assertThat(history).extracting(CodeReviewHistory::versionNo).containsExactly(1, 2);
    assertThat(repository.findEvidenceDetail(userId, first)).isPresent();
    assertThat(repository.findEvidenceDetail(userId, foreign)).isEmpty();
    assertThat(repository.findNormalizedSubmissionVersions(userId, List.of(first, second)))
        .extracting(version -> version.reviewId()).containsExactly(first, second);
    assertThat(repository.findNormalizedSubmissionVersions(userId, List.of(first, foreign))).extracting(version -> version.reviewId())
        .containsExactly(first);
    assertThat(verification).extracting(CodeReviewVerification::reviewId).containsExactly(first, second);
    assertThat(verification.get(0).affectedTagIds()).containsExactly(tagId);

    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryRunScopeRegistry.ScopeLease lease = registry.openUpdateScope(userId, verification);
    CompareSubmissionVersionsAgentTool tool = new CompareSubmissionVersionsAgentTool(
        registry, repository, new SubmissionVersionDiffService(), new ReviewTrajectoryService());
    AgentExecutionContext context = new AgentExecutionContext(
        "run", 1, registry.initialRequestMetadata(lease), false);

    JsonNode result = tool.execute(JsonNodeFactory.instance.objectNode()
        .put(LearnerMemoryAgentToolContracts.ARGUMENT_FROM_REVIEW_ID, first)
        .put(LearnerMemoryAgentToolContracts.ARGUMENT_TO_REVIEW_ID, second), context);

    assertThat(result.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_STATUS).asText())
        .isEqualTo(LearnerMemoryAgentToolContracts.STATUS_OK);
    assertThat(result.path(LearnerMemoryAgentToolContracts.RESULT_FIELD_UNIFIED_DIFF).asText())
        .startsWith("--- submission-v1-" + first + "\n+++ submission-v2-" + second);
    assertThat(result.toString()).doesNotContain("rawCode", "normalizedCode", "reviewMarkdown");
  }

  private CodeReviewHistoryRepository repository() throws Exception {
    return new MyBatisCodeReviewHistoryRepository(
        sqlSessionTemplate("mapper/practice/PracticeCodeReviewMapper.xml").getMapper(PracticeCodeReviewMapper.class),
        new ObjectMapper());
  }

  private long insertReview(
      long userId,
      String problemSlug,
      int versionNo,
      String normalizedCode,
      Instant createdAt,
      long tagId
  ) throws Exception {
    String key = userId + ":" + problemSlug;
    Long existingSessionId = sessionIds.get(key);
    long sessionId = existingSessionId == null ? insertPracticeSession(userId, problemSlug) : existingSessionId;
    sessionIds.put(key, sessionId);
    long reviewId = queryLong(
        """
        INSERT INTO practice_code_review (
          user_id, plan_id, phase_index, problem_slug, practice_session_id, version_no, user_message_id,
          raw_code, normalized_code, language, detection_evidence_json, context_summary,
          total_score, correctness_score, complexity_score, edge_case_score, code_quality_score, problem_fit_score,
          passed, deduction_reasons_json, improvement_suggestions_json, review_markdown, created_at
        ) VALUES (?, 1, 1, ?, ?, ?, ?, 'private raw code', ?, 'java',
          '[{"type":"complexity","value":"nested loop"}]'::jsonb, 'private context',
          8, 3, 2, 1, 1, 1, true, '["deduction"]'::jsonb, '["improvement"]'::jsonb, 'private markdown', ?)
        RETURNING id
        """,
        userId, problemSlug, sessionId, versionNo, insertUserMessage(userId), normalizedCode,
        java.sql.Timestamp.from(createdAt));
    execute("INSERT INTO practice_code_review_tag (review_id, tag_id) VALUES (?, ?)", reviewId, tagId);
    return reviewId;
  }
}
