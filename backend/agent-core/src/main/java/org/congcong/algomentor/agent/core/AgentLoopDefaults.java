package org.congcong.algomentor.agent.core;

/**
 * Agent loop 运行时默认值。
 */
public final class AgentLoopDefaults {

  /**
   * 单次 Agent loop 默认最大步数，防止工具调用无限循环。
   */
  public static final int DEFAULT_MAX_STEPS = 50;

  private AgentLoopDefaults() {
  }
}
