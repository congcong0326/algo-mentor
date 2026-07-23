package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.congcong.algomentor.identity.group.repository.mybatis.UserGroupMapper;
import org.junit.jupiter.api.Test;

class UserGroupMyBatisConfigurationTest {

  @Test
  void applicationMapperScanParsesUserGroupStatements() throws Exception {
    SqlSessionFactory factory = new MentorApiMyBatisConfiguration()
        .sqlSessionFactory(mock(DataSource.class), new ObjectMapper());

    String namespace = UserGroupMapper.class.getName();
    Configuration configuration = factory.getConfiguration();
    assertThat(configuration.hasStatement(namespace + ".searchGroups")).isTrue();
    assertThat(configuration.hasStatement(namespace + ".upsertMembership")).isTrue();
    assertThat(configuration.hasStatement(namespace + ".findActiveMembershipsByUserIds")).isTrue();

    assertThat(constructorTypes(configuration, namespace + ".UserGroupRowMap"))
        .containsExactly(
            Long.class,
            String.class,
            String.class,
            String.class,
            String.class,
            long.class,
            Instant.class,
            Instant.class,
            Instant.class,
            Long.class);
    assertThat(constructorTypes(configuration, namespace + ".UserGroupMemberRowMap"))
        .containsExactly(
            long.class,
            String.class,
            String.class,
            String.class,
            String.class,
            Instant.class,
            Instant.class);
    assertThat(constructorTypes(configuration, namespace + ".MembershipRowMap"))
        .containsExactly(
            long.class,
            long.class,
            Instant.class,
            Instant.class,
            Instant.class,
            Instant.class);
    assertThat(constructorTypes(configuration, namespace + ".MembershipSummaryRowMap"))
        .containsExactly(
            long.class,
            long.class,
            String.class,
            String.class,
            Instant.class,
            Instant.class);
  }

  private List<Class<?>> constructorTypes(Configuration configuration, String resultMapId) {
    return configuration.getResultMap(resultMapId).getResultMappings().stream()
        .filter(mapping -> mapping.getFlags().contains(ResultFlag.CONSTRUCTOR))
        .<Class<?>>map(mapping -> mapping.getJavaType())
        .toList();
  }
}
