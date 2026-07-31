package org.congcong.algomentor.mentor.application.profile.recall;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.Kind;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract.Grade;

/** 以固定业务相关性和稳定次序挑选 bootstrap 直接命中 claim。 */
public final class LearnerMemoryDirectHitSelector {

  public List<LearnerMemoryClaimRevision> select(
      Collection<LearnerMemoryClaimRevision> claims,
      String currentUserMessage,
      Collection<Long> currentProblemTagIds
  ) {
    Set<Long> tagIds = currentProblemTagIds == null ? Set.of() : new HashSet<>(currentProblemTagIds);
    String normalizedMessage = normalize(currentUserMessage);
    return (claims == null ? List.<LearnerMemoryClaimRevision>of() : claims).stream()
        .filter(java.util.Objects::nonNull)
        .filter(claim -> relevance(claim, normalizedMessage, tagIds) < Integer.MAX_VALUE)
        .sorted(Comparator
            .comparingInt((LearnerMemoryClaimRevision claim) -> relevance(claim, normalizedMessage, tagIds))
            .thenComparingInt(claim -> -gradeWeight(claim.evidenceGrade()))
            .thenComparing(LearnerMemoryClaimRevision::updatedAt, Comparator.reverseOrder())
            .thenComparingLong(LearnerMemoryClaimRevision::id))
        .limit(LearnerMemoryRecallContracts.MAX_DIRECT_HITS)
        .toList();
  }

  private int relevance(LearnerMemoryClaimRevision claim, String message, Set<Long> tagIds) {
    if (claim.scope().kind() == Kind.DECLARED_FACT) {
      return 0;
    }
    if (claim.scope().kind() == Kind.TAG_ASSESSMENT && tagIds.contains(claim.scope().tagId())) {
      return 1;
    }
    if (claim.scope().kind() == Kind.GENERAL_OBSERVATION && matches(message, claim.claimText())) {
      return 2;
    }
    return Integer.MAX_VALUE;
  }

  private boolean matches(String message, String claimText) {
    String normalizedClaim = normalize(claimText);
    if (message.isBlank() || normalizedClaim.isBlank()) {
      return false;
    }
    if (message.contains(normalizedClaim) || normalizedClaim.contains(message)) {
      return true;
    }
    return java.util.Arrays.stream(normalizedClaim.split("\\s+"))
        .filter(token -> token.length() >= 3)
        .anyMatch(message::contains);
  }

  private int gradeWeight(Grade grade) {
    return switch (grade) {
      case USER_AUTHORED -> 4;
      case STRONG -> 3;
      case SUPPORTED -> 2;
      case LIMITED -> 1;
    };
  }

  static String normalize(String value) {
    if (value == null || value.isBlank()) {
      return "";
    }
    return Normalizer.normalize(value, Normalizer.Form.NFKC)
        .toLowerCase(Locale.ROOT)
        .replaceAll("[^\\p{L}\\p{N}]+", " ")
        .trim();
  }
}
