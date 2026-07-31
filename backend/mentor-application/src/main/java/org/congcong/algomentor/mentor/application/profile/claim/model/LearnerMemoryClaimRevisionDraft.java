package org.congcong.algomentor.mentor.application.profile.claim.model;

import java.time.Instant;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;

/** 新增 claim revision 时尚未分配数据库主键的写入草稿。 */
public record LearnerMemoryClaimRevisionDraft(
    UUID claimKey,
    long userId,
    LearnerMemoryClaimScope scope,
    int revisionNo,
    LearnerMemoryClaimContract.RevisionStatus status,
    String claimText,
    LearnerMemoryClaimTextHash claimTextHash,
    LearnerMemoryClaimContract.Origin origin,
    LearnerMemoryEvidenceContract.Pattern evidencePattern,
    LearnerMemoryEvidenceContract.Grade evidenceGrade,
    String decisionReason,
    long updateRunId,
    Long supersedesRevisionId,
    Instant validFrom,
    Instant validTo
) {

  public LearnerMemoryClaimRevisionDraft {
    if (claimKey == null || userId <= 0 || scope == null || revisionNo <= 0 || status == null
        || claimTextHash == null || origin == null || evidencePattern == null || evidenceGrade == null
        || updateRunId <= 0 || validFrom == null) {
      throw new IllegalArgumentException("claim revision draft 固定字段非法。");
    }
    claimText = normalizeClaimText(claimText);
    if (supersedesRevisionId != null && supersedesRevisionId <= 0) {
      throw new IllegalArgumentException("supersedes revision id 必须为正数。");
    }
    if (status == LearnerMemoryClaimContract.RevisionStatus.SUPERSEDED) {
      if (validTo == null || validTo.isBefore(validFrom)) {
        throw new IllegalArgumentException("SUPERSEDED claim 必须有合法 validTo。");
      }
    } else if (validTo != null) {
      throw new IllegalArgumentException("当前 claim revision 的 validTo 必须为空。");
    }
    decisionReason = normalizeNullable(decisionReason);
  }

  private static String normalizeClaimText(String value) {
    if (value == null) {
      throw new IllegalArgumentException("claim text 不能为空。");
    }
    String normalized = value.trim();
    if (normalized.isEmpty() || normalized.length() > LearnerMemoryClaimContract.CLAIM_TEXT_MAX_CHARS) {
      throw new IllegalArgumentException("claim text 必须为 1 至 600 个字符。");
    }
    return normalized;
  }

  private static String normalizeNullable(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.trim();
    return normalized.isEmpty() ? null : normalized;
  }
}
