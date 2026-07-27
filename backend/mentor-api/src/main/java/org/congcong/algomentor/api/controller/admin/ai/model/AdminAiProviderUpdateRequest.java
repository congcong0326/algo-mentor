package org.congcong.algomentor.api.controller.admin.ai.model;

import com.fasterxml.jackson.databind.JsonNode;

/** 更新 provider instance 的全量可编辑字段；providerType 创建后不可修改。 */
public record AdminAiProviderUpdateRequest(String name, Boolean enabled, JsonNode config) {
}
