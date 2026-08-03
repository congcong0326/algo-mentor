package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.stream.Collectors;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewReviewCenterIndexMigrationResourceTest {

  @Test
  void createsTheUserProblemRecentReviewIndexInTheRequiredOrder() throws Exception {
    String migration;
    try (Reader reader = Resources.getResourceAsReader(
        "db/migration/V53__practice_code_review_user_problem_recent_index.sql");
        BufferedReader bufferedReader = new BufferedReader(reader)) {
      migration = bufferedReader.lines().collect(Collectors.joining(" ")).replaceAll("\\s+", " ");
    }

    assertThat(migration).contains(
        "CREATE INDEX IF NOT EXISTS idx_practice_code_review_user_problem_recent"
            + " ON practice_code_review (user_id, problem_slug, created_at DESC, id DESC);");
  }
}
