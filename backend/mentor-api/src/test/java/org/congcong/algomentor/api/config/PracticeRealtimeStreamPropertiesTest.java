package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class PracticeRealtimeStreamPropertiesTest {

  @Test
  void defaultsProvideTimeForBlockedXreadToReturnNormally() {
    PracticeRealtimeStreamProperties properties = new PracticeRealtimeStreamProperties();

    properties.validate();

    assertThat(properties.getCommandTimeout()).isGreaterThan(properties.getReadBlock());
    assertThat(properties.getMaxReadConnections()).isPositive();
  }

  @Test
  void rejectsACommandTimeoutThatCannotCoverBlockedXread() {
    PracticeRealtimeStreamProperties properties = new PracticeRealtimeStreamProperties();
    properties.setReadBlock(Duration.ofSeconds(3));
    properties.setCommandTimeout(Duration.ofSeconds(3));

    assertThatThrownBy(properties::validate)
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Practice realtime Redis commandTimeout must be greater than readBlock");
  }

  @Test
  void rejectsNonPositiveReadConnectionLimits() {
    PracticeRealtimeStreamProperties properties = new PracticeRealtimeStreamProperties();

    assertThatThrownBy(() -> properties.setMaxReadConnections(0))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Practice realtime Redis maxReadConnections must be positive");
  }
}
