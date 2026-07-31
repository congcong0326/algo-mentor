package org.congcong.algomentor.mentor.application.profile.claim.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.junit.jupiter.api.Test;

class LearnerMemorySnapshotTest {

  @Test
  void normalizesWhitespaceAndUsesUtf8Sha256TextHashes() {
    LearnerMemoryClaimTextHasher hasher = new LearnerMemoryClaimTextHasher();

    assertThat(hasher.hash("  Java\n\tpractice  "))
        .isEqualTo(hasher.hash("Java practice"));
    assertThat(hasher.hash("Java practice"))
        .isNotEqualTo(hasher.hash("JavaScript practice"));
  }

  @Test
  void snapshotOrderAndTokenDoNotDependOnRepositoryOrderButDetectChanges() {
    LearnerMemoryClaimSnapshotFactory factory = new LearnerMemoryClaimSnapshotFactory();
    LearnerMemoryClaimRevision declared = claim(2L, "00000000-0000-0000-0000-000000000002",
        LearnerMemoryClaimContract.Kind.DECLARED_FACT,
        LearnerMemoryClaimContract.Dimension.LEARNER_BACKGROUND,
        null, "2026-07-30T00:00:01Z");
    LearnerMemoryClaimRevision general = claim(1L, "00000000-0000-0000-0000-000000000001",
        LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.PROBLEM_SOLVING_APPROACH,
        null, "2026-07-30T00:00:00Z");

    LearnerMemoryClaimSnapshot first = factory.create(List.of(general, declared));
    LearnerMemoryClaimSnapshot sameClaimsDifferentOrder = factory.create(List.of(declared, general));
    LearnerMemoryClaimSnapshot changed = factory.create(List.of(general, claim(2L,
        "00000000-0000-0000-0000-000000000002", LearnerMemoryClaimContract.Kind.DECLARED_FACT,
        LearnerMemoryClaimContract.Dimension.LEARNER_BACKGROUND, null, "2026-07-30T00:00:02Z")));

    assertThat(first.activeClaims()).extracting(LearnerMemoryClaimRevision::id).containsExactly(2L, 1L);
    assertThat(first.token()).isEqualTo(sameClaimsDifferentOrder.token());
    assertThat(first.token()).isNotEqualTo(changed.token());
    assertThat(factory.create(List.of()).token()).isEqualTo(factory.create(List.of()).token());
  }

  private LearnerMemoryClaimRevision claim(
      long id,
      String claimKey,
      LearnerMemoryClaimContract.Kind kind,
      LearnerMemoryClaimContract.Dimension dimension,
      Long tagId,
      String updatedAt) {
    Instant timestamp = Instant.parse(updatedAt);
    return new LearnerMemoryClaimRevision(
        id, UUID.fromString(claimKey), 7L, new LearnerMemoryClaimScope(kind, dimension, tagId), 1,
        LearnerMemoryClaimContract.RevisionStatus.ACTIVE, "claim " + id, "a".repeat(64),
        LearnerMemoryClaimContract.Origin.SYSTEM_DERIVED,
        LearnerMemoryEvidenceContract.Pattern.CROSS_PROBLEM_RECURRENCE,
        LearnerMemoryEvidenceContract.Grade.SUPPORTED,
        null, 11L, null, timestamp, null, timestamp, timestamp);
  }
}
