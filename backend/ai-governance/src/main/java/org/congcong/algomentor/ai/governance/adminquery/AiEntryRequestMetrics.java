package org.congcong.algomentor.ai.governance.adminquery;

/** 当天用户 AI 入口尝试按最终状态分组的指标。 */
public record AiEntryRequestMetrics(
    long total,
    long completed,
    long failed,
    long cancelled,
    long quotaRejected,
    long inProgress,
    long otherRejected
) {
}
