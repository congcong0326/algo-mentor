package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class LegacyProblemCleanupMigrationResourceTest {

  @Test
  void v43RemovesLegacyProblemsAndTheirBusinessReferences() throws Exception {
    String sql = new ClassPathResource("db/migration/V43__remove_legacy_problem_batch.sql")
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(sql)
        .contains("6bd9323f1a542eac6997f9f76656842333d96c45")
        .contains("prune_legacy_problem_references")
        .contains("DELETE FROM learning_plan_draft")
        .contains("DELETE FROM practice_session")
        .contains("DELETE FROM learning_plan_problem_progress")
        .contains("DELETE FROM learning_plan_recommended_problem")
        .contains("DELETE FROM agent_task")
        .contains("DELETE FROM problem problem_row")
        .doesNotContain("DELETE FROM learning_plan_template_problem_ref");
  }
}
