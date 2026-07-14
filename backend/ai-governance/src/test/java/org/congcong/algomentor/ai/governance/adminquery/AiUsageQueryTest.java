package org.congcong.algomentor.ai.governance.adminquery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.congcong.algomentor.ai.governance.model.AiGovernanceAdminException;
import org.congcong.algomentor.ai.governance.model.AiGovernanceErrorCode;
import org.junit.jupiter.api.Test;

class AiUsageQueryTest {

  @Test
  void normalizesProviderButKeepsExactModelAndBuildsInclusiveDateBounds() {
    AiUsageQuery query = AiUsageQuery.of(
        LocalDate.parse("2026-07-01"),
        LocalDate.parse("2026-07-03"),
        ZoneOffset.UTC,
        42L,
        " OpenAI ",
        " GPT-5.2 ",
        "LEARNING_CHAT",
        "PRACTICE_CHAT");

    assertThat(query.provider()).isEqualTo("openai");
    assertThat(query.model()).isEqualTo("GPT-5.2");
    assertThat(query.fromAt()).isEqualTo(Instant.parse("2026-07-01T00:00:00Z"));
    assertThat(query.toExclusive()).isEqualTo(Instant.parse("2026-07-04T00:00:00Z"));
  }

  @Test
  void rejectsRangesLongerThanNinetyInclusiveDaysAndInvalidUsers() {
    assertThatThrownBy(() -> AiUsageQuery.of(
        LocalDate.parse("2026-01-01"),
        LocalDate.parse("2026-04-01"),
        ZoneOffset.UTC,
        null,
        null,
        null,
        null,
        null))
        .isInstanceOfSatisfying(AiGovernanceAdminException.class,
            exception -> assertThat(exception.code()).isEqualTo(AiGovernanceErrorCode.AI_USAGE_DATE_RANGE_INVALID));

    assertThatThrownBy(() -> AiUsageQuery.of(
        LocalDate.parse("2026-07-01"),
        LocalDate.parse("2026-07-01"),
        ZoneOffset.UTC,
        0L,
        null,
        null,
        null,
        null))
        .isInstanceOfSatisfying(AiGovernanceAdminException.class,
            exception -> assertThat(exception.code()).isEqualTo(AiGovernanceErrorCode.AI_USAGE_QUERY_INVALID));
  }
}
