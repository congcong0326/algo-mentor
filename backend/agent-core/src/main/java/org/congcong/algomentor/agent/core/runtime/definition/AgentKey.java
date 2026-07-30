package org.congcong.algomentor.agent.core.runtime.definition;

import java.util.Objects;

/** Agent Definition 的稳定标识及其允许的业务输入类型。 */
public record AgentKey<I>(String value, Class<I> inputType) {

  public AgentKey {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Agent key value must not be blank");
    }
    value = value.trim();
    inputType = Objects.requireNonNull(inputType, "Agent key input type must not be null");
  }
}
