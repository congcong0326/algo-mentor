package org.congcong.algomentor.agent.core.runtime.audit;

/** 同一 turn 下关联 run 的低敏标识，供会话审计时间线定位重试。 */
public record AgentAuditRunAttempt(long runId, int attemptNo, String status) {
}
