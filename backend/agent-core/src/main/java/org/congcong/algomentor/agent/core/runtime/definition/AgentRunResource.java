package org.congcong.algomentor.agent.core.runtime.definition;

/** 由 Definition 获取并由 Runtime 在单次 run 终态释放的外部资源。 */
@FunctionalInterface
public interface AgentRunResource {

  AgentRunResource NONE = () -> {
  };

  void release();

  static AgentRunResource none() {
    return NONE;
  }
}
