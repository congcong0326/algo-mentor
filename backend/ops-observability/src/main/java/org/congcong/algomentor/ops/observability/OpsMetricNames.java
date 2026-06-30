package org.congcong.algomentor.ops.observability;

public final class OpsMetricNames {

  /** Active SSE connection gauge, tagged by stream_type. */
  public static final String SSE_CONNECTIONS_ACTIVE = "algo.mentor.sse.connections.active";
  /** Opened SSE connection counter, tagged by stream_type. */
  public static final String SSE_CONNECTIONS_OPENED = "algo.mentor.sse.connections.opened";
  /** Completed SSE connection counter, tagged by stream_type. */
  public static final String SSE_CONNECTIONS_COMPLETED = "algo.mentor.sse.connections.completed";
  /** Failed SSE connection counter, tagged by stream_type and failure_type. */
  public static final String SSE_CONNECTIONS_FAILED = "algo.mentor.sse.connections.failed";
  /** Timed out SSE connection counter, tagged by stream_type. */
  public static final String SSE_CONNECTIONS_TIMEOUT = "algo.mentor.sse.connections.timeout";
  /** Client-disconnected SSE connection counter, tagged by stream_type. */
  public static final String SSE_CONNECTIONS_CLIENT_DISCONNECTED =
      "algo.mentor.sse.connections.client_disconnected";
  /** Agent run lifecycle counter, tagged by source and status. */
  public static final String AGENT_RUNS = "algo.mentor.agent.runs";
  /** Agent tool permission decision counter, tagged by decision. */
  public static final String AGENT_TOOL_PERMISSION_DECISIONS =
      "algo.mentor.agent.tool.permission.decisions";
  /** Agent tool execution counter, tagged by tool_name and status. */
  public static final String AGENT_TOOL_EXECUTIONS = "algo.mentor.agent.tool.executions";
  /** Learning plan draft generation counter, tagged by status. */
  public static final String LEARNING_PLAN_DRAFT_GENERATIONS =
      "algo.mentor.learning_plan.draft.generations";
  /** Practice message stream counter, tagged by status. */
  public static final String PRACTICE_MESSAGE_STREAMS = "algo.mentor.practice.message.streams";
  /** Practice code review counter, tagged by status. */
  public static final String PRACTICE_CODE_REVIEWS = "algo.mentor.practice.code_reviews";

  private OpsMetricNames() {
  }

}
