package org.congcong.algomentor.policy.repository.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class GenericPolicyMapperXmlTest {

  private static final String MAPPER_RESOURCE = "mapper/policy/GenericPolicyMapper.xml";
  private static final String TYPE_LOCK_STATEMENT =
      "org.congcong.algomentor.policy.repository.mybatis.GenericPolicyMapper.acquireTypeLock";

  @Test
  void typeLockReturnsMappableInteger() throws Exception {
    Configuration configuration = new Configuration();
    try (Reader reader = Resources.getResourceAsReader(MAPPER_RESOURCE)) {
      new XMLMapperBuilder(
              reader, configuration, MAPPER_RESOURCE, configuration.getSqlFragments())
          .parse();
    }

    MappedStatement statement = configuration.getMappedStatement(TYPE_LOCK_STATEMENT);
    assertThat(statement.getResultMaps())
        .singleElement()
        .satisfies(resultMap -> assertThat(resultMap.getType()).isEqualTo(Integer.class));
    assertThat(statement.getBoundSql(Map.of("typeCode", "session-limit")).getSql())
        .contains("select 1")
        .contains("pg_advisory_xact_lock");
  }
}
