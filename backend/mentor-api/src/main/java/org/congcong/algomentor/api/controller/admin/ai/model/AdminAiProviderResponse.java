package org.congcong.algomentor.api.controller.admin.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** provider 列表与详情响应；列表不包含 config，详情包含完整 config。 */
public record AdminAiProviderResponse(
    long id,
    String name,
    String providerType,
    boolean enabled,
    String baseUrl,
    JsonNode config,
    int modelCount,
    Instant createdAt,
    Instant updatedAt
) {
}
