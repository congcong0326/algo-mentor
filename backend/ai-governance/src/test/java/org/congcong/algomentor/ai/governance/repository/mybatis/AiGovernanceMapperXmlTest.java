package org.congcong.algomentor.ai.governance.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.junit.jupiter.api.Test;

class AiGovernanceMapperXmlTest {

  @Test
  void mapperXmlFilesParse() throws Exception {
    org.apache.ibatis.session.Configuration configuration = new org.apache.ibatis.session.Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    for (String mapper : List.of(
        "mapper/ai/AiDailyUsageMapper.xml",
        "mapper/ai/AiRunAdmissionMapper.xml")) {
      try (InputStream input = getClass().getClassLoader().getResourceAsStream(mapper)) {
        assertThat(input).as(mapper).isNotNull();
        new XMLMapperBuilder(input, configuration, mapper, configuration.getSqlFragments()).parse();
      }
    }
  }
}
