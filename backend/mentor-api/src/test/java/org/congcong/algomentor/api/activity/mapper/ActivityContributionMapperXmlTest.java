package org.congcong.algomentor.api.activity.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class ActivityContributionMapperXmlTest {

  @Test
  void mybatisLoadsActivityContributionMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    try (Reader reader = Resources.getResourceAsReader("mapper/activity/ActivityContributionMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/activity/ActivityContributionMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.activity.mapper.ActivityContributionMapper.findDailyCounts")).isTrue();
  }

  @Test
  void groupsFormalReviewsByRequestedTimezoneAndBounds() throws Exception {
    String mapperXml;
    try (Reader reader = Resources.getResourceAsReader("mapper/activity/ActivityContributionMapper.xml");
        BufferedReader bufferedReader = new BufferedReader(reader)) {
      mapperXml = bufferedReader.lines().collect(Collectors.joining("\n"));
    }
    String normalized = mapperXml.replaceAll("\\s+", " ");

    assertThat(normalized)
        .contains("FROM practice_code_review")
        .contains("WHERE user_id = #{userId}")
        .contains("created_at &gt;= #{fromInclusive}")
        .contains("created_at &lt; #{toExclusive}")
        .contains("created_at AT TIME ZONE #{timezone}")
        .contains("GROUP BY activity_date")
        .contains("COUNT(*)::int AS activity_count");
  }
}
