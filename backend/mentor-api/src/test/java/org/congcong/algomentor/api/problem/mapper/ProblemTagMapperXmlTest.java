package org.congcong.algomentor.api.problem.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.congcong.algomentor.api.problem.mapper.model.ProblemTagAssignmentRow;
import org.congcong.algomentor.api.problem.mapper.model.ProblemTagCatalogUpsertRow;
import org.junit.jupiter.api.Test;

class ProblemTagMapperXmlTest {

  @Test
  void mybatisLoadsProblemTagMapperXmlAndUsesConditionalCatalogUpdates() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    try (Reader reader = Resources.getResourceAsReader("mapper/problem/ProblemTagMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/problem/ProblemTagMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    String namespace = "org.congcong.algomentor.api.problem.mapper.ProblemTagMapper.";
    assertThat(configuration.hasStatement(namespace + "upsertCatalog")).isTrue();
    assertThat(configuration.hasStatement(namespace + "deleteAssignmentsByProblemSlug")).isTrue();
    assertThat(configuration.hasStatement(namespace + "insertAssignments")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findArrayConsistencyViolationSlugs")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findAssignmentOrdinalViolationSlugs")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findInactiveCatalogValues")).isTrue();

    String catalogSql = configuration.getMappedStatement(namespace + "upsertCatalog")
        .getBoundSql(Map.of("rows", List.of(new ProblemTagCatalogUpsertRow("array", "Array", "数组"))))
        .getSql();
    assertThat(catalogSql).contains("ON CONFLICT (value)", "IS DISTINCT FROM", "active = TRUE");
    String assignmentSql = configuration.getMappedStatement(namespace + "insertAssignments")
        .getBoundSql(Map.of("rows", List.of(new ProblemTagAssignmentRow("two-sum", "array", 0))))
        .getSql();
    assertThat(assignmentSql).contains("problem_tag_assignment", "JOIN problem_tag pt", "ORDER BY assignments.ordinal ASC");
  }
}
