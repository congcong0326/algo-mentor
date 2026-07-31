package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.congcong.algomentor.api.profile.mapper.LearnerMemoryMapper;
import org.congcong.algomentor.api.profile.repository.MyBatisLearnerProfileDocumentProjectionRepository;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.congcong.algomentor.mentor.application.profile.document.LearnerProfileDocument;
import org.congcong.algomentor.mentor.application.profile.document.LearnerProfileDocumentProjector;
import org.congcong.algomentor.mentor.application.profile.document.LearnerProfileDocumentService;
import org.congcong.algomentor.mentor.application.profile.document.LearnerProfileStatementReferenceCodec;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemorySectionCatalog;
import org.junit.jupiter.api.Test;

class LearnerMemoryDocumentEndToEndIT extends PostgresIntegrationTestSupport {

  @Test
  void projectsCurrentUsersActiveClaimsWithBoundEvidenceAndRejectsRetiredRefs() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long otherUserId = insertUser();
    insertProblem("two-sum", 1, List.of(), List.of(), List.of());
    long runId = insertUpdateRun(userId, "DECLARED_FACT");
    long otherRunId = insertUpdateRun(otherUserId, "DECLARED_FACT");
    long messageId = insertUserMessage(userId);
    execute("UPDATE agent_message SET content = ? WHERE id = ?", "我希望在三个月内完成后端面试准备。", messageId);
    long reviewId = insertReview(userId);
    long declaredRevisionId = insertClaim(
        userId, runId, "DECLARED_FACT", "GOALS_AND_INTENTS", "准备后端面试", "USER_EXPLICIT",
        "USER_DECLARATION", "USER_AUTHORED");
    long observationRevisionId = insertClaim(
        userId, runId, "GENERAL_OBSERVATION", "IMPLEMENTATION_AND_ERROR_PATTERN", "边界条件仍需复盘",
        "SYSTEM_DERIVED", "SINGLE_REVIEW", "LIMITED");
    insertClaim(
        otherUserId, otherRunId, "DECLARED_FACT", "GOALS_AND_INTENTS", "其他用户内容", "USER_EXPLICIT",
        "USER_DECLARATION", "USER_AUTHORED");
    execute(
        "INSERT INTO learner_memory_claim_message_evidence (claim_revision_id, message_id, evidence_role, sequence_no) VALUES (?, ?, 'DECLARED', 1)",
        declaredRevisionId, messageId);
    execute(
        "INSERT INTO learner_memory_claim_review_evidence (claim_revision_id, review_id, evidence_role, sequence_no) VALUES (?, ?, 'OBSERVED', 1)",
        observationRevisionId, reviewId);

    LearnerProfileDocumentService service = service();
    LearnerProfileDocument document = service.getDocument(userId, "zh-CN");

    assertThat(document.blocks()).hasSize(4);
    assertThat(document.citationMap()).hasSize(2);
    assertThat(document.citationMap().values()).extracting(LearnerProfileDocument.Citation::claimRevisionId)
        .containsExactly(declaredRevisionId, observationRevisionId);
    LearnerProfileDocument.Citation declaredCitation = document.citationMap().get(1);
    LearnerProfileDocument.Citation observationCitation = document.citationMap().get(2);
    assertThat(declaredCitation.previewEvidence()).extracting(LearnerProfileDocument.EvidenceItem::type)
        .containsExactly(LearnerProfileDocument.EvidenceType.USER_MESSAGE);
    assertThat(observationCitation.previewEvidence()).extracting(LearnerProfileDocument.EvidenceItem::type)
        .containsExactly(LearnerProfileDocument.EvidenceType.CODE_REVIEW);
    assertThat(observationCitation.previewEvidence().get(0).codeReview().problemSlug()).isEqualTo("two-sum");
    assertThat(observationCitation.previewEvidence().get(0).codeReview()).hasNoNullFieldsOrProperties();
    assertThat(observationCitation.previewEvidence().get(0).codeReview().getClass().getRecordComponents())
        .extracting(component -> component.getName())
        .doesNotContain("rawCode", "normalizedCode", "reviewMarkdown");

    LearnerProfileDocument.EvidencePage page = service.getEvidence(
        userId, declaredCitation.statementRef(), null, 20);
    assertThat(page.items()).extracting(LearnerProfileDocument.EvidenceItem::type)
        .containsExactly(LearnerProfileDocument.EvidenceType.USER_MESSAGE);
    assertThat(page.items().get(0).userMessage().excerpt()).isEqualTo("我希望在三个月内完成后端面试准备。");
    assertThatThrownBy(() -> service.getEvidence(otherUserId, declaredCitation.statementRef(), null, 20))
        .isInstanceOf(LearnerProfileDocumentService.LearnerProfileStatementNotFoundException.class);

    execute("UPDATE learner_memory_claim_revision SET status = 'RETIRED' WHERE id = ?", declaredRevisionId);
    assertThatThrownBy(() -> service.getEvidence(userId, declaredCitation.statementRef(), null, 20))
        .isInstanceOf(LearnerProfileDocumentService.LearnerProfileStatementNotFoundException.class);
  }

  private LearnerProfileDocumentService service() throws Exception {
    LearnerMemoryMapper mapper = sqlSessionTemplate("mapper/profile/LearnerMemoryMapper.xml")
        .getMapper(LearnerMemoryMapper.class);
    return new LearnerProfileDocumentService(
        new MyBatisLearnerProfileDocumentProjectionRepository(mapper),
        new LearnerProfileDocumentProjector(new LearnerMemorySectionCatalog()),
        new LearnerProfileStatementReferenceCodec("postgres-document-it-secret"));
  }

  private long insertUpdateRun(long userId, String trigger) throws SQLException {
    return queryLong(
        """
        INSERT INTO learner_memory_update_run (
          user_id, trigger_type, status, idempotency_key, prompt_version, schema_version,
          input_count, operation_count, tool_call_count, started_at, completed_at)
        VALUES (?, ?, 'SUCCEEDED', ?, 'v1', 'v1', 0, 0, 0, NOW(), NOW())
        RETURNING id
        """,
        userId, trigger, "document-it-" + UUID.randomUUID());
  }

  private long insertClaim(
      long userId,
      long runId,
      String kind,
      String dimension,
      String text,
      String origin,
      String pattern,
      String grade
  ) throws SQLException {
    return queryLong(
        """
        INSERT INTO learner_memory_claim_revision (
          claim_key, user_id, entry_kind, dimension, revision_no, status, claim_text, claim_text_hash,
          origin_type, evidence_pattern, evidence_grade, update_run_id, valid_from)
        VALUES (?, ?, ?, ?, 1, 'ACTIVE', ?, ?, ?, ?, ?, ?, NOW())
        RETURNING id
        """,
        UUID.randomUUID(), userId, kind, dimension, text, "0".repeat(64), origin, pattern, grade, runId);
  }

  private long insertReview(long userId) throws SQLException {
    long sessionId = insertPracticeSession(userId, "two-sum");
    long messageId = insertUserMessage(userId);
    return queryLong(
        """
        INSERT INTO practice_code_review (
          user_id, plan_id, phase_index, problem_slug, practice_session_id, version_no, user_message_id,
          raw_code, normalized_code, language, context_summary, total_score, correctness_score,
          complexity_score, edge_case_score, code_quality_score, problem_fit_score, passed, review_markdown)
        VALUES (?, 1, 1, 'two-sum', ?, 1, ?, 'class Solution {}', 'class Solution {}', 'JAVA',
          'context', 9.0, 4.0, 2.0, 1.0, 1.0, 0.0, TRUE, 'review')
        RETURNING id
        """,
        userId, sessionId, messageId);
  }
}
