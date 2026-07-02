package org.congcong.algomentor.api.review.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.Reader;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.congcong.algomentor.agent.persistence.postgres.json.JsonbTypeHandler;
import org.junit.jupiter.api.Test;

class MistakeReviewMapperXmlTest {

  @Test
  void mybatisLoadsMistakeReviewMapperXml() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);
    configuration.getTypeHandlerRegistry().register(
        com.fasterxml.jackson.databind.JsonNode.class,
        new JsonbTypeHandler(new ObjectMapper()));
    configuration.getTypeHandlerRegistry().register(new JsonbTypeHandler(new ObjectMapper()));

    parse(configuration, "mapper/review/MistakeNoteMapper.xml");
    parse(configuration, "mapper/review/ReviewLogMapper.xml");

    String noteNamespace = "org.congcong.algomentor.api.review.mapper.MistakeNoteMapper.";
    assertThat(configuration.hasStatement(noteNamespace + "upsertForReviewFailure")).isTrue();
    assertThat(configuration.hasStatement(noteNamespace + "mark")).isTrue();
    assertThat(configuration.hasStatement(noteNamespace + "findDue")).isTrue();
    assertThat(configuration.hasStatement(noteNamespace + "updateScheduling")).isTrue();
    assertThat(configuration.hasStatement(noteNamespace + "savePendingCard")).isTrue();

    String logNamespace = "org.congcong.algomentor.api.review.mapper.ReviewLogMapper.";
    assertThat(configuration.hasStatement(logNamespace + "insert")).isTrue();
  }

  private void parse(Configuration configuration, String resource) throws Exception {
    try (Reader reader = Resources.getResourceAsReader(resource)) {
      new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
    }
  }
}
