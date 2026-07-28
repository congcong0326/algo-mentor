package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class LearningPlanTemplateMatchMigrationResourceTest {

  @Test
  void v42RecalculatesTemplateProblemMatchesFromRecommendationReasonAvailability() throws Exception {
    String sql = new ClassPathResource(
        "db/migration/V42__repair_learning_plan_template_problem_matches.sql")
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(sql)
        .contains("UPDATE learning_plan_template_problem_ref")
        .contains("recommendation_reason_en")
        .contains("recommendation_reason_zh")
        .contains("UPDATE learning_plan_template template")
        .contains("matched_problem_count")
        .contains("missing_problem_count");
  }
}
