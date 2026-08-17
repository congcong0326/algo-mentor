package org.congcong.algomentor.agent.persistence.postgres.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class AgentRunMetadataMigrationTest {

  @Test
  void migrationAddsRunMetadataWithAnEmptyObjectDefault() throws Exception {
    ClassPathResource resource = new ClassPathResource(
        "db/migration/agent/V67__agent_run_metadata.sql");

    assertThat(resource.exists()).isTrue();
    assertThat(resource.getContentAsString(StandardCharsets.UTF_8))
        .contains("ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}'::JSONB");
  }
}
