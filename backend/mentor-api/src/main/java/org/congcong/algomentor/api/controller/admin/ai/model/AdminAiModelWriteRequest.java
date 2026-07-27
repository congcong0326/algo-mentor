package org.congcong.algomentor.api.controller.admin.ai.model;

/** 配置模型的全量写入请求体。 */
public record AdminAiModelWriteRequest(String displayName, String modelId, Boolean enabled) {
}
