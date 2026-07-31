package org.congcong.algomentor.api.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.congcong.algomentor.api.support.PostgresIntegrationTestSupport;
import org.junit.jupiter.api.Test;

class LearnerMemoryCleanInstallIT extends PostgresIntegrationTestSupport {

  @Test
  void createsOnlyTheMemoryStorageOnAnEmptySchema() throws Exception {
    migrateLatest();

    assertThat(count("learner_memory_update_run")).isZero();
    assertThat(count("learner_memory_update_run_review")).isZero();
    assertThat(count("learner_memory_claim_revision")).isZero();
    assertThat(count("learner_memory_claim_review_evidence")).isZero();
    assertThat(count("learner_memory_claim_message_evidence")).isZero();
    assertThatThrownBy(() -> count("learner_profile_entry")).isInstanceOf(Exception.class);
  }
}
