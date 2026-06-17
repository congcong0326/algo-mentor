package org.congcong.algomentor.agent.persistence.postgres.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Reader;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.congcong.algomentor.agent.persistence.postgres.json.AgentMessageRoleTypeHandler;
import org.congcong.algomentor.agent.persistence.postgres.json.JsonbTypeHandler;
import org.junit.jupiter.api.Test;

class AgentMapperXmlTest {

  @Test
  void mybatisLoadsAgentMapperXmlFiles() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.getTypeHandlerRegistry().register(new JsonbTypeHandler(new ObjectMapper()));
    configuration.getTypeHandlerRegistry().register(new AgentMessageRoleTypeHandler());

    for (String resource : mapperResources()) {
      try (Reader reader = Resources.getResourceAsReader(resource)) {
        new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
      }
    }

    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentConversationMapper.insertRun")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunMapper.markRunFailed")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentContextSnapshotMapper.insertSnapshot")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentRunTraceMapper.insertToolStart")).isTrue();
    assertThat(configuration.hasStatement(
        "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentContentBlobMapper.insertBlob")).isTrue();
    boolean hasPrimitiveLongConstructorArg = configuration.getResultMap(
            "org.congcong.algomentor.agent.persistence.postgres.mapper.AgentConversationMapper.AgentMessageMap")
        .getConstructorResultMappings()
        .stream()
        .map(mapping -> mapping.getJavaType())
        .anyMatch(long.class::equals);
    assertThat(hasPrimitiveLongConstructorArg).isTrue();
  }

  private List<String> mapperResources() {
    return List.of(
        "mapper/agent/AgentConversationMapper.xml",
        "mapper/agent/AgentRunMapper.xml",
        "mapper/agent/AgentRunTraceMapper.xml",
        "mapper/agent/AgentContentBlobMapper.xml",
        "mapper/agent/AgentContextSnapshotMapper.xml",
        "mapper/agent/AgentArtifactMapper.xml");
  }
}
