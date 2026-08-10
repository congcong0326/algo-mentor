package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class LearningPlanDraftOriginMigrationResourceTest {

  @Test
  void migrationAddsAndBackfillsSourceIndependentOriginSnapshots() throws Exception {
    String sql = new ClassPathResource("db/migration/V57__learning_plan_draft_origin_snapshots.sql")
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(sql)
        .contains("origin_brief_json JSONB")
        .contains("origin_plan_json JSONB")
        .contains("FROM learning_plan_draft_revision revision")
        .contains("ORDER BY revision.revision_no, revision.id")
        .contains("CASE WHEN draft.draft_plan_json IS NOT NULL THEN draft.command_json END")
        .doesNotContain("template_id");
  }

  @Test
  void draftMapperFreezesTheFirstCompletePlanAndDoesNotOverwriteIt() throws Exception {
    String xml = new ClassPathResource("mapper/learningplan/LearningPlanMapper.xml")
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(xml)
        .contains("origin_brief_json = COALESCE(")
        .contains("origin_plan_json = COALESCE(")
        .contains("WHEN #{draftPlanJson")
        .contains("ELSE #{commandJson")
        .contains("findDraftOriginForUser");
  }
}
