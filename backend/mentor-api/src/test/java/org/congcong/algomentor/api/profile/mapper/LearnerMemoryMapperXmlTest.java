package org.congcong.algomentor.api.profile.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.Reader;
import java.util.stream.Collectors;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

class LearnerMemoryMapperXmlTest {

  private static final String RESOURCE = "mapper/profile/LearnerMemoryMapper.xml";
  private static final String NAMESPACE = "org.congcong.algomentor.api.profile.mapper.LearnerMemoryMapper.";

  @Test
  void mybatisLoadsAllLearnerMemoryStatements() throws Exception {
    Configuration configuration = new Configuration();
    configuration.setMapUnderscoreToCamelCase(true);

    try (Reader reader = Resources.getResourceAsReader(RESOURCE)) {
      new XMLMapperBuilder(reader, configuration, RESOURCE, configuration.getSqlFragments()).parse();
    }

    assertThat(configuration.hasStatement(NAMESPACE + "findActiveByUser")).isTrue();
    assertThat(configuration.hasStatement(NAMESPACE + "findActiveDocumentClaims")).isTrue();
    assertThat(configuration.hasStatement(NAMESPACE + "existsActiveDocumentClaim")).isTrue();
    assertThat(configuration.hasStatement(NAMESPACE + "findDocumentEvidenceByRevisionIds")).isTrue();
    assertThat(configuration.hasStatement(NAMESPACE + "findActiveDocumentEvidencePage")).isTrue();
    assertThat(configuration.hasStatement(NAMESPACE + "findActiveByScopes")).isTrue();
    assertThat(configuration.hasStatement(NAMESPACE + "findActiveByRevisionIds")).isTrue();
    assertThat(configuration.hasStatement(NAMESPACE + "findActiveByUserForUpdate")).isTrue();
    assertThat(configuration.hasStatement(NAMESPACE + "findReviewEvidenceByRevisionIds")).isTrue();
    assertThat(configuration.hasStatement(NAMESPACE + "findMessageEvidenceByRevisionIds")).isTrue();
    assertThat(configuration.hasStatement(NAMESPACE + "insertClaim")).isTrue();
    assertThat(configuration.hasStatement(NAMESPACE + "insertUpdateRun")).isTrue();
    assertThat(configuration.hasStatement(NAMESPACE + "insertUpdateRunReviews")).isTrue();
    assertThat(configuration.hasStatement(NAMESPACE + "findUpdateRunById")).isTrue();
  }

  @Test
  void mapperScopesReadsOrdersEvidenceByBusinessTimeAndLocksTheUser() throws Exception {
    String mapperXml;
    try (Reader reader = Resources.getResourceAsReader(RESOURCE);
        BufferedReader bufferedReader = new BufferedReader(reader)) {
      mapperXml = bufferedReader.lines().collect(Collectors.joining("\n"));
    }
    String normalized = mapperXml.replaceAll("\\s+", " ");

    assertThat(normalized)
        .contains("WHERE user_id = #{userId} AND status = 'ACTIVE' ORDER BY <include refid=\"StableClaimOrder\"/>")
        .contains("SELECT id FROM auth_users WHERE id = #{userId} FOR UPDATE")
        .contains("WHERE claim.user_id = #{userId} AND evidence.claim_revision_id IN")
        .contains("ORDER BY evidence.claim_revision_id, review.created_at ASC, evidence.review_id ASC")
        .contains("ORDER BY evidence.claim_revision_id, message.created_at ASC, evidence.message_id ASC")
        .contains("WHERE task.user_id = #{userId} AND message.id IN");
  }

  @Test
  void documentQueriesScopeActiveEvidenceAndExposeOnlySafeProjectionFields() throws Exception {
    String mapperXml;
    try (Reader reader = Resources.getResourceAsReader(RESOURCE);
        BufferedReader bufferedReader = new BufferedReader(reader)) {
      mapperXml = bufferedReader.lines().collect(Collectors.joining("\n"));
    }
    String normalized = mapperXml.replaceAll("\\s+", " ");

    assertThat(normalized)
        .contains("WHERE claim.user_id = #{userId} AND claim.status = 'ACTIVE'")
        .contains("JOIN practice_code_review review ON review.id = review_evidence.review_id AND review.user_id = claim.user_id")
        .contains("JOIN agent_task task ON task.id = message.task_id AND task.user_id = claim.user_id")
        .contains("LEFT(message.content, 240) AS message_excerpt")
        .contains("ORDER BY occurred_at ASC, source_type ASC, source_id ASC")
        .doesNotContain("raw_code", "normalized_code", "review_markdown");
  }
}
