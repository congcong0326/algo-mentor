package org.congcong.algomentor.api.controller.admin.ai.model;

import java.time.Instant;

/** 管理员可见的显式配置模型。 */
public record AdminAiConfiguredModelResponse(
    long id,
    long providerInstanceId,
    String displayName,
    String modelId,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
}
