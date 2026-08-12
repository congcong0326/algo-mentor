package org.congcong.algomentor.api.controller.admin.ai.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/** 一个 step 对应的最终脱敏出站请求。 */
public record AdminAiAuditStepDetailResponse(
    AdminAiAuditStepResponse step,
    Long snapshotId,
    JsonNode requestSnapshot,
    JsonNode messages,
    JsonNode tools,
    JsonNode toolChoice,
    JsonNode generationOptions,
    String requestHash,
    String redactionPolicyVersion,
    Instant snapshotRetentionExpiresAt,
    JsonNode metadata,
    List<AdminAiAuditToolCallResponse> toolCalls
) {
}
