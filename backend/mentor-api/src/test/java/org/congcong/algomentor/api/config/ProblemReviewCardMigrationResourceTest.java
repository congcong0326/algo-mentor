package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class ProblemReviewCardMigrationResourceTest {

  @Test
  void v40RebuildsReviewCardsAttemptsAndProblemNotes() throws Exception {
    String sql = new ClassPathResource("db/migration/V40__rebuild_problem_review_card.sql")
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(sql)
        .contains("DROP TABLE IF EXISTS review_recall_evaluation")
        .contains("DROP TABLE IF EXISTS review_log")
        .contains("DROP TABLE IF EXISTS mistake_note")
        .contains("DROP COLUMN IF EXISTS ai_suggestion_enabled")
        .contains("CREATE TABLE problem_review_card")
        .contains("CREATE TABLE problem_review_attempt")
        .contains("CREATE TABLE user_problem_note")
        .contains("UNIQUE (user_id, client_attempt_id)")
        .contains("UNIQUE (user_id, problem_slug)")
        .contains("char_length(note_markdown) <= 10000");
  }
}
