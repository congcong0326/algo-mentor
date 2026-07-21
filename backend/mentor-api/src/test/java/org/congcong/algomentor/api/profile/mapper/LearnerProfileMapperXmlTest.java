package org.congcong.algomentor.api.profile.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class LearnerProfileMapperXmlTest {

  @Test
  void mybatisLoadsLearnerProfileDisplayQuery() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    try (Reader reader = Resources.getResourceAsReader("mapper/profile/LearnerProfileMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/profile/LearnerProfileMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.profile.mapper.LearnerProfileMapper.findCurrentForDisplay")).isTrue();
  }

  @Test
  void displayQueryReturnsOnlyActiveEntriesAndJoinsTrustedTagLabels() throws Exception {
    String mapperXml;
    try (Reader reader = Resources.getResourceAsReader("mapper/profile/LearnerProfileMapper.xml");
        BufferedReader bufferedReader = new BufferedReader(reader)) {
      mapperXml = bufferedReader.lines().collect(Collectors.joining("\n"));
    }
    String normalized = mapperXml.replaceAll("\\s+", " ");

    assertThat(normalized)
        .contains("LEFT JOIN problem_tag tag ON tag.id = profile_entry.tag_id")
        .contains("WHERE profile_entry.user_id = #{userId}")
        .contains("AND profile_entry.status = 'ACTIVE'")
        .doesNotContain("profile_entry.model_name", "profile_entry.prompt_version");
  }
}
