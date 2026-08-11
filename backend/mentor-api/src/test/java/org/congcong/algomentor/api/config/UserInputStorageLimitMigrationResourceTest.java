package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class UserInputStorageLimitMigrationResourceTest {

  @Test
  void v59AddsFixedDatabaseSafetyCeilings() throws Exception {
    String sql = new ClassPathResource("db/migration/V59__user_input_storage_limits.sql")
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(sql)
        .contains("ck_user_problem_note_outline_storage_bytes")
        .contains("octet_length(outline_json::TEXT) <= 65536")
        .contains("char_length(outline_json ->> 'coreIdea')")
        .contains("ck_user_problem_note_outline_text_fields")
        .contains("ck_learning_plan_draft_command_storage_bytes")
        .contains("octet_length(command_json::TEXT) <= 65536");
  }
}
