package org.congcong.algomentor.agent.core.runtime.audit;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/** 最终脱敏 provider 请求快照和其对应工具交互。 */
public record AgentAuditStepDetail(
    AgentAuditStepSummary summary,
    Long snapshotId,
    JsonNode requestSnapshot,
    JsonNode messages,
    JsonNode tools,
    JsonNode toolChoice,
    JsonNode generationOptions,
    String requestHash,
    String redactionPolicyVersion,
    Instant snapshotRetentionExpiresAt,
    JsonNode snapshotMetadata,
    List<AgentAuditToolCall> toolCalls
) {

  public AgentAuditStepDetail {
    toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
  }
}
