package org.congcong.algomentor.api.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

class ProblemTagNormalizationMigrationIT extends PostgresIntegrationTestSupport {

  @Test
  void migratesV32ArraysIntoCanonicalCatalogAssignmentsAndCompatibilityArrays() throws Exception {
    migrateToV32();
    insertProblem("no-tags", 1, List.of(), List.of(), List.of());
    insertProblem(
        "ordered-tags",
        2,
        List.of("graph", "array"),
        List.of("Graph Theory", "Array"),
        List.of("Graph Theory", "Array"));
    insertProblem("translated-graph", 3, List.of("graph"), List.of("Graph"), List.of("图"));
    insertProblem("translated-graph-again", 4, List.of("graph"), List.of("Graph"), List.of("图"));
    insertProblem("fallback", 5, List.of("hash-table"), List.of(""), List.of(""));
    insertProblem(
        "exact-duplicate",
        6,
        List.of("array", "array"),
        List.of("Array", "Array"),
        List.of("数组", "数组"));

    migrateLatest();

    assertThat(queryString("SELECT to_regclass('public.problem_tag')")).isEqualTo("problem_tag");
    assertThat(queryString("SELECT to_regclass('public.problem_tag_assignment')"))
        .isEqualTo("problem_tag_assignment");
    assertThat(queryLong("""
        SELECT COUNT(*)
        FROM pg_indexes
        WHERE schemaname = 'public'
          AND indexname = 'idx_problem_tag_assignment_tag_problem'
        """)).isEqualTo(1L);
    assertThat(queryLong("SELECT COUNT(*) FROM problem_tag")).isEqualTo(3L);
    assertThat(queryString("SELECT label_en FROM problem_tag WHERE value = 'graph'"))
        .isEqualTo("Graph");
    assertThat(queryString("SELECT label_zh FROM problem_tag WHERE value = 'graph'"))
        .isEqualTo("图");
    assertThat(queryString("SELECT label_en FROM problem_tag WHERE value = 'hash-table'"))
        .isEqualTo("hash-table");
    assertThat(queryString("SELECT label_zh FROM problem_tag WHERE value = 'hash-table'"))
        .isEqualTo("hash-table");
    assertThat(queryString("""
        SELECT array_to_string(tag_values, E'\\n')
        FROM problem
        WHERE slug = 'ordered-tags'
        """)).isEqualTo("graph\narray");
    assertThat(queryString("""
        SELECT array_to_string(tag_labels_en, E'\\n')
        FROM problem
        WHERE slug = 'ordered-tags'
        """)).isEqualTo("Graph\nArray");
    assertThat(queryString("""
        SELECT array_to_string(tag_labels_zh, E'\\n')
        FROM problem
        WHERE slug = 'ordered-tags'
        """)).isEqualTo("图\n数组");
    assertThat(queryLong("""
        SELECT COUNT(*)
        FROM problem_tag_assignment assignment
        JOIN problem p ON p.id = assignment.problem_id
        WHERE p.slug = 'no-tags'
        """)).isZero();
    assertThat(queryLong("""
        SELECT COUNT(*)
        FROM problem_tag_assignment assignment
        JOIN problem p ON p.id = assignment.problem_id
        WHERE p.slug = 'exact-duplicate'
        """)).isEqualTo(1L);
    assertThat(queryString("""
        SELECT array_to_string(tag_values, E'\\n')
        FROM problem
        WHERE slug = 'exact-duplicate'
        """)).isEqualTo("array");
    assertThat(queryLong(orderedArrayConsistencySql())).isZero();
  }

  @Test
  void rejectsConflictingDuplicateValuesAndRollsBackTheMigration() throws Exception {
    migrateToV32();
    insertProblem(
        "conflicting-tags",
        1,
        List.of("graph", "graph"),
        List.of("Graph", "Graph Theory"),
        List.of("图", "图"));

    assertThatThrownBy(this::migrateLatest)
        .hasMessageContaining("Conflicting duplicate problem tag labels");
    assertThat(queryString("SELECT to_regclass('public.problem_tag')")).isNull();
    assertThat(queryString("SELECT to_regclass('public.problem_tag_assignment')")).isNull();
  }

  private String orderedArrayConsistencySql() {
    return """
        SELECT COUNT(*)
        FROM problem p
        LEFT JOIN LATERAL (
          SELECT
            array_agg(tag.value::TEXT ORDER BY assignment.ordinal) AS tag_values,
            array_agg(tag.label_en::TEXT ORDER BY assignment.ordinal) AS tag_labels_en,
            array_agg(tag.label_zh::TEXT ORDER BY assignment.ordinal) AS tag_labels_zh
          FROM problem_tag_assignment assignment
          JOIN problem_tag tag ON tag.id = assignment.tag_id
          WHERE assignment.problem_id = p.id
        ) normalized_tags ON TRUE
        WHERE p.tag_values IS DISTINCT FROM COALESCE(normalized_tags.tag_values, ARRAY[]::TEXT[])
           OR p.tag_labels_en IS DISTINCT FROM COALESCE(normalized_tags.tag_labels_en, ARRAY[]::TEXT[])
           OR p.tag_labels_zh IS DISTINCT FROM COALESCE(normalized_tags.tag_labels_zh, ARRAY[]::TEXT[])
        """;
  }
}
