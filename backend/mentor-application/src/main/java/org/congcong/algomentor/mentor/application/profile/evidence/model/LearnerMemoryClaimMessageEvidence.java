package org.congcong.algomentor.mentor.application.profile.evidence.model;

import java.time.Instant;

/** Claim revision 使用的一条受信用户消息证据。 */
public record LearnerMemoryClaimMessageEvidence(
    long claimRevisionId,
    long messageId,
    LearnerMemoryEvidenceContract.MessageRole role,
    int sequenceNo,
    Instant createdAt
) {

  public LearnerMemoryClaimMessageEvidence {
    requirePositive(claimRevisionId, "claim revision id");
    requirePositive(messageId, "message id");
    if (role == null || sequenceNo <= 0 || createdAt == null) {
      throw new IllegalArgumentException("message evidence 字段非法。");
    }
  }

  private static void requirePositive(long value, String field) {
    if (value <= 0) {
      throw new IllegalArgumentException(field + " 必须为正数。");
    }
  }
}
