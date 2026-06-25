package org.congcong.algomentor.agent.persistence.postgres.mapper.model;

import java.time.Instant;
import java.util.Map;

public record AgentTurnMessagesRow(
    long runId,
    long turnId,
    long taskId,
    long userMessageId,
    long userSequenceNo,
    String userContent,
    Instant userCreatedAt,
    Map<String, Object> userMetadata,
    Long assistantMessageId,
    Long assistantSequenceNo,
    String assistantContent,
    Instant assistantCreatedAt,
    Map<String, Object> assistantMetadata
) {
}
