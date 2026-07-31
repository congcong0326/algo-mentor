package org.congcong.algomentor.mentor.application.profile.claim.model;

import java.time.Instant;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;

/** 版本化原子 claim 的领域事实，不承载持久化或 Agent 行为。 */
public record LearnerMemoryClaimRevision(
    long id,
    UUID claimKey,
    long userId,
    LearnerMemoryClaimScope scope,
    int revisionNo,
    LearnerMemoryClaimContract.RevisionStatus status,
    String claimText,
    String claimTextHash,
    LearnerMemoryClaimContract.Origin origin,
    LearnerMemoryEvidenceContract.Pattern evidencePattern,
    LearnerMemoryEvidenceContract.Grade evidenceGrade,
    String decisionReason,
    long updateRunId,
    Long supersedesRevisionId,
    Instant validFrom,
    Instant validTo,
    Instant createdAt,
    Instant updatedAt
) {

  private static final String SHA_256_HEX = "[0-9a-f]{64}";

  public LearnerMemoryClaimRevision {
    requirePositive(id, "claim revision id");
    if (claimKey == null) {
      throw new IllegalArgumentException("claim key 不能为空。");
    }
    requirePositive(userId, "user id");
    if (scope == null || status == null || origin == null || evidencePattern == null || evidenceGrade == null) {
      throw new IllegalArgumentException("claim revision 的固定字段不能为空。");
    }
    if (revisionNo <= 0) {
      throw new IllegalArgumentException("revision no 必须为正数。");
    }
    claimText = requireClaimText(claimText);
    if (claimTextHash == null || !claimTextHash.matches(SHA_256_HEX)) {
      throw new IllegalArgumentException("claim text hash 必须是 64 位小写 SHA-256。");
    }
    requirePositive(updateRunId, "update run id");
    if (supersedesRevisionId != null) {
      requirePositive(supersedesRevisionId, "supersedes revision id");
      if (supersedesRevisionId == id) {
        throw new IllegalArgumentException("claim revision 不能引用自身。");
      }
    }
    if (validFrom == null || createdAt == null || updatedAt == null) {
      throw new IllegalArgumentException("claim revision 时间不能为空。");
    }
    validateValidity(status, validFrom, validTo);
    decisionReason = normalizeNullable(decisionReason);
  }

  private static void validateValidity(
      LearnerMemoryClaimContract.RevisionStatus status,
      Instant validFrom,
      Instant validTo) {
    if (status == LearnerMemoryClaimContract.RevisionStatus.SUPERSEDED) {
      if (validTo == null || validTo.isBefore(validFrom)) {
        throw new IllegalArgumentException("SUPERSEDED claim 必须有不早于生效时间的 validTo。");
      }
      return;
    }
    if (validTo != null) {
      throw new IllegalArgumentException("当前 claim revision 的 validTo 必须为空。");
    }
  }

  private static String requireClaimText(String value) {
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

  private static void requirePositive(long value, String field) {
    if (value <= 0) {
      throw new IllegalArgumentException(field + " 必须为正数。");
    }
  }
}
