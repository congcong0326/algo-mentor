package org.congcong.algomentor.api.practice.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.Reader;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.congcong.algomentor.agent.persistence.postgres.json.JsonbTypeHandler;
import org.junit.jupiter.api.Test;

class PracticeCodeReviewMapperXmlTest {

  @Test
  void mybatisLoadsPracticeCodeReviewMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.getTypeHandlerRegistry().register(
        com.fasterxml.jackson.databind.JsonNode.class,
        new JsonbTypeHandler(new ObjectMapper()));
    configuration.getTypeHandlerRegistry().register(new JsonbTypeHandler(new ObjectMapper()));

    try (Reader reader = Resources.getResourceAsReader("mapper/practice/PracticeCodeReviewMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/practice/PracticeCodeReviewMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    String namespace = "org.congcong.algomentor.api.practice.mapper.PracticeCodeReviewMapper.";
    assertThat(configuration.hasStatement(namespace + "insert")).isTrue();
    assertThat(configuration.hasStatement(namespace + "lockSessionForReviewInsert")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findLatest")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findLatestSummary")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findSummaries")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findById")).isTrue();
    assertThat(configuration.hasStatement(namespace + "findByUserMessage")).isTrue();
  }

  @Test
  void usesSeparateSessionLockBeforeInsertVersionCalculation() throws Exception {
    String mapperXml;
    try (Reader reader = Resources.getResourceAsReader("mapper/practice/PracticeCodeReviewMapper.xml");
        BufferedReader bufferedReader = new BufferedReader(reader)) {
      mapperXml = bufferedReader.lines().collect(Collectors.joining("\n"));
    }
    String normalizedMapperXml = mapperXml.replaceAll("\\s+", " ");

    assertThat(normalizedMapperXml).contains(
        "<select id=\"lockSessionForReviewInsert\" resultMap=\"PracticeCodeReviewSessionLockRowMap\"> "
            + "SELECT id, plan_id, phase_index, problem_slug FROM practice_session "
            + "WHERE id = #{sessionId} AND user_id = #{userId} AND status = 'ACTIVE' FOR UPDATE </select>");
    assertThat(normalizedMapperXml).contains(
        "next_version AS ( SELECT COALESCE(MAX(version_no), 0) + 1 AS version_no "
            + "FROM practice_code_review WHERE practice_session_id = #{sessionId} )");
    assertThat(normalizedMapperXml).doesNotContain("locked_session AS");
    assertThat(normalizedMapperXml).doesNotContain("FROM locked_session");
  }
}
