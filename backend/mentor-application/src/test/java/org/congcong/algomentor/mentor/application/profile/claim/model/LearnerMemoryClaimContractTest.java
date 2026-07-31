package org.congcong.algomentor.mentor.application.profile.claim.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Instant;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.Dimension;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.Kind;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.RevisionStatus;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.junit.jupiter.api.Test;

class LearnerMemoryClaimContractTest {

  @Test
  void acceptsOnlyTheFixedKindDimensionTagMatrix() {
    for (Dimension dimension : Dimension.values()) {
      assertThat(LearnerMemoryClaimContract.isValidScope(Kind.DECLARED_FACT, dimension, null))
          .isEqualTo(LearnerMemoryClaimContract.declaredDimensions().contains(dimension));
      assertThat(LearnerMemoryClaimContract.isValidScope(Kind.GENERAL_OBSERVATION, dimension, null))
          .isEqualTo(LearnerMemoryClaimContract.generalDimensions().contains(dimension));
      assertThat(LearnerMemoryClaimContract.isValidScope(Kind.TAG_ASSESSMENT, dimension, 7L))
          .isEqualTo(dimension == Dimension.TAG_MASTERY);
    }
    assertThat(LearnerMemoryClaimContract.isValidScope(Kind.TAG_ASSESSMENT, Dimension.TAG_MASTERY, null))
        .isFalse();
    assertThat(LearnerMemoryClaimContract.isValidScope(Kind.DECLARED_FACT, Dimension.LEARNER_BACKGROUND, 7L))
        .isFalse();
  }

  @Test
  void rejectsInvalidClaimRevisionInvariants() {
    Instant now = Instant.parse("2026-07-30T00:00:00Z");
    LearnerMemoryClaimScope scope = new LearnerMemoryClaimScope(
        Kind.DECLARED_FACT, Dimension.LEARNER_BACKGROUND, null);

    assertThat(revision(scope, RevisionStatus.ACTIVE, "x".repeat(600), "a".repeat(64), now, null)
        .claimText()).hasSize(600);
    assertThat(revision(scope, RevisionStatus.RETIRED, "valid", "b".repeat(64), now, null)
        .status()).isEqualTo(RevisionStatus.RETIRED);
    assertThat(revision(scope, RevisionStatus.SUPPRESSED, "valid", "c".repeat(64), now, null)
        .status()).isEqualTo(RevisionStatus.SUPPRESSED);
    assertThat(revision(scope, RevisionStatus.REJECTED, "valid", "d".repeat(64), now, null)
        .status()).isEqualTo(RevisionStatus.REJECTED);
    assertThat(revision(scope, RevisionStatus.SUPERSEDED, "valid", "e".repeat(64), now, now)
        .status()).isEqualTo(RevisionStatus.SUPERSEDED);
    assertThatIllegalArgumentException().isThrownBy(() -> revision(
        scope, RevisionStatus.ACTIVE, " ", "a".repeat(64), now, null));
    assertThatIllegalArgumentException().isThrownBy(() -> revision(
        scope, RevisionStatus.ACTIVE, "x".repeat(601), "a".repeat(64), now, null));
    assertThatIllegalArgumentException().isThrownBy(() -> revision(
        scope, RevisionStatus.ACTIVE, "valid", "A".repeat(64), now, null));
    assertThatIllegalArgumentException().isThrownBy(() -> revision(
        scope, RevisionStatus.ACTIVE, "valid", "a".repeat(64), now, now));
    assertThatIllegalArgumentException().isThrownBy(() -> revision(
        scope, RevisionStatus.SUPERSEDED, "valid", "a".repeat(64), now, null));
    assertThatIllegalArgumentException().isThrownBy(() -> new LearnerMemoryClaimRevision(
        9L, UUID.randomUUID(), 7L, scope, 1, RevisionStatus.ACTIVE, "valid", "a".repeat(64),
        LearnerMemoryClaimContract.Origin.USER_EXPLICIT,
        LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION,
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED,
        null, 11L, 9L, now, null, now, now));
  }

  private LearnerMemoryClaimRevision revision(
      LearnerMemoryClaimScope scope,
      RevisionStatus status,
      String claimText,
      String hash,
      Instant validFrom,
      Instant validTo) {
    return new LearnerMemoryClaimRevision(
        9L, UUID.randomUUID(), 7L, scope, 1, status, claimText, hash,
        LearnerMemoryClaimContract.Origin.USER_EXPLICIT,
        LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION,
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED,
        null, 11L, null, validFrom, validTo, validFrom, validFrom);
  }
}
