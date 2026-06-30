package org.congcong.algomentor.ops.observability;

public enum OpsStatus {

  STARTED("started"),
  COMPLETED("completed"),
  FAILED("failed"),
  UNREVIEWABLE("unreviewable");

  private final String tagValue;

  OpsStatus(String tagValue) {
    this.tagValue = tagValue;
  }

  public String tagValue() {
    return tagValue;
  }

}
