package org.congcong.algomentor.agent.core.toolresult;

/** 不含结果正文的工具结果来源，供范围读取策略进行受限授权。 */
public record ToolResultProvenance(int stepIndex, String toolCallId, String toolName) {

  public ToolResultProvenance {
    if (stepIndex < 0) {
      throw new IllegalArgumentException("stepIndex must not be negative");
    }
    toolCallId = normalize(toolCallId);
    toolName = normalize(toolName);
  }

  public static ToolResultProvenance unknown() {
    return new ToolResultProvenance(0, "", "");
  }

  public boolean isKnown() {
    return !toolCallId.isBlank() && !toolName.isBlank();
  }

  private static String normalize(String value) {
    return value == null ? "" : value.trim();
  }
}
