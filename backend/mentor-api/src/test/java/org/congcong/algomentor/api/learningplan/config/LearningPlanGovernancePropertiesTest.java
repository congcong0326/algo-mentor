package org.congcong.algomentor.api.learningplan.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class LearningPlanGovernancePropertiesTest {

  @Test
  void exposesValidatedQuotaZoneAndCleanupDefaults() {
    LearningPlanGovernanceProperties properties = new LearningPlanGovernanceProperties();

    assertThat(properties.quotaZoneId()).isEqualTo(ZoneId.of("UTC"));
    assertThat(properties.getCleanup().isEnabled()).isTrue();
    assertThat(properties.getCleanup().getFixedDelay()).isEqualTo(Duration.ofHours(1));
    assertThat(properties.getCleanup().getBatchSize()).isEqualTo(1_000);
    assertThat(properties.getCleanup().getDailyUsageRetentionDays()).isEqualTo(30);
  }

  @Test
  void rejectsInvalidOperationalValues() {
    LearningPlanGovernanceProperties properties = new LearningPlanGovernanceProperties();

    assertThatThrownBy(() -> properties.setQuotaZone("not-a-zone"))
        .isInstanceOf(RuntimeException.class);
    assertThatThrownBy(() -> properties.getCleanup().setFixedDelay(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties.getCleanup().setFixedDelay(Duration.ofNanos(1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties.getCleanup().setBatchSize(0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
