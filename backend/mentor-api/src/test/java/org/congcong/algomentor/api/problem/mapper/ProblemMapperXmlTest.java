package org.congcong.algomentor.api.problem.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ProblemMapperXmlTest {

  @Test
  void mybatisLoadsProblemMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

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
        "org.congcong.algomentor.api.problem.mapper.ProblemMapper.upsertProblem")).isTrue();
  }
}
