package org.congcong.algomentor.agent.core;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AgentToolRegistryTest {

  @Test
  void rejectsNullToolCollection() {
    assertThatThrownBy(() -> AgentToolRegistry.of(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessage("agent tools must not be null");
  }
}
