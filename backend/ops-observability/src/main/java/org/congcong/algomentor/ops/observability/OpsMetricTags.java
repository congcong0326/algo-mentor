package org.congcong.algomentor.ops.observability;

public final class OpsMetricTags {

  /** Low-cardinality SSE stream type tag key. */
  public static final String STREAM_TYPE = "stream_type";
  /** Low-cardinality SSE failure type tag key. */
  public static final String FAILURE_TYPE = "failure_type";
  /** Low-cardinality agent source tag key. */
  public static final String SOURCE = "source";
  /** Low-cardinality lifecycle/result status tag key. */
  public static final String STATUS = "status";
  /** Low-cardinality tool permission decision tag key. */
  public static final String DECISION = "decision";
  /** Low-cardinality agent tool name tag key. */
  public static final String TOOL_NAME = "tool_name";

  private OpsMetricTags() {
  }

}
