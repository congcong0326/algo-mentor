package org.congcong.algomentor.agent.core.work;

/**
 * 面向普通用户的 Agent 后台工作状态 SSE 事件名。
 */
public final class AgentWorkStatusEventNames {

  /**
   * Agent run 已开始。
   */
  public static final String WORK_START = "work_start";

  /**
   * 模型正在输出可摘要的进度。
   */
  public static final String WORK_PROGRESS = "work_progress";

  /**
   * Agent 开始执行工具。
   */
  public static final String WORK_TOOL_START = "work_tool_start";

  /**
   * Agent 工具执行完成。
   */
  public static final String WORK_TOOL_END = "work_tool_end";

  /**
   * Agent run 已完成。
   */
  public static final String WORK_DONE = "work_done";

  /**
   * Agent run 失败。
   */
  public static final String WORK_ERROR = "work_error";

  private AgentWorkStatusEventNames() {
  }
}
