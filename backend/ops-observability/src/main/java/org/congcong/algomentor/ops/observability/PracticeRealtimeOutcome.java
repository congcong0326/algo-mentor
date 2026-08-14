package org.congcong.algomentor.ops.observability;

/** Practice Chat Redis Stream 操作结果的低基数分类。 */
public enum PracticeRealtimeOutcome {

  SUCCESS("success"),
  FAILURE("failure");

  private final String tagValue;

  PracticeRealtimeOutcome(String tagValue) {
    this.tagValue = tagValue;
  }

  public String tagValue() {
    return tagValue;
  }
}
