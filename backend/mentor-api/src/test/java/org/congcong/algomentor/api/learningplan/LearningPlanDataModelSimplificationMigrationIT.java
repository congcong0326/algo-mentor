package org.congcong.algomentor.api.learningplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

class LearningPlanDataModelSimplificationMigrationIT extends PostgresIntegrationTestSupport {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void migratesLegacySnapshotsAndDropsRetiredTemplateColumns() throws Exception {
    migrateTo("53");
    long templateId = insertLegacyTemplate();
    insertLegacyTemplatePhase(templateId);
    execute("UPDATE learning_plan_template SET english_content_ready = TRUE WHERE id = ?", templateId);

    long draftId = queryLong("""
        INSERT INTO learning_plan_draft (
          user_id, status, command_json, draft_plan_json, expires_at
        )
        VALUES (7, 'GENERATED', ?::jsonb, ?::jsonb, NOW() + INTERVAL '1 day')
        RETURNING id
        """, legacyCommandJson(), legacyPlanJson());
    long planId = queryLong("""
        INSERT INTO learning_plan (user_id, status, title, plan_json)
        VALUES (7, 'ACTIVE', '旧计划', ?::jsonb)
        RETURNING id
        """, legacyPlanJson());
    long draftRevisionGroupId = insertProposalGroup("DRAFT_REVISION", "DRAFT", draftId);
    long draftRevisionId = queryLong("""
        INSERT INTO learning_plan_draft_revision (
          proposal_group_id, draft_id, user_id, revision_no, status, instruction,
          base_plan_json, proposed_plan_json
        )
        VALUES (?, ?, 7, 1, 'READY', '更新计划', ?::jsonb, ?::jsonb)
        RETURNING id
        """, draftRevisionGroupId, draftId, legacyPlanJson(), legacyPlanJson());
    long extensionRevisionGroupId = insertProposalGroup("PLAN_EXTENSION", "PLAN", planId);
    long extensionRevisionId = queryLong("""
        INSERT INTO learning_plan_extension_revision (
          proposal_group_id, plan_id, user_id, revision_no, status, instruction,
          base_plan_json, base_max_phase_index, previous_extension_json, proposed_extension_json
        )
        VALUES (?, ?, 7, 1, 'READY', '追加阶段', ?::jsonb, 2, ?::jsonb, ?::jsonb)
        RETURNING id
        """, extensionRevisionGroupId, planId, legacyPlanJson(), legacyExtensionJson(), legacyExtensionJson());

    migrateLatest();

    assertSimplifiedCommand(queryString(
        "SELECT command_json::text FROM learning_plan_draft WHERE id = ?", draftId));
    assertSimplifiedPlan(queryString(
        "SELECT draft_plan_json::text FROM learning_plan_draft WHERE id = ?", draftId));
    assertSimplifiedPlan(queryString(
        "SELECT plan_json::text FROM learning_plan WHERE id = ?", planId));
    assertSimplifiedPlan(queryString(
        "SELECT base_plan_json::text FROM learning_plan_draft_revision WHERE id = ?", draftRevisionId));
    assertSimplifiedPlan(queryString(
        "SELECT proposed_plan_json::text FROM learning_plan_draft_revision WHERE id = ?", draftRevisionId));
    assertSimplifiedPlan(queryString(
        "SELECT base_plan_json::text FROM learning_plan_extension_revision WHERE id = ?", extensionRevisionId));
    assertSimplifiedExtension(queryString(
        "SELECT previous_extension_json::text FROM learning_plan_extension_revision WHERE id = ?", extensionRevisionId));
    assertSimplifiedExtension(queryString(
        "SELECT proposed_extension_json::text FROM learning_plan_extension_revision WHERE id = ?", extensionRevisionId));
    assertThat(queryLong("""
        SELECT COUNT(*)
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND (
            (table_name = 'learning_plan_template'
              AND column_name IN ('interview_oriented', 'difficulty_mix_json'))
            OR (table_name = 'learning_plan_template_phase'
              AND column_name IN (
                'objectives_json', 'objectives_en_json', 'recommended_tags_json',
                'acceptance_criteria_json', 'acceptance_criteria_en_json',
                'review_advice', 'review_advice_en'))
          )
        """)).isZero();
    assertThat(queryLong("SELECT COUNT(*) FROM flyway_schema_history WHERE version = '55' AND success = TRUE"))
        .isEqualTo(1L);
  }

  private long insertLegacyTemplate() throws Exception {
    return queryLong("""
        INSERT INTO learning_plan_template (
          template_id, title, title_en, summary, summary_en, catalog_category, intent, goal, goal_en,
          default_duration_weeks, level, default_weekly_hours, difficulty_preference, interview_oriented,
          topic_preferences_json, target_audience, target_audience_en, difficulty_mix_json,
          prerequisites_json, prerequisites_en_json, recommended_for_json, recommended_for_en_json,
          not_recommended_for_json, not_recommended_for_en_json, expected_outcome, expected_outcome_en,
          english_content_ready, source_name, source_url, source_commit, source_data_path,
          source_description, curation_notes, license_notice, metadata_json
        )
        VALUES (
          'legacy_template', '旧模板', 'Legacy template', '旧摘要', 'Legacy summary',
          'SYSTEMATIC_LEARNING', 'LONG_TERM_LEARNING', '旧目标', 'Legacy goal',
          2, 'BEGINNER', 5, 'MEDIUM', FALSE,
          '["Array"]'::jsonb, '学习者', 'Learner', '{"Easy":{"count":1}}'::jsonb,
          '["基础"]'::jsonb, '["Basics"]'::jsonb, '["学习"]'::jsonb, '["Learning"]'::jsonb,
          '["不适用"]'::jsonb, '["Not suitable"]'::jsonb, '完成目标', 'Complete outcome',
          FALSE, 'test-source', 'https://example.test/source', 'test-commit', 'seed.json',
          'source', 'notes', 'license', '{}'::jsonb
        )
        RETURNING id
        """);
  }

  private void insertLegacyTemplatePhase(long templateId) throws Exception {
    execute("""
        INSERT INTO learning_plan_template_phase (
          template_id, phase_index, title, title_en, duration_weeks, focus, focus_en,
          objectives_json, objectives_en_json, recommended_tags_json,
          acceptance_criteria_json, acceptance_criteria_en_json, review_advice, review_advice_en
        )
        VALUES (
          ?, 1, '旧阶段', 'Legacy phase', 2, '旧重点', 'Legacy focus',
          '["旧目标"]'::jsonb, '["Legacy objective"]'::jsonb, '["Array"]'::jsonb,
          '["旧验收"]'::jsonb, '["Legacy acceptance"]'::jsonb, '旧复盘', 'Legacy review'
        )
        """, templateId);
  }

  private long insertProposalGroup(String proposalType, String targetType, long targetId) throws Exception {
    return queryLong("""
        INSERT INTO learning_plan_proposal_group (
          user_id, proposal_type, target_type, target_id, status, initial_instruction
        )
        VALUES (7, ?, ?, ?, 'ACTIVE', '迁移测试')
        RETURNING id
        """, proposalType, targetType, targetId);
  }

  private void assertSimplifiedCommand(String json) throws Exception {
    assertThat(objectMapper.readTree(json).has("interviewOriented")).isFalse();
  }

  private void assertSimplifiedPlan(String json) throws Exception {
    JsonNode plan = objectMapper.readTree(json);
    assertThat(plan.has("interviewOriented")).isFalse();
    assertThat(fieldNames(plan.path("metadata")))
        .containsExactlyInAnyOrder("template", "contentLocale");
    assertThat(fieldNames(plan.path("metadata").path("template")))
        .containsExactlyInAnyOrder("templateId", "matchedProblemCount");
    assertThat(plan.path("phases").get(0).path("title").asText()).isEqualTo("第一阶段");
    assertThat(plan.path("phases").get(1).path("title").asText()).isEqualTo("第二阶段");
    assertSimplifiedPhases(plan.path("phases"));
  }

  private void assertSimplifiedExtension(String json) throws Exception {
    JsonNode extension = objectMapper.readTree(json);
    assertThat(fieldNames(extension.path("metadata")))
        .containsExactlyInAnyOrder("template", "contentLocale");
    assertThat(fieldNames(extension.path("metadata").path("template")))
        .containsExactlyInAnyOrder("templateId", "matchedProblemCount");
    assertSimplifiedPhases(extension.path("newPhases"));
  }

  private void assertSimplifiedPhases(JsonNode phases) {
    for (JsonNode phase : phases) {
      assertThat(fieldNames(phase))
          .containsExactlyInAnyOrder("phaseIndex", "title", "durationWeeks", "focus", "problems");
    }
  }

  private List<String> fieldNames(JsonNode node) {
    List<String> names = new ArrayList<>();
    node.fieldNames().forEachRemaining(names::add);
    return names;
  }

  private String legacyCommandJson() {
    return """
        {"intent":"LONG_TERM_LEARNING","objective":"旧目标","interviewOriented":true}
        """;
  }

  private String legacyPlanJson() {
    return """
        {
          "title":"旧计划",
          "summary":"旧摘要",
          "intent":"LONG_TERM_LEARNING",
          "objective":"旧目标",
          "durationWeeks":2,
          "level":"BEGINNER",
          "weeklyHours":5,
          "topicPreferences":["Array"],
          "interviewOriented":false,
          "phases":[
            {
              "phaseIndex":1,
              "title":"第一阶段",
              "durationWeeks":1,
              "focus":"数组",
              "objectives":["旧目标"],
              "recommendedTags":["Array"],
              "acceptanceCriteria":["旧验收"],
              "reviewAdvice":"旧复盘",
              "problems":[]
            },
            {
              "phaseIndex":2,
              "title":"第二阶段",
              "durationWeeks":1,
              "focus":"字符串",
              "objectives":["旧目标"],
              "recommendedTags":["String"],
              "acceptanceCriteria":["旧验收"],
              "reviewAdvice":"旧复盘",
              "problems":[]
            }
          ],
          "metadata":{
            "contentLocale":"zh-CN",
            "problemRecommendationIncomplete":true,
            "loadRisk":"OVERLOADED",
            "weeklyBuckets":[{"weekIndex":1}],
            "nextTrainingPackage":{"newProblemCount":1},
            "template":{
              "templateId":"legacy_template",
              "matchedProblemCount":2,
              "sourceName":"test-source",
              "sourceUrl":"https://example.test/source",
              "sourceCommit":"test-commit",
              "sourceDataPath":"seed.json",
              "problemCount":2,
              "missingProblemCount":0,
              "problemRefs":["two-sum"]
            }
          }
        }
        """;
  }

  private String legacyExtensionJson() {
    return """
        {
          "summary":"旧扩展",
          "newPhases":[{
            "phaseIndex":3,
            "title":"扩展阶段",
            "durationWeeks":1,
            "focus":"图论",
            "objectives":["旧目标"],
            "recommendedTags":["Graph"],
            "acceptanceCriteria":["旧验收"],
            "reviewAdvice":"旧复盘",
            "problems":[]
          }],
          "metadata":{
            "contentLocale":"zh-CN",
            "problemRecommendationIncomplete":true,
            "loadRisk":"OVERLOADED",
            "weeklyBuckets":[],
            "nextTrainingPackage":{},
            "template":{
              "templateId":"legacy_template",
              "matchedProblemCount":2,
              "sourceName":"test-source"
            }
          }
        }
        """;
  }
}
