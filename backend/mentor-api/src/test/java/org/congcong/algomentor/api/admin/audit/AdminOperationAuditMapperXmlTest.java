package org.congcong.algomentor.api.admin.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class AdminOperationAuditMapperXmlTest {

  @Test
  void mapperLoadsAndCastsMetadataToJsonb() throws Exception {
    Configuration configuration = new Configuration();
    try (Reader reader = Resources.getResourceAsReader("mapper/admin/AdminOperationAuditMapper.xml")) {
      new XMLMapperBuilder(
          reader,
          configuration,
          "mapper/admin/AdminOperationAuditMapper.xml",
          configuration.getSqlFragments()).parse();
    }

    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.api.admin.audit.AdminOperationAuditMapper.insert")).isTrue();
  }
}
