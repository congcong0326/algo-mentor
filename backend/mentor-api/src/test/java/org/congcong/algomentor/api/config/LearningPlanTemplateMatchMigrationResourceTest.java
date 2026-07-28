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

  @Test
  void v44AddsAndBackfillsCatalogFields() throws Exception {
    String sql = new ClassPathResource(
        "db/migration/V44__learning_plan_template_catalog.sql")
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(sql)
        .contains("catalog_category VARCHAR(40)")
        .contains("recommended_order INTEGER")
        .contains("SYSTEMATIC_LEARNING")
        .contains("INTERVIEW_PREP")
        .contains("TOPIC_BREAKTHROUGH")
        .contains("LANGUAGE_AND_ROLE")
        .contains("ck_learning_plan_template_recommended_order")
        .contains("idx_learning_plan_template_catalog")
        .contains("leetcode_75_core_sprint' THEN 1")
        .contains("topic_dynamic_programming_foundation' THEN 6");
  }
}
