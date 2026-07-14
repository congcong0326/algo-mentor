package org.congcong.algomentor.api.controller.admin.ai.model;

import java.time.Instant;

/** API 传输中的金额与单价均使用十进制字符串。 */
public record AdminAiModelPriceResponse(
    long id,
    String provider,
    String model,
    String currency,
    String inputPricePerMillion,
    String cachedInputPricePerMillion,
    String outputPricePerMillion,
    String costMultiplier,
    boolean enabled,
    Long updatedBy,
    String updatedByDisplayName,
    Instant createdAt,
    Instant updatedAt
) {
}
