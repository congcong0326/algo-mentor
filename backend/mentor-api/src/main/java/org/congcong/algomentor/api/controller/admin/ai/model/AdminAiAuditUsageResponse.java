package org.congcong.algomentor.api.controller.admin.ai.model;

/** 管理端审计使用量的统一响应口径，null 代表 provider 未返回。 */
public record AdminAiAuditUsageResponse(
    Long inputTokens,
    Long cachedTokens,
    Long uncachedInputTokens,
    Long outputTokens,
    Long reasoningTokens,
    Long totalTokens,
    Double cacheRatio
) {
}
