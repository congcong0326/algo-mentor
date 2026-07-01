package org.congcong.algomentor.ops.observability;

public enum SseStreamType {

  AI_EXPLANATION("ai_explanation"),
  LEARNING_PLAN_DRAFT("learning_plan_draft"),
  LEARNING_PLAN_PROPOSAL("learning_plan_proposal"),
  PRACTICE_MESSAGE("practice_message"),
  AGENT_CONVERSATION("agent_conversation");

  private final String tagValue;

  SseStreamType(String tagValue) {
    this.tagValue = tagValue;
  }

  public String tagValue() {
    return tagValue;
  }

}
