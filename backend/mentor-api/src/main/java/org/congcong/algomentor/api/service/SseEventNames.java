package org.congcong.algomentor.api.service;

/**
 * 后端 SSE 事件名，前端 EventSource 监听这些稳定字符串。
 */
public final class SseEventNames {

  /**
   * Agent run 开始。
   */
  public static final String AGENT_RUN_START = "agent_run_start";

  /**
   * Agent 单步开始。
   */
  public static final String AGENT_STEP_START = "agent_step_start";

  /**
   * Agent 工具调用开始。
   */
  public static final String AGENT_TOOL_START = "agent_tool_start";

  /**
   * Agent 工具调用结束。
   */
  public static final String AGENT_TOOL_END = "agent_tool_end";

  /**
   * Agent 工具权限申请。
   */
  public static final String TOOL_PERMISSION_REQUEST = "tool_permission_request";

  /**
   * Agent 工具权限决策。
   */
  public static final String TOOL_PERMISSION_DECISION = "tool_permission_decision";

  /**
   * Agent 工具权限等待超时。
   */
  public static final String TOOL_PERMISSION_TIMEOUT = "tool_permission_timeout";

  /**
   * Agent 单步结束。
   */
  public static final String AGENT_STEP_END = "agent_step_end";

  /**
   * Agent run 结束。
   */
  public static final String AGENT_RUN_END = "agent_run_end";

  /**
   * Agent 层错误。
   */
  public static final String AGENT_ERROR = "agent_error";

  /**
   * LLM 消息开始。
   */
  public static final String MESSAGE_START = "message_start";

  /**
   * LLM 内容增量。
   */
  public static final String CONTENT_DELTA = "content_delta";

  /**
   * LLM 工具调用开始。
   */
  public static final String TOOL_CALL_START = "tool_call_start";

  /**
   * LLM 工具调用参数增量。
   */
  public static final String TOOL_CALL_DELTA = "tool_call_delta";

  /**
   * LLM 工具调用结束。
   */
  public static final String TOOL_CALL_END = "tool_call_end";

  /**
   * LLM token 使用量。
   */
  public static final String USAGE = "usage";

  /**
   * LLM 消息结束。
   */
  public static final String MESSAGE_END = "message_end";

  /**
   * LLM provider 错误。
   */
  public static final String ERROR = "error";

  /**
   * SSE 心跳。
   */
  public static final String HEARTBEAT = "heartbeat";

  private SseEventNames() {
  }
}
