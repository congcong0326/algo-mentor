package org.congcong.algomentor.agent.core.toolresult;

import org.congcong.algomentor.agent.core.AgentExecutionContext;

/**
 * 可选的范围读取授权扩展。
 *
 * <p>Guard 只接收当前执行上下文和工具结果 provenance，不接收 blob 正文。默认实现不改变通用
 * {@code read_tool_result} 行为。</p>
 */
public interface ToolResultReadGuard {

  ToolResultReadGuard NOOP = new ToolResultReadGuard() {
    @Override
    public ToolResultReadPermit beforeRead(
        AgentExecutionContext context, ToolResultProvenance provenance, int requestedMaxChars) {
      return ToolResultReadPermit.allow(requestedMaxChars);
    }

    @Override
    public void afterRead(ToolResultReadPermit permit, int visibleChars) {
      // No accounting is needed for generic range reads.
    }
  };

  ToolResultReadPermit beforeRead(
      AgentExecutionContext context, ToolResultProvenance provenance, int requestedMaxChars);

  void afterRead(ToolResultReadPermit permit, int visibleChars);

  /** Guard 为一次范围读取保留的可见字符上限或受控拒绝结果。 */
  record ToolResultReadPermit(
      int maxVisibleChars,
      String rejectionType,
      String rejectionMessage,
      Object trackingToken
  ) {

    public ToolResultReadPermit {
      if (maxVisibleChars < 0) {
        throw new IllegalArgumentException("maxVisibleChars must not be negative");
      }
      rejectionType = rejectionType == null ? "" : rejectionType.trim();
      rejectionMessage = rejectionMessage == null ? "" : rejectionMessage.trim();
      if (!rejectionType.isEmpty() && maxVisibleChars != 0) {
        throw new IllegalArgumentException("Rejected permit must not reserve visible characters");
      }
    }

    public static ToolResultReadPermit allow(int maxVisibleChars) {
      return new ToolResultReadPermit(Math.max(0, maxVisibleChars), "", "", null);
    }

    public static ToolResultReadPermit track(int maxVisibleChars, Object trackingToken) {
      return new ToolResultReadPermit(Math.max(0, maxVisibleChars), "", "", trackingToken);
    }

    public static ToolResultReadPermit reject(String type, String message) {
      return new ToolResultReadPermit(0, type, message, null);
    }

    public boolean allowed() {
      return rejectionType.isEmpty();
    }
  }
}
