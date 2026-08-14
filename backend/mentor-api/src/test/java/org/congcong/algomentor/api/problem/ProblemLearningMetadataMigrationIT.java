package org.congcong.algomentor.api.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

class ProblemLearningMetadataMigrationIT extends PostgresIntegrationTestSupport {

  @Test
  void upgradesLegacyCategoriesWithoutLosingRowsAndCreatesMetadataConstraints() throws Exception {
    migrateTo("63");
    insertProblem("two-sum", 1, java.util.List.of(), java.util.List.of(), java.util.List.of());
    execute("INSERT INTO problem_category (slug, name) VALUES ('legacy-category', 'Legacy Category')");
    execute("""
        INSERT INTO problem_category_item (category_id, problem_id)
        SELECT category.id, problem.id
        FROM problem_category category
        JOIN problem ON problem.slug = 'two-sum'
        WHERE category.slug = 'legacy-category'
        """);

    migrateLatest();

    assertThat(queryString("SELECT name_en FROM problem_category WHERE slug = 'legacy-category'"))
        .isEqualTo("Legacy Category");
    assertThat(queryString("SELECT name_zh FROM problem_category WHERE slug = 'legacy-category'"))
        .isEqualTo("Legacy Category");
    assertThat(queryString("SELECT source FROM problem_category WHERE slug = 'legacy-category'"))
        .isEqualTo("LEGACY");
    assertThat(queryString("SELECT source FROM problem_category_item LIMIT 1")).isEqualTo("LEGACY");
    assertThat(count("problem_relation")).isZero();
    assertThat(count("problem_hint")).isZero();
    assertThat(count("problem_code_template")).isZero();

    assertThatThrownBy(() -> execute("""
        INSERT INTO problem_relation (
          source_problem_id, target_problem_slug, target_problem_id, relation_type, source, source_snapshot
        )
        SELECT id, slug, id, 'LEETCODE_SIMILAR', 'LEETCODE', 'snapshot'
        FROM problem WHERE slug = 'two-sum'
        """))
        .hasMessageContaining("ck_problem_relation_resolved_target_not_source");
  }
}
