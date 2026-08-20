package org.congcong.algomentor.api.learningplan.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.Reader;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class LearningPlanMapperXmlTest {

  private static final String MAPPER_RESOURCE = "mapper/learningplan/LearningPlanMapper.xml";
  private static final String DRAFT_REVISION_GENERATION_LOCK_STATEMENT =
      LearningPlanMapper.class.getName() + ".lockDraftRevisionGenerationRequest";

  @Test
  void draftRevisionGenerationLockReturnsMappableInteger() throws Exception {
    Configuration configuration = new Configuration();
    try (Reader reader = Resources.getResourceAsReader(MAPPER_RESOURCE)) {
      new XMLMapperBuilder(
          reader, configuration, MAPPER_RESOURCE, configuration.getSqlFragments()).parse();
    }

    MappedStatement statement = configuration.getMappedStatement(DRAFT_REVISION_GENERATION_LOCK_STATEMENT);
    assertThat(statement.getResultMaps())
        .singleElement()
        .satisfies(resultMap -> assertThat(resultMap.getType()).isEqualTo(Integer.class));
    assertThat(statement.getBoundSql(Map.of(
        "userId", 1L,
        "draftId", 2L,
        "requestKey", "request-key")).getSql())
        .contains("SELECT 1")
        .contains("pg_advisory_xact_lock");
  }
}
