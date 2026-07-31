package org.congcong.algomentor.ops.observability;

public enum AgentOpsSource {

  LEARNING_PLAN_DRAFT("learning_plan_draft"),
  PRACTICE_MESSAGE("practice_message"),
  AGENT_CONVERSATION("agent_conversation");

  private final String tagValue;

  AgentOpsSource(String tagValue) {
    this.tagValue = tagValue;
  }

  public String tagValue() {
    return tagValue;
  }

  /** 将 Runtime 注入的稳定 Agent key 收敛为低基数指标来源。 */
  public static AgentOpsSource fromAgentKey(Object agentKey) {
    if (!(agentKey instanceof String key) || key.isBlank()) {
      return AGENT_CONVERSATION;
    }
    return switch (key) {
      case "learning-plan-draft", "learning-plan-revision", "learning-plan-extension" -> LEARNING_PLAN_DRAFT;
      case "practice-chat" -> PRACTICE_MESSAGE;
      default -> AGENT_CONVERSATION;
    };
  }

}
