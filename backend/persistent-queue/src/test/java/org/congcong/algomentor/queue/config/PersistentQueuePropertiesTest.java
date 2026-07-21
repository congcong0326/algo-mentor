package org.congcong.algomentor.queue.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PersistentQueuePropertiesTest {

  @Test
  void hasSafeRuntimeDefaults() {
    PersistentQueueProperties properties = new PersistentQueueProperties();

    assertThat(properties.getConsumer().isEnabled()).isFalse();
    assertThat(properties.getConsumer().getPollInterval()).isEqualTo(Duration.ofSeconds(10));
    assertThat(properties.getConsumer().getShutdownTimeout()).isEqualTo(Duration.ofSeconds(30));
    assertThat(properties.getCleanup().getSucceededRetention()).isEqualTo(Duration.ofDays(7));
    assertThat(properties.getCleanup().getFixedDelay()).isEqualTo(Duration.ofHours(1));
    assertThat(properties.getCleanup().getBatchSize()).isEqualTo(1_000);
  }

  @Test
  void rejectsNonPositiveRuntimeSettings() {
    PersistentQueueProperties properties = new PersistentQueueProperties();

    assertThatThrownBy(() -> properties.getConsumer().setPollInterval(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties.getConsumer().setShutdownTimeout(Duration.ofSeconds(-1))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties.getCleanup().setSucceededRetention(Duration.ZERO)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties.getCleanup().setFixedDelay(null)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> properties.getCleanup().setBatchSize(0)).isInstanceOf(IllegalArgumentException.class);
  }
}
