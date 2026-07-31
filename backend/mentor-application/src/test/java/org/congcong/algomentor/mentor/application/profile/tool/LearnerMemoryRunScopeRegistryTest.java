package org.congcong.algomentor.mentor.application.profile.tool;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.recall.LearnerMemoryRecallSnapshot;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification;
import org.junit.jupiter.api.Test;

class LearnerMemoryRunScopeRegistryTest {

  @Test
  void enforcesScopeSpecificLimitsAndRelease() {
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryRunScopeRegistry.ScopeLease lease = registry.openUpdateScope(7, List.of(review(101, "two-sum", 1),
        review(102, "two-sum", 2), review(103, "valid-parentheses", 1)));

    assertThat(lease.scopeRef()).isNotEqualTo("7").hasSize(43);
    assertThat(registry.reserveTrajectory(lease.scopeRef(), "two-sum").status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.GRANTED);
    assertThat(registry.reserveTrajectory(lease.scopeRef(), "two-sum").status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.ALREADY_USED);
    assertThat(registry.reserveDiff(lease.scopeRef(), 101, 103).status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.FORBIDDEN);
    assertThat(registry.reserveEvidence(lease.scopeRef(), 102).status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.GRANTED);
    assertThat(registry.reserveDiff(lease.scopeRef(), 101, 102).status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.GRANTED);
    assertThat(registry.reserveEvidence(lease.scopeRef(), 101).status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.BUDGET_EXHAUSTED);

    lease.release();
    lease.release();
    assertThat(registry.activeScopeCount()).isZero();
    assertThat(registry.reserveEvidence(lease.scopeRef(), 101).status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.SCOPE_UNAVAILABLE);
  }

  @Test
  void isolatesConcurrentScopesAndExpiresUnreleasedScopes() {
    MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry(
        new java.security.SecureRandom(), clock, Duration.ofSeconds(10));
    LearnerMemoryRunScopeRegistry.ScopeLease first = registry.openUpdateScope(7, List.of(review(101, "two-sum", 1)));
    LearnerMemoryRunScopeRegistry.ScopeLease second = registry.openUpdateScope(8, List.of(review(201, "other", 1)));

    assertThat(registry.reserveEvidence(first.scopeRef(), 201).status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.FORBIDDEN);
    assertThat(registry.reserveEvidence(second.scopeRef(), 201).scope().userId()).isEqualTo(8);
    clock.advance(Duration.ofSeconds(10));

    assertThat(registry.reserveEvidence(first.scopeRef(), 101).status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.SCOPE_UNAVAILABLE);
    assertThat(registry.activeScopeCount()).isZero();
  }

  @Test
  void limitsPracticeChatTrajectoryScopeToItsCurrentProblemAndOneLookup() {
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryRunScopeRegistry.ScopeLease lease = registry.openPracticeChatTrajectoryScope(7, "two-sum");

    assertThat(registry.reserveTrajectory(lease.scopeRef(), "other").status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.FORBIDDEN);
    assertThat(registry.reserveTrajectory(lease.scopeRef(), "two-sum").scope().userId()).isEqualTo(7);
    assertThat(registry.reserveTrajectory(lease.scopeRef(), "two-sum").status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.ALREADY_USED);
    assertThat(registry.reserveEvidence(lease.scopeRef(), 101).status())
        .isEqualTo(LearnerMemoryRunScopeRegistry.ScopeUseStatus.FORBIDDEN);
  }

  @Test
  void keepsRecallClaimsOnlyInsideAnOpaqueLeaseUntilRelease() {
    LearnerMemoryRunScopeRegistry registry = new LearnerMemoryRunScopeRegistry();
    LearnerMemoryClaimRevision claim = recallClaim();

    LearnerMemoryRunScopeRegistry.RecallScopeLease lease = registry.openRecallScope(
        7L,
        "a".repeat(64),
        "zh-CN",
        List.of(new LearnerMemoryRecallSnapshot.SectionInput(
            "background-goals",
            "学习背景与目标",
            List.of(new LearnerMemoryRecallSnapshot.StatementInput(claim, "用户明确自述", false)))),
        List.of(claim.id()));

    assertThat(lease.scopeRef()).isNotEqualTo("7").hasSize(43);
    assertThat(lease.snapshot().directHits()).singleElement().satisfies(statement -> {
      assertThat(statement.statementRef()).isNotEqualTo("1").hasSize(43);
      assertThat(statement.claim().claimText()).isEqualTo("准备后端面试");
    });
    assertThat(registry.activeRecallScopeCount()).isEqualTo(1);

    lease.release();
    lease.release();
    assertThat(registry.activeRecallScopeCount()).isZero();
  }

  private static CodeReviewVerification review(long id, String slug, int version) {
    return new CodeReviewVerification(id, slug, version, List.of(8L), Instant.parse("2026-01-01T00:00:00Z"));
  }

  private static LearnerMemoryClaimRevision recallClaim() {
    Instant now = Instant.parse("2026-01-01T00:00:00Z");
    return new LearnerMemoryClaimRevision(
        1L,
        UUID.fromString("00000000-0000-0000-0000-000000000001"),
        7L,
        new LearnerMemoryClaimScope(
            LearnerMemoryClaimContract.Kind.DECLARED_FACT,
            LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS,
            null),
        1,
        LearnerMemoryClaimContract.RevisionStatus.ACTIVE,
        "准备后端面试",
        "a".repeat(64),
        LearnerMemoryClaimContract.Origin.USER_EXPLICIT,
        LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION,
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED,
        null,
        1L,
        null,
        now,
        null,
        now,
        now);
  }

  private static final class MutableClock extends Clock {

    private Instant instant;

    private MutableClock(Instant instant) {
      this.instant = instant;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return instant;
    }

    private void advance(Duration duration) {
      instant = instant.plus(duration);
    }
  }
}
