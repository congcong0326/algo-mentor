package org.congcong.algomentor.mentor.application.profile.claim.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimCapacity;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevisionDraft;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.repository.LearnerMemoryClaimRepository;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.junit.jupiter.api.Test;

class LearnerMemoryClaimRepositoryTest {

  @Test
  void calculatesUserAndScopeCapacityFromRepositoryCounts() {
    LearnerMemoryClaimScope declaredScope = new LearnerMemoryClaimScope(
        LearnerMemoryClaimContract.Kind.DECLARED_FACT,
        LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS,
        null);
    LearnerMemoryClaimScope tagScope = new LearnerMemoryClaimScope(
        LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT,
        LearnerMemoryClaimContract.Dimension.TAG_MASTERY,
        9L);
    CountingRepository repository = new CountingRepository(500, 10);
    LearnerMemoryClaimQueryService service = new LearnerMemoryClaimQueryService(
        repository, new LearnerMemoryClaimSnapshotFactory());

    LearnerMemoryClaimCapacity declaredCapacity = service.capacity(7L, declaredScope);
    assertThat(declaredCapacity.state()).isEqualTo(LearnerMemoryClaimContract.CapacityState.SOFT_LIMIT);
    assertThat(declaredCapacity.scopeActiveLimit()).isEqualTo(10);
    assertThat(declaredCapacity.isScopeLimitReached()).isTrue();

    repository.userCount = 1_000;
    repository.scopeCount = 5;
    LearnerMemoryClaimCapacity tagCapacity = service.capacity(7L, tagScope);
    assertThat(tagCapacity.state()).isEqualTo(LearnerMemoryClaimContract.CapacityState.HARD_LIMIT);
    assertThat(tagCapacity.scopeActiveLimit()).isEqualTo(5);
    assertThat(tagCapacity.isScopeLimitReached()).isTrue();
  }

  @Test
  void readsOnlyActiveClaimsAndKeepsTheStableSnapshotOrder() {
    LearnerMemoryClaimRevision active = claim(2L, LearnerMemoryClaimContract.RevisionStatus.ACTIVE);
    LearnerMemoryClaimRevision superseded = claim(1L, LearnerMemoryClaimContract.RevisionStatus.SUPERSEDED);
    CountingRepository repository = new CountingRepository(1, 1);
    repository.active = List.of(active);
    LearnerMemoryClaimQueryService service = new LearnerMemoryClaimQueryService(
        repository, new LearnerMemoryClaimSnapshotFactory());

    assertThat(service.snapshot(7L).activeClaims()).containsExactly(active);
    assertThat(service.rereadActiveByRevisionIds(7L, List.of(1L, 2L))).containsExactly(active);
    assertThat(superseded.status()).isEqualTo(LearnerMemoryClaimContract.RevisionStatus.SUPERSEDED);
  }

  private LearnerMemoryClaimRevision claim(long id, LearnerMemoryClaimContract.RevisionStatus status) {
    Instant now = Instant.parse("2026-07-30T00:00:00Z");
    return new LearnerMemoryClaimRevision(
        id, UUID.randomUUID(), 7L, new LearnerMemoryClaimScope(
            LearnerMemoryClaimContract.Kind.DECLARED_FACT,
            LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS,
            null),
        1, status, "claim", "a".repeat(64), LearnerMemoryClaimContract.Origin.USER_EXPLICIT,
        LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION,
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED,
        null, 11L, null, now, status == LearnerMemoryClaimContract.RevisionStatus.SUPERSEDED ? now : null,
        now, now);
  }

  private static final class CountingRepository implements LearnerMemoryClaimRepository {
    private long userCount;
    private long scopeCount;
    private List<LearnerMemoryClaimRevision> active = List.of();

    private CountingRepository(long userCount, long scopeCount) {
      this.userCount = userCount;
      this.scopeCount = scopeCount;
    }

    @Override
    public List<LearnerMemoryClaimRevision> findActiveByUser(long userId) {
      return active;
    }

    @Override
    public List<LearnerMemoryClaimRevision> findActiveByScopes(
        long userId, Collection<LearnerMemoryClaimScope> scopes) {
      return active;
    }

    @Override
    public List<LearnerMemoryClaimRevision> findActiveByRevisionIds(long userId, Collection<Long> revisionIds) {
      return active;
    }

    @Override
    public List<LearnerMemoryClaimRevision> findActiveByUserForUpdate(long userId) {
      return active;
    }

    @Override
    public Optional<LearnerMemoryClaimRevision> findCurrent(long userId, UUID claimKey) {
      return Optional.empty();
    }

    @Override
    public List<LearnerMemoryClaimRevision> findHistory(long userId, UUID claimKey) {
      return List.of();
    }

    @Override
    public long countActiveByUser(long userId) {
      return userCount;
    }

    @Override
    public long countActiveByScope(long userId, LearnerMemoryClaimScope scope) {
      return scopeCount;
    }

    @Override
    public void lockUser(long userId) {
    }

    @Override
    public LearnerMemoryClaimRevision insert(LearnerMemoryClaimRevisionDraft draft) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void markCurrentSuperseded(long revisionId, Instant validTo) {
      throw new UnsupportedOperationException();
    }
  }
}
