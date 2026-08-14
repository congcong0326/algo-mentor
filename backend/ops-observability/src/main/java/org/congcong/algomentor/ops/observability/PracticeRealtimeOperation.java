package org.congcong.algomentor.ops.observability;

/** Practice Chat Redis Stream 的低基数操作分类。 */
public enum PracticeRealtimeOperation {

  APPEND("append"),
  EXPIRE("expire"),
  READ("read"),
  CONNECT("connect"),
  RECONNECT("reconnect");

  private final String tagValue;

  PracticeRealtimeOperation(String tagValue) {
    this.tagValue = tagValue;
  }

  public String tagValue() {
    return tagValue;
  }
}
