package org.congcong.algomentor.agent.core.runtime.audit;

/** provider 返回的统一 token usage；缺失 usage 保持 null，不能伪造为零。 */
public record AgentAuditUsage(
    Long inputTokens,
    Long cachedTokens,
    Long outputTokens,
    Long reasoningTokens,
    Long totalTokens
) {

  public static AgentAuditUsage empty() {
    return new AgentAuditUsage(null, null, null, null, null);
  }
}
