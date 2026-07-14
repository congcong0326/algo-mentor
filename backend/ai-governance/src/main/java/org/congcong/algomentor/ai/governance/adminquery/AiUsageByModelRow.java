package org.congcong.algomentor.ai.governance.adminquery;

/** 按精确 provider/model 聚合的用量。 */
public record AiUsageByModelRow(
    String provider,
    String model,
    boolean priced,
    AiUsageMetrics metrics
) {
}
