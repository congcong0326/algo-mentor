package org.congcong.algomentor.mentor.application.profile.claim.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.RevisionStatus;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemorySnapshotToken;

/** 固定 ACTIVE claim 排序并派生用户级 snapshot token。 */
public final class LearnerMemoryClaimSnapshotFactory {

  private static final Comparator<LearnerMemoryClaimRevision> STABLE_ORDER = Comparator
      .comparing((LearnerMemoryClaimRevision claim) -> claim.scope().kind().ordinal())
      .thenComparing(claim -> claim.scope().dimension().ordinal())
      .thenComparing(claim -> claim.scope().tagId(), Comparator.nullsFirst(Long::compareTo))
      .thenComparing(LearnerMemoryClaimRevision::updatedAt, Comparator.reverseOrder())
      .thenComparingLong(LearnerMemoryClaimRevision::id);

  public LearnerMemoryClaimSnapshot create(List<LearnerMemoryClaimRevision> claims) {
    List<LearnerMemoryClaimRevision> ordered = (claims == null ? List.<LearnerMemoryClaimRevision>of() : claims)
        .stream()
        .filter(claim -> claim != null && claim.status() == RevisionStatus.ACTIVE)
        .sorted(STABLE_ORDER)
        .toList();
    return new LearnerMemoryClaimSnapshot(new LearnerMemorySnapshotToken(tokenValue(ordered)), ordered);
  }

  public Comparator<LearnerMemoryClaimRevision> stableOrder() {
    return STABLE_ORDER;
  }

  private String tokenValue(List<LearnerMemoryClaimRevision> claims) {
    StringBuilder source = new StringBuilder("learner-memory-snapshot-v1\n");
    for (LearnerMemoryClaimRevision claim : claims) {
      source.append(claim.id()).append('|')
          .append(claim.claimKey()).append('|')
          .append(claim.revisionNo()).append('|')
          .append(claim.status()).append('|')
          .append(claim.updatedAt()).append('\n');
    }
    try {
      byte[] bytes = MessageDigest.getInstance("SHA-256").digest(source.toString().getBytes(StandardCharsets.UTF_8));
      StringBuilder hex = new StringBuilder(bytes.length * 2);
      for (byte value : bytes) {
        hex.append(String.format("%02x", value));
      }
      return hex.toString();
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("JDK SHA-256 不可用。", exception);
    }
  }
}
