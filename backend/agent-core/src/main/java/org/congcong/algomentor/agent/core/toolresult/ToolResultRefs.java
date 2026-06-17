package org.congcong.algomentor.agent.core.toolresult;

/**
 * 工具结果引用格式约定。
 */
public final class ToolResultRefs {

  /**
   * resultRef 的统一前缀，后缀为持久化 blob ID 或 fallback hash。
   */
  public static final String PREFIX = "tool-result:";

  private ToolResultRefs() {
  }
}
