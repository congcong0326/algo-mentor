package org.congcong.algomentor.agent.core.execution;

import java.util.concurrent.RejectedExecutionException;

/** 携带稳定原因的 Agent 执行池拒绝异常。 */
public final class AgentExecutionRejectedException extends RejectedExecutionException {

  private final AgentExecutionRejectionReason reason;
  private final AgentExecutionGroup group;

  public AgentExecutionRejectedException(
      AgentExecutionRejectionReason reason,
      String message,
      RejectedExecutionException cause
  ) {
    this(reason, null, message, cause);
  }

  public AgentExecutionRejectedException(
      AgentExecutionRejectionReason reason,
      AgentExecutionGroup group,
      String message,
      RejectedExecutionException cause
  ) {
    super(message, cause);
    this.reason = reason == null ? AgentExecutionRejectionReason.SATURATED : reason;
    this.group = group;
  }

  public AgentExecutionRejectionReason reason() {
    return reason;
  }

  /** 被拒绝的受信执行组；兼容旧调用方时可能为空。 */
  public AgentExecutionGroup group() {
    return group;
  }
}
