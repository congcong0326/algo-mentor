package org.congcong.algomentor.api.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

class ProblemReviewCardMigrationIT extends PostgresIntegrationTestSupport {

  @Test
  void migratesCleanDatabaseToProblemReviewCardSchema() throws Exception {
    migrateLatest();

    assertNewReviewSchema();
    assertOldReviewSchemaRemoved();
    assertThat(queryLong("""
        SELECT COUNT(*)
        FROM flyway_schema_history
        WHERE version = '40' AND success = TRUE
        """)).isEqualTo(1L);
  }

  @Test
  void upgradesV39ByDroppingLegacyReviewSchema() throws Exception {
    migrateTo("39");

    assertThat(queryString("SELECT to_regclass('mistake_note')")).isEqualTo("mistake_note");
    assertThat(queryString("SELECT to_regclass('review_log')")).isEqualTo("review_log");
    assertThat(queryString("SELECT to_regclass('review_recall_evaluation')"))
        .isEqualTo("review_recall_evaluation");
    assertThat(columnCount("user_review_preference", "ai_suggestion_enabled")).isEqualTo(1L);

    migrateLatest();

    assertNewReviewSchema();
    assertOldReviewSchemaRemoved();
  }

  @Test
  void resetsCorruptedRelearningCardsBeforeTheyProduceMultiYearIntervals() throws Exception {
    migrateTo("65");
    String problemSlug = "remove-duplicates-from-sorted-array";
    insertProblem(problemSlug, 26, List.of("ARRAY"), List.of("Array"), List.of("数组"));
    long userId = insertUser();
    execute("""
        INSERT INTO problem_review_card (
          user_id, problem_slug, source, source_detail_json,
          repetitions, interval_days, fsrs_state, fsrs_step,
          fsrs_stability, fsrs_difficulty, due_at, lapses, created_at, updated_at
        ) VALUES (?, ?, 'REVIEW_FAILED', '{}'::jsonb, 0, 0, 'RELEARNING', 0,
          16.150700, 2.482439, NOW(), 1, NOW(), NOW())
        """, userId, problemSlug);

    migrateLatest();

    assertThat(queryString("SELECT fsrs_state FROM problem_review_card WHERE user_id = ?", userId))
        .isEqualTo("LEARNING");
    assertThat(queryLong("SELECT COUNT(*) FROM problem_review_card WHERE user_id = ?"
        + " AND fsrs_stability IS NULL AND fsrs_difficulty IS NULL AND last_reviewed_at IS NULL", userId))
        .isEqualTo(1L);
  }

  private void assertNewReviewSchema() throws Exception {
    assertThat(queryString("SELECT to_regclass('problem_review_card')"))
        .isEqualTo("problem_review_card");
    assertThat(queryString("SELECT to_regclass('problem_review_attempt')"))
        .isEqualTo("problem_review_attempt");
    assertThat(queryString("SELECT to_regclass('user_problem_note')"))
        .isEqualTo("user_problem_note");
    assertThat(constraintCount("problem_review_card", "uk_problem_review_card_user_problem"))
        .isEqualTo(1L);
    assertThat(constraintCount("problem_review_attempt", "uk_problem_review_attempt_user_client"))
        .isEqualTo(1L);
    assertThat(constraintCount("user_problem_note", "uk_user_problem_note_user_problem"))
        .isEqualTo(1L);
  }

  private void assertOldReviewSchemaRemoved() throws Exception {
    assertThat(queryString("SELECT to_regclass('mistake_note')")).isNull();
    assertThat(queryString("SELECT to_regclass('review_log')")).isNull();
    assertThat(queryString("SELECT to_regclass('review_recall_evaluation')")).isNull();
    assertThat(columnCount("user_review_preference", "ai_suggestion_enabled")).isZero();
  }

  private long columnCount(String tableName, String columnName) throws Exception {
    return queryLong("""
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = ?
          AND column_name = ?
        """, tableName, columnName);
  }

  private long constraintCount(String tableName, String constraintName) throws Exception {
    return queryLong("""
        SELECT COUNT(*)
        FROM information_schema.table_constraints
        WHERE constraint_schema = current_schema()
          AND table_name = ?
          AND constraint_name = ?
        """, tableName, constraintName);
  }
}
