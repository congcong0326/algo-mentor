package org.congcong.algomentor.api.ability.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AbilityProfileMapperXmlTest {

  @Test
  void mybatisLoadsAbilityProfileMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    try (Reader reader = Resources.getResourceAsReader("mapper/ability/AbilityProfileMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/ability/AbilityProfileMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    String namespace = "org.congcong.algomentor.api.ability.mapper.AbilityProfileMapper.";
    assertThat(configuration.hasStatement(namespace + "findCommonTagScores")).isTrue();
  }

  @Test
  void queryKeepsCommonTagsAndUsesLatestReviewPerProblem() throws Exception {
    String mapperXml;
    try (Reader reader = Resources.getResourceAsReader("mapper/ability/AbilityProfileMapper.xml");
        BufferedReader bufferedReader = new BufferedReader(reader)) {
      mapperXml = bufferedReader.lines().collect(Collectors.joining("\n"));
    }
    String normalizedMapperXml = mapperXml.replaceAll("\\s+", " ");

    assertThat(normalizedMapperXml).contains(
        "ROW_NUMBER() OVER (PARTITION BY problem_slug ORDER BY created_at DESC, id DESC)");
    assertThat(normalizedMapperXml).contains("CROSS JOIN LATERAL unnest(");
    assertThat(normalizedMapperXml).contains("LEFT JOIN tag_review_scores");
    assertThat(normalizedMapperXml).contains("HAVING COUNT(*) >= #{minProblemCount}");
    assertThat(normalizedMapperXml).contains("ORDER BY tc.problem_count DESC, tc.tag ASC");
  }
}
