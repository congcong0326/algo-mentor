package org.congcong.algomentor.ops.observability;

public enum OpsLogEventType {

  HTTP_REQUEST_FAILED("http_request_failed"),
  SSE_CONNECTION_OPENED("sse_connection_opened"),
  SSE_CONNECTION_COMPLETED("sse_connection_completed"),
  SSE_CONNECTION_CLIENT_DISCONNECTED("sse_connection_client_disconnected"),
  SSE_CONNECTION_FAILED("sse_connection_failed"),
  SSE_CONNECTION_TIMEOUT("sse_connection_timeout"),
  AGENT_RUN_STARTED("agent_run_started"),
  AGENT_RUN_COMPLETED("agent_run_completed"),
  AGENT_RUN_FAILED("agent_run_failed"),
  AGENT_LLM_REQUEST_STARTED("agent_llm_request_started"),
  AGENT_LLM_STEP_COMPLETED("agent_llm_step_completed"),
  AGENT_TOOL_STARTED("agent_tool_started"),
  AGENT_TOOL_COMPLETED("agent_tool_completed"),
  AGENT_TOOL_FAILED("agent_tool_failed"),
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
