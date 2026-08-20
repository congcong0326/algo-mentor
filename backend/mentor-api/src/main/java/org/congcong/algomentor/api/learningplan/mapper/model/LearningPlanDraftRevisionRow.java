package org.congcong.algomentor.api.learningplan.mapper.model;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

public record LearningPlanDraftRevisionRow(
    Long id,
    Long proposalGroupId,
    Long draftId,
    Long userId,
    Integer revisionNo,
    String status,
    String instruction,
    JsonNode baseBriefJson,
    JsonNode basePlanJson,
    JsonNode proposedBriefJson,
    JsonNode proposedPlanJson,
    String errorCode,
    String errorMessage,
    String generationRequestKey,
    String generationRequestFingerprint,
    String generationRunId,
    Instant generationStartedAt,
    Instant generationCompletedAt,
    Instant createdAt,
    Instant updatedAt
) {

  /** 阶段二前的 Mapper fixture 不携带生成控制面字段。 */
  public LearningPlanDraftRevisionRow(
      Long id,
      Long proposalGroupId,
      Long draftId,
      Long userId,
      Integer revisionNo,
      String status,
      String instruction,
      JsonNode baseBriefJson,
      JsonNode basePlanJson,
      JsonNode proposedBriefJson,
      JsonNode proposedPlanJson,
      String errorCode,
      String errorMessage,
      Instant createdAt,
      Instant updatedAt
  ) {
    this(id, proposalGroupId, draftId, userId, revisionNo, status, instruction, baseBriefJson, basePlanJson,
        proposedBriefJson, proposedPlanJson, errorCode, errorMessage, null, null, null, null, null,
        createdAt, updatedAt);
  }
}
