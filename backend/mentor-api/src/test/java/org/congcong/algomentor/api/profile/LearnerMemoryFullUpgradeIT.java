package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import java.util.UUID;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

class LearnerMemoryFullUpgradeIT extends PostgresIntegrationTestSupport {

  @Test
  void finalUpgradeRemovesLegacyProfileStorageAndPreservesMemoryClaims() throws Exception {
    migrateTo("48");
    long userId = insertUser();
    execute(
        """
        INSERT INTO learner_profile_entry (user_id, entry_kind, dimension, revision_no, status, content_text, origin_type, valid_from)
        VALUES (?, 'DECLARED_FACT', 'LEARNER_BACKGROUND', 1, 'ACTIVE', 'legacy profile', 'USER_EXPLICIT', NOW())
        """,
        userId);
    execute(
        """
        INSERT INTO queue_message (topic, message_key, message_value, status)
        VALUES ('learner-profile.code-review.v1', ?, '{}', 'PENDING')
        """,
        Long.toString(userId));
    long agentRunId = insertAgentRun(userId);
    long updateRunId = insertUpdateRun(userId, "run-" + UUID.randomUUID(), agentRunId);
    insertClaim(
        userId, updateRunId, UUID.randomUUID(), 1, "DECLARED_FACT", "LEARNER_BACKGROUND", null,
        "ACTIVE", "0".repeat(64), null);

    migrateLatest();

    assertThatThrownBy(() -> count("learner_profile_entry")).isInstanceOf(Exception.class);
    assertThat(queryLong("SELECT COUNT(*) FROM queue_message WHERE topic = 'learner-profile.code-review.v1'"))
        .isZero();
    assertThat(count("learner_memory_update_run")).isEqualTo(1);
    assertThat(count("learner_memory_claim_revision")).isEqualTo(1);
  }

  @Test
  void enforcesMemoryRevisionRunAndEvidenceConstraints() throws Exception {
    migrateLatest();
    long userId = insertUser();
    long agentRunId = insertAgentRun(userId);
    long updateRunId = insertUpdateRun(userId, "run-" + UUID.randomUUID(), agentRunId);
    long claimId = insertClaim(
        userId, updateRunId, UUID.randomUUID(), 1, "DECLARED_FACT", "LEARNER_BACKGROUND", null,
        "ACTIVE", "a".repeat(64), null);

    assertThatThrownBy(() -> insertClaim(
        userId, updateRunId, UUID.randomUUID(), 1, "DECLARED_FACT", "LEARNER_BACKGROUND", null,
        "ACTIVE", "a".repeat(64), null)).isInstanceOf(Exception.class);
    assertThat(insertClaim(
        userId, updateRunId, UUID.randomUUID(), 1, "GENERAL_OBSERVATION", "PROBLEM_SOLVING_APPROACH", null,
        "ACTIVE", "a".repeat(64), null)).isPositive();
    assertThat(insertClaim(
        userId, updateRunId, UUID.randomUUID(), 1, "DECLARED_FACT", "LEARNER_BACKGROUND", null,
        "SUPERSEDED", "a".repeat(64), null)).isPositive();
    assertThatThrownBy(() -> insertClaim(
        userId, updateRunId, UUID.randomUUID(), 1, "TAG_ASSESSMENT", "LEARNER_BACKGROUND", null,
        "ACTIVE", "b".repeat(64), null)).isInstanceOf(Exception.class);
    assertThatThrownBy(() -> execute(
        """
        INSERT INTO learner_memory_claim_revision (
          claim_key, user_id, entry_kind, dimension, revision_no, status, claim_text, claim_text_hash,
          origin_type, evidence_pattern, evidence_grade, update_run_id, valid_from, valid_to)
        VALUES (?, ?, 'DECLARED_FACT', 'GOALS_AND_INTENTS', 1, 'RETIRED', 'invalid validity', ?,
          'USER_EXPLICIT', 'USER_DECLARATION', 'USER_AUTHORED', ?, NOW(), NOW())
        """,
        UUID.randomUUID(), userId, "c".repeat(64), updateRunId)).isInstanceOf(Exception.class);

    UUID revisionKey = UUID.randomUUID();
    long supersededId = insertClaim(
        userId, updateRunId, revisionKey, 1, "GENERAL_OBSERVATION", "PROBLEM_SOLVING_APPROACH", null,
        "SUPERSEDED", "d".repeat(64), null);
    long activeRevisionId = insertClaim(
        userId, updateRunId, revisionKey, 2, "GENERAL_OBSERVATION", "PROBLEM_SOLVING_APPROACH", null,
        "ACTIVE", "e".repeat(64), supersededId);
    assertThat(activeRevisionId).isPositive();
    assertThatThrownBy(() -> insertClaim(
        userId, updateRunId, revisionKey, 3, "GENERAL_OBSERVATION", "PROBLEM_SOLVING_APPROACH", null,
        "ACTIVE", "f".repeat(64), null)).isInstanceOf(Exception.class);
    assertThatThrownBy(() -> insertClaim(
        userId, updateRunId, revisionKey, 1, "GENERAL_OBSERVATION", "PROBLEM_SOLVING_APPROACH", null,
        "SUPERSEDED", "5".repeat(64), null)).isInstanceOf(Exception.class);
    assertThatThrownBy(() -> insertClaim(
        userId, updateRunId, UUID.randomUUID(), 1, "GENERAL_OBSERVATION", "PROBLEM_SOLVING_APPROACH", null,
        "ACTIVE", "6".repeat(64), supersededId)).isInstanceOf(Exception.class);
    assertThatThrownBy(() -> execute(
        """
        INSERT INTO learner_memory_claim_revision (
          id, claim_key, user_id, entry_kind, dimension, revision_no, status, claim_text, claim_text_hash,
          origin_type, evidence_pattern, evidence_grade, update_run_id, supersedes_revision_id, valid_from)
        VALUES (9001, ?, ?, 'DECLARED_FACT', 'TIME_AND_RESOURCE_CONSTRAINTS', 1, 'ACTIVE', 'self reference', ?,
          'USER_EXPLICIT', 'USER_DECLARATION', 'USER_AUTHORED', ?, 9001, NOW())
        """,
        UUID.randomUUID(), userId, "7".repeat(64), updateRunId)).isInstanceOf(Exception.class);

    long reviewId = insertReview(userId);
    execute(
        """
        INSERT INTO learner_memory_update_run_review (update_run_id, review_id, sequence_no)
        VALUES (?, ?, 1)
        """,
        updateRunId, reviewId);
    assertThatThrownBy(() -> execute(
        """
        INSERT INTO learner_memory_update_run_review (update_run_id, review_id, sequence_no)
        VALUES (?, ?, 1)
        """,
        updateRunId, reviewId)).isInstanceOf(Exception.class);
    execute(
        """
        INSERT INTO learner_memory_claim_review_evidence (claim_revision_id, review_id, evidence_role, sequence_no)
        VALUES (?, ?, 'OBSERVED', 1)
        """,
        claimId, reviewId);
    assertThatThrownBy(() -> execute("DELETE FROM practice_code_review WHERE id = ?", reviewId))
        .isInstanceOf(Exception.class);

    long messageId = insertUserMessage(userId);
    long messageClaimId = insertClaim(
        userId, updateRunId, UUID.randomUUID(), 1, "DECLARED_FACT", "GOALS_AND_INTENTS", null,
        "ACTIVE", "1".repeat(64), null);
    execute(
        """
        INSERT INTO learner_memory_claim_message_evidence (claim_revision_id, message_id, evidence_role, sequence_no)
        VALUES (?, ?, 'DECLARED', 1)
        """,
        messageClaimId, messageId);
    assertThatThrownBy(() -> execute("DELETE FROM agent_message WHERE id = ?", messageId))
        .isInstanceOf(Exception.class);

    execute("DELETE FROM agent_run WHERE id = ?", agentRunId);
    assertThat(queryLong(
        "SELECT COUNT(*) FROM learner_memory_update_run WHERE id = ? AND agent_run_id IS NULL", updateRunId))
        .isEqualTo(1);
  }

  private long insertUpdateRun(long userId, String idempotencyKey, Long agentRunId) throws SQLException {
    return queryLong(
        """
        INSERT INTO learner_memory_update_run (
          user_id, trigger_type, status, idempotency_key, agent_run_id, prompt_version, schema_version,
          input_count, operation_count, tool_call_count, started_at, completed_at)
        VALUES (?, 'CODE_REVIEW_BATCH', 'SUCCEEDED', ?, ?, 'v2', 'v2', 1, 0, 0, NOW(), NOW())
        RETURNING id
        """,
        userId, idempotencyKey, agentRunId);
  }

  private long insertClaim(
      long userId,
      long updateRunId,
      UUID claimKey,
      int revisionNo,
      String kind,
      String dimension,
      Long tagId,
      String status,
      String hash,
      Long supersedesRevisionId) throws SQLException {
    return queryLong(
        """
        INSERT INTO learner_memory_claim_revision (
          claim_key, user_id, entry_kind, dimension, tag_id, revision_no, status, claim_text, claim_text_hash,
          origin_type, evidence_pattern, evidence_grade, update_run_id, supersedes_revision_id, valid_from, valid_to)
        VALUES (?, ?, ?, ?, ?, ?, ?, 'claim text', ?, 'SYSTEM_DERIVED', 'CROSS_PROBLEM_RECURRENCE',
          'SUPPORTED', ?, ?, NOW(), CASE WHEN ? = 'SUPERSEDED' THEN NOW() ELSE NULL END)
        RETURNING id
        """,
        claimKey, userId, kind, dimension, tagId, revisionNo, status, hash, updateRunId, supersedesRevisionId, status);
  }

  private long insertAgentRun(long userId) throws SQLException {
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
        taskId, turnId, UUID.randomUUID().toString(), "agent-run-" + UUID.randomUUID());
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
          'context', 10.0, 4.0, 2.0, 2.0, 1.0, 1.0, TRUE, 'review')
        RETURNING id
        """,
        userId, sessionId, messageId);
  }
}
