package org.congcong.algomentor.api.review.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Reader;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.congcong.algomentor.agent.persistence.postgres.json.JsonbTypeHandler;
import org.junit.jupiter.api.Test;

class ProblemReviewMapperXmlTest {

  @Test
  void mybatisLoadsAllProblemReviewMappers() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.getTypeHandlerRegistry().register(
        com.fasterxml.jackson.databind.JsonNode.class,
        new JsonbTypeHandler(new ObjectMapper()));
    configuration.getTypeHandlerRegistry().register(new JsonbTypeHandler(new ObjectMapper()));

    parse(configuration, "mapper/review/ProblemReviewCardMapper.xml");
    parse(configuration, "mapper/review/ProblemReviewAttemptMapper.xml");
    parse(configuration, "mapper/review/UserProblemNoteMapper.xml");

    assertStatements(configuration, ProblemReviewCardMapper.class,
        "upsertForReview", "mark", "findForUser", "findForUpdate", "findByUserAndSlug",
        "findDue", "list", "countDue", "countScheduledBefore", "findNextDueAt",
        "updateArchived", "updateScheduling");
    assertStatements(configuration, ProblemReviewAttemptMapper.class,
        "findByUserAndClientAttemptId", "insertIfAbsent", "findRecent");
    assertStatements(configuration, UserProblemNoteMapper.class,
        "findSummary", "find", "insert", "update", "delete");
  }

  private void assertStatements(Configuration configuration, Class<?> mapper, String... statementIds) {
    for (String statementId : statementIds) {
      assertThat(configuration.hasStatement(mapper.getName() + "." + statementId))
          .as("MyBatis statement %s.%s", mapper.getSimpleName(), statementId)
          .isTrue();
    }
  }

  private void parse(Configuration configuration, String resource) throws Exception {
    try (Reader reader = Resources.getResourceAsReader(resource)) {
      new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
    }
  }
}
