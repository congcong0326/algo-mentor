package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

class LearnerProfileFullUpgradeIT extends PostgresIntegrationTestSupport {

  @Test
  void upgradesTheV33BaselineToTheLearnerProfileSchemaAndValidatesIt() throws Exception {
    migrateTo("33");

    assertThat(queryString("SELECT to_regclass('learner_profile_entry')::text")).isNull();
    assertThat(queryString("SELECT to_regclass('queue_message')::text")).isNull();
    assertThat(queryString("SELECT to_regclass('practice_code_review_tag')::text")).isNull();

    migrateLatest();
    flyway().validate();

    assertThat(queryLong("SELECT COUNT(*) FROM flyway_schema_history WHERE version IN ('34', '35', '36')"))
        .isEqualTo(3L);
    assertThat(queryString("SELECT to_regclass('learner_profile_entry')::text")).isEqualTo("learner_profile_entry");
    assertThat(queryString("SELECT to_regclass('queue_message')::text")).isEqualTo("queue_message");
    assertThat(queryString("SELECT to_regclass('practice_code_review_tag')::text"))
        .isEqualTo("practice_code_review_tag");
    assertThat(queryLong("""
        SELECT COUNT(*)
        FROM pg_indexes
        WHERE schemaname = current_schema()
          AND indexname IN (
            'uk_learner_profile_entry_active_tag',
            'uk_learner_profile_entry_active_dimension',
            'idx_queue_message_pending_topic_key',
            'idx_practice_code_review_tag_tag_review')
        """)).isEqualTo(4L);
    assertThat(queryLong("""
        SELECT COUNT(*)
        FROM pg_constraint
        WHERE conrelid IN ('learner_profile_entry'::regclass, 'queue_message'::regclass)
          AND conname IN (
            'ck_learner_profile_entry_scope',
            'ck_learner_profile_entry_validity',
            'ck_queue_message_status',
            'ck_queue_message_succeeded_at')
        """)).isEqualTo(4L);
  }
}
