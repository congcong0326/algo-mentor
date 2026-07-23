package org.congcong.algomentor.agent.core.execution;

import java.util.concurrent.RejectedExecutionException;

/** 携带稳定原因的 Agent 执行池拒绝异常。 */
public final class AgentExecutionRejectedException extends RejectedExecutionException {

  private final AgentExecutionRejectionReason reason;

  public AgentExecutionRejectedException(
      AgentExecutionRejectionReason reason,
      String message,
      RejectedExecutionException cause
  ) {
    super(message, cause);
    this.reason = reason == null ? AgentExecutionRejectionReason.SATURATED : reason;
  }

  public AgentExecutionRejectionReason reason() {
    return reason;
  }
}
