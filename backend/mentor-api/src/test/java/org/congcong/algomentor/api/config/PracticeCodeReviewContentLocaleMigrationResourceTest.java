package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class PracticeCodeReviewContentLocaleMigrationResourceTest {

  @Test
  void v52AddsDefaultedAndConstrainedReviewContentLocale() throws Exception {
    String sql = new ClassPathResource(
        "db/migration/V52__practice_code_review_content_locale.sql")
        .getContentAsString(StandardCharsets.UTF_8);

    assertThat(sql)
        .contains("content_locale VARCHAR(16) NOT NULL DEFAULT 'zh-CN'")
        .contains("ck_practice_code_review_content_locale")
        .contains("content_locale IN ('zh-CN', 'en-US')");
  }
}
