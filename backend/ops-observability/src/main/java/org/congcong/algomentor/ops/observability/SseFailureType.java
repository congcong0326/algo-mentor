package org.congcong.algomentor.ops.observability;

public enum SseFailureType {

  SEND_FAILURE("send_failure"),
  UPSTREAM_ERROR("upstream_error"),
  TIMEOUT("timeout"),
  UNKNOWN("unknown");

  private final String tagValue;

  SseFailureType(String tagValue) {
    this.tagValue = tagValue;
  }

  public String tagValue() {
    return tagValue;
  }

}
