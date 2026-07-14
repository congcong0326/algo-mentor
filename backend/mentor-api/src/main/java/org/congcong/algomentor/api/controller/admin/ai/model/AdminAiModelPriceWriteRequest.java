package org.congcong.algomentor.api.controller.admin.ai.model;

/** 所有金额字段保持字符串，避免 JavaScript 浮点转换。 */
public record AdminAiModelPriceWriteRequest(
    String provider,
    String model,
    String inputPricePerMillion,
    String cachedInputPricePerMillion,
    String outputPricePerMillion,
    String costMultiplier,
    Boolean enabled
) {
}
