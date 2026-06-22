package org.congcong.algomentor.api.problem.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Reader;
import org.congcong.algomentor.agent.persistence.postgres.json.JsonbTypeHandler;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ProblemMapperXmlTest {

  @Test
  void mybatisLoadsProblemMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.getTypeHandlerRegistry().register(
        com.fasterxml.jackson.databind.JsonNode.class,
        new JsonbTypeHandler(new ObjectMapper()));
    configuration.getTypeHandlerRegistry().register(new JsonbTypeHandler(new ObjectMapper()));

    try (Reader reader = Resources.getResourceAsReader("mapper/problem/ProblemMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/problem/ProblemMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.findProblems")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.findProblemBySlug")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.countAllProblems")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.countProblemsByDifficulty")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.countProblemsByTag")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.countProblemCategories")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.upsertProblem")).isTrue();
  }

  @Test
  void mybatisLoadsLearningPlanMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    try (Reader reader = Resources.getResourceAsReader("mapper/learningplan/LearningPlanMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/learningplan/LearningPlanMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.insertDraft")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.findDraftByIdForUser")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.insertPlan")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.learningplan.mapper.LearningPlanMapper.findPlansByUserId")).isTrue();
  }
}
