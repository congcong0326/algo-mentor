package org.congcong.algomentor.ops.observability;

public enum AgentOpsSource {

  AI_EXPLANATION("ai_explanation"),
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

}
