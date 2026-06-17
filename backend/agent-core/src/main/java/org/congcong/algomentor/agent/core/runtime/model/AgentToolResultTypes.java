package org.congcong.algomentor.agent.core.runtime.model;

/**
 * Agent 工具结果 JSON 的 type 字段取值。
 */
public final class AgentToolResultTypes {

  /**
   * 大工具结果只向模型暴露预览，完整内容通过 resultRef 读取。
   */
  public static final String PREVIEW = "tool_result_preview";

  /**
   * read_tool_result 返回的有界范围片段。
   */
  public static final String RANGE = "tool_result_range";

  /**
   * run 内较早工具结果被上下文压缩后留下的占位。
   */
  public static final String COMPACTED = "tool_result_compacted";

  private AgentToolResultTypes() {
  }
}
