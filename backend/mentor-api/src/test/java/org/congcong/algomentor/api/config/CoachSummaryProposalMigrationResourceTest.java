package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class CoachSummaryProposalMigrationResourceTest {

  @Test
  void v58AddsIndependentSummaryRevisionAndOnePendingProposalPerProblem() throws Exception {
    String sql = new ClassPathResource("db/migration/V58__practice_coach_summary_proposal.sql")
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(sql)
        .contains("coach_summary_revision BIGINT NOT NULL DEFAULT 0")
        .contains("coach_summary_updated_at TIMESTAMPTZ NULL")
        .contains("CREATE TABLE IF NOT EXISTS practice_coach_summary_proposal")
        .contains("UNIQUE (source_run_id, source_tool_call_id)")
        .contains("status IN ('PENDING', 'APPLIED', 'SUPERSEDED')")
        .contains("WHERE status = 'PENDING'");
  }
}
