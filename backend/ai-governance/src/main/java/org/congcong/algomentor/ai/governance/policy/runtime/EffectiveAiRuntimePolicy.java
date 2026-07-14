package org.congcong.algomentor.ai.governance.policy.runtime;

import java.time.Instant;

/** 为某个用户和静态 purpose 解析出的最终运行策略。 */
public record EffectiveAiRuntimePolicy(
    boolean globalAiEnabled,
    Boolean aiEnabledOverride,
    boolean effectiveAiEnabled,
    AiRuntimeDisabledReason effectiveDisabledReason,
    int globalDefaultDailyRequestLimit,
    Integer dailyRequestLimitOverride,
    int effectiveDailyRequestLimit,
    Long updatedBy,
    Instant updatedAt
) {
}
