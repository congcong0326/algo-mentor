package org.congcong.algomentor.api.learningplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

class LearningPlanTemplateEnglishContentMigrationIT extends PostgresIntegrationTestSupport {

  @Test
  void keepsEnglishContentValidationAfterTheSimplificationMigration() throws Exception {
    migrateLatest();
    long templateId = insertReadyTemplate("english-ready", 1);

    assertThat(queryLong("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '55' AND success = TRUE"))
        .isEqualTo(1L);
    assertThat(queryLong("SELECT COUNT(*) FROM learning_plan_template WHERE id = ? AND english_content_ready", templateId))
        .isEqualTo(1L);
    assertThatThrownBy(() -> execute(
        "UPDATE learning_plan_template SET prerequisites_en_json = '[\"   \"]'::jsonb WHERE id = ?",
        templateId))
        .isInstanceOf(SQLException.class);
  }

  @Test
  void readyTemplateRequiresAtLeastOneCompleteEnglishPhase() throws Exception {
    migrateLatest();

    assertThatThrownBy(() -> insertTemplate("no-phase", true))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("incomplete English phase content");

    long templateId = insertReadyTemplate("incomplete-phase", 1);
    assertThatThrownBy(() -> execute(
        "UPDATE learning_plan_template_phase SET title_en = NULL WHERE template_id = ?",
        templateId))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("incomplete English phase content");
  }

  @Test
  void phaseReassignmentAlsoValidatesThePreviousTemplate() throws Exception {
    migrateLatest();
    long sourceTemplateId = insertReadyTemplate("phase-source", 1);
    long destinationTemplateId = insertReadyTemplate("phase-destination", 2);

    assertThatThrownBy(() -> execute(
        "UPDATE learning_plan_template_phase SET template_id = ? WHERE template_id = ?",
        destinationTemplateId,
        sourceTemplateId))
        .isInstanceOf(SQLException.class)
        .hasMessageContaining("incomplete English phase content");
    assertThat(queryLong(
        "SELECT COUNT(*) FROM learning_plan_template_phase WHERE template_id = ?",
        sourceTemplateId)).isEqualTo(1L);
    assertThat(queryLong(
        "SELECT COUNT(*) FROM learning_plan_template_phase WHERE template_id = ?",
        destinationTemplateId)).isEqualTo(1L);
  }

  private long insertReadyTemplate(String templateKey, int phaseIndex) throws Exception {
    long templateId = insertTemplate(templateKey, false);
    execute("""
        INSERT INTO learning_plan_template_phase (
          template_id, phase_index, title, title_en, duration_weeks, focus, focus_en
        )
        VALUES (?, ?, '阶段', 'Phase', 1, '重点', 'Focus')
        """, templateId, phaseIndex);
    execute("UPDATE learning_plan_template SET english_content_ready = TRUE WHERE id = ?", templateId);
    return templateId;
  }

  private long insertTemplate(String templateKey, boolean englishContentReady) throws Exception {
    return queryLong("""
        INSERT INTO learning_plan_template (
          template_id, title, title_en, summary, summary_en, catalog_category, intent, goal, goal_en,
          default_duration_weeks, level, default_weekly_hours, difficulty_preference,
          topic_preferences_json, target_audience, target_audience_en,
          prerequisites_json, prerequisites_en_json, recommended_for_json, recommended_for_en_json,
          not_recommended_for_json, not_recommended_for_en_json, expected_outcome, expected_outcome_en,
          english_content_ready, source_name, source_url, source_commit, source_data_path,
          source_description, curation_notes, license_notice, metadata_json
        )
        VALUES (?, '标题', 'Title', '摘要', 'Summary', 'SYSTEMATIC_LEARNING', 'LONG_TERM_LEARNING', '目标', 'Goal',
          1, 'BEGINNER', 5, 'MEDIUM', '["Array"]'::jsonb, '学习者', 'Learner',
          '["基础"]'::jsonb, '["Basics"]'::jsonb, '["学习"]'::jsonb, '["Learning"]'::jsonb,
          '["不适用"]'::jsonb, '["Not suitable"]'::jsonb, '结果', 'Outcome', ?, 'test-source',
          'https://example.test/source', 'test-commit', 'seed.json', 'source', 'notes', 'license', '{}'::jsonb)
        RETURNING id
        """, templateKey, englishContentReady);
  }
}
