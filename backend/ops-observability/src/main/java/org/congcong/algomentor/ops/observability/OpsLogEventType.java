package org.congcong.algomentor.ops.observability;

public enum OpsLogEventType {

  HTTP_REQUEST_FAILED("http_request_failed"),
  SSE_CONNECTION_OPENED("sse_connection_opened"),
  SSE_CONNECTION_COMPLETED("sse_connection_completed"),
  SSE_CONNECTION_FAILED("sse_connection_failed"),
  SSE_CONNECTION_TIMEOUT("sse_connection_timeout"),
  AGENT_RUN_FAILED("agent_run_failed"),
  AGENT_TOOL_PERMISSION_TIMEOUT("agent_tool_permission_timeout"),
  LEARNING_PLAN_DRAFT_FAILED("learning_plan_draft_failed"),
  PRACTICE_MESSAGE_STREAM_FAILED("practice_message_stream_failed");

  private final String logValue;

  OpsLogEventType(String logValue) {
    this.logValue = logValue;
  }

  public String logValue() {
    return logValue;
  }

}
