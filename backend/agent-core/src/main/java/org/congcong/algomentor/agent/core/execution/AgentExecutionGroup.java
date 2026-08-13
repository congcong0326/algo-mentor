package org.congcong.algomentor.agent.core.execution;

import java.util.Arrays;
import java.util.Optional;

/**
 * Agent 执行容量的稳定业务分组。
 *
 * <p>{@link #code()} 是配置 key 与 Micrometer tag 的外部契约，不能以枚举名称替代。</p>
 */
public enum AgentExecutionGroup {
  PRACTICE("practice"),
  LEARNING_PLAN("learning-plan"),
  LEARNER_PROFILE_BACKGROUND("learner-profile-background");

  private final String code;

  AgentExecutionGroup(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }

  /** 按稳定外部 code 解析已注册执行组。 */
  public static Optional<AgentExecutionGroup> fromCode(String code) {
    if (code == null || code.isBlank()) {
      return Optional.empty();
    }
    return Arrays.stream(values()).filter(group -> group.code.equals(code)).findFirst();
  }
}
