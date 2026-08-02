package org.congcong.algomentor.api.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import org.congcong.algomentor.api.ability.mapper.AbilityProfileMapper;
import org.congcong.algomentor.api.ability.mapper.model.AbilityTagScoreRow;
import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;

class AbilityProfileMapperIT extends PostgresIntegrationTestSupport {

  @Test
  void aggregatesActiveNormalizedTagsAndKeepsOnlyTheLatestReviewPerProblem() throws Exception {
    migrateLatest();
    long array = insertCatalog("array", "Array", "数组", true);
    long matrix = insertCatalog("matrix", "Matrix", "矩阵", true);
    long graph = insertCatalog("graph", "Graph", "图", false);
    long hashTable = insertCatalog("hash-table", "Hash Table", "哈希表", true);
    for (int index = 0; index < 20; index++) {
      String slug = "array-" + index;
      List<String> values = index == 0 ? List.of("array", "hash-table") : List.of("array");
      List<String> labelsEn = index == 0 ? List.of("Array", "Hash Table") : List.of("Array");
      List<String> labelsZh = index == 0 ? List.of("数组", "哈希表") : List.of("数组");
      insertProblem(slug, index + 1, values, labelsEn, labelsZh);
      assignTag(slug, array, 0);
      if (index == 0) {
        assignTag(slug, hashTable, 1);
      }
    }
    for (int index = 0; index < 20; index++) {
      String slug = "matrix-" + index;
      insertProblem(slug, 100 + index, List.of("matrix"), List.of("Matrix"), List.of("矩阵"));
      assignTag(slug, matrix, 0);
    }
    for (int index = 0; index < 20; index++) {
      String slug = "inactive-graph-" + index;
      insertProblem(slug, 200 + index, List.of("graph"), List.of("Graph"), List.of("图"));
      assignTag(slug, graph, 0);
    }
    insertReview(7L, "array-0", 2, "NOW() - INTERVAL '2 minutes'", 0);
    insertReview(7L, "array-0", 8, "NOW() - INTERVAL '1 minute'", 1);
    insertReview(7L, "array-1", 6, "NOW()", 2);
    AbilityProfileMapper mapper = mapper();

    List<AbilityTagScoreRow> rows = mapper.findCommonTagScores(7L, 20, "zh-CN");

    assertThat(rows)
        .extracting(row -> List.of(row.tag(), row.label(), row.problemCount(), row.reviewedProblemCount()))
        .containsExactly(
            List.of("array", "数组", 20L, 2L),
            List.of("matrix", "矩阵", 20L, 0L));
    assertThat(rows.get(0).rawAverageScore()).isEqualByComparingTo(new BigDecimal("7"));
    assertThat(rows.get(1).rawAverageScore()).isNull();

    List<AbilityTagScoreRow> englishRows = mapper.findCommonTagScores(7L, 20, "en-US");

    assertThat(englishRows)
        .extracting(row -> List.of(row.tag(), row.label()))
        .containsExactly(
            List.of("array", "Array"),
            List.of("matrix", "Matrix"));
  }

  private AbilityProfileMapper mapper() throws Exception {
    SqlSessionTemplate sqlSessionTemplate = sqlSessionTemplate("mapper/ability/AbilityProfileMapper.xml");
    return sqlSessionTemplate.getMapper(AbilityProfileMapper.class);
  }

  private void insertReview(
      long userId,
      String problemSlug,
      int totalScore,
      String createdAtExpression,
      int phaseIndex
  ) throws Exception {
    try (Connection connection = dataSource().getConnection()) {
      long taskId = insertReturningLong(connection, """
          INSERT INTO agent_task (user_id, status, created_at, updated_at)
          VALUES (?, 'COMPLETED', NOW(), NOW())
          RETURNING id
          """, userId);
      long turnId = insertReturningLong(connection, """
          INSERT INTO agent_turn (task_id, sequence_no, status, created_at, updated_at)
          VALUES (?, 1, 'COMPLETED', NOW(), NOW())
          RETURNING id
          """, taskId);
      long messageId = insertReturningLong(connection, """
          INSERT INTO agent_message (
            task_id, turn_id, role, content, sequence_no, status, created_at, updated_at
          )
          VALUES (?, ?, 'user', 'code', 1, 'COMPLETED', NOW(), NOW())
          RETURNING id
          """, taskId, turnId);
      long sessionId = insertReturningLong(connection, """
          INSERT INTO practice_session (user_id, plan_id, phase_index, problem_slug, status)
          VALUES (?, 1, ?, ?, 'ACTIVE')
          RETURNING id
          """, userId, phaseIndex, problemSlug);
      String sql = """
          INSERT INTO practice_code_review (
            user_id,
            plan_id,
            phase_index,
            problem_slug,
            practice_session_id,
            version_no,
            user_message_id,
            raw_code,
            normalized_code,
            language,
            detection_evidence_json,
            context_summary,
            total_score,
            correctness_score,
            complexity_score,
            edge_case_score,
            code_quality_score,
            problem_fit_score,
            passed,
            deduction_reasons_json,
            improvement_suggestions_json,
            review_markdown,
            created_at
          )
          VALUES (?, 1, ?, ?, ?, 1, ?, 'code', 'code', 'python', '[]'::JSONB, 'context',
              ?, 2, 1, 1, 0, 0, FALSE, '[]'::JSONB, '[]'::JSONB, 'review', %s)
          """.formatted(createdAtExpression);
      try (PreparedStatement statement = connection.prepareStatement(sql)) {
        statement.setLong(1, userId);
        statement.setInt(2, phaseIndex);
        statement.setString(3, problemSlug);
        statement.setLong(4, sessionId);
        statement.setLong(5, messageId);
        statement.setInt(6, totalScore);
        statement.executeUpdate();
      }
    }
  }

  private long insertReturningLong(Connection connection, String sql, Object... parameters) throws Exception {
    try (PreparedStatement statement = connection.prepareStatement(sql)) {
      for (int index = 0; index < parameters.length; index++) {
        statement.setObject(index + 1, parameters[index]);
      }
      try (ResultSet resultSet = statement.executeQuery()) {
        resultSet.next();
        return resultSet.getLong(1);
      }
    }
  }
}
