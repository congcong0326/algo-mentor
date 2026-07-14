package org.congcong.algomentor.ai.governance.adminquery;

/** 按业务调用场景聚合的用量。 */
public record AiUsageBySourceRow(
    String source,
    AiUsageMetrics metrics
) {
}
