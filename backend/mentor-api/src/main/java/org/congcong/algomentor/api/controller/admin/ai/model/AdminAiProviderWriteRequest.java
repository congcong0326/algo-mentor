package org.congcong.algomentor.api.controller.admin.ai.model;

import com.fasterxml.jackson.databind.JsonNode;

/** 创建 provider instance 的全量请求体。 */
public record AdminAiProviderWriteRequest(
    String name,
    String providerType,
    Boolean enabled,
    JsonNode config
) {
}
