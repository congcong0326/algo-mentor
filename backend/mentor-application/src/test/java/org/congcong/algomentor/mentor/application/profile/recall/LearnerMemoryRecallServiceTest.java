package org.congcong.algomentor.mentor.application.profile.recall;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTag;
import org.congcong.algomentor.mentor.application.practice.TrustedProblemTagCatalog;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.repository.LearnerMemoryClaimRepository;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimQueryService;
import org.congcong.algomentor.mentor.application.profile.claim.service.LearnerMemoryClaimSnapshotFactory;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryRunScopeRegistry;
import org.junit.jupiter.api.Test;

class LearnerMemoryRecallServiceTest {

  @Test
  void opensStableClaimSnapshotWithFixedSectionAndDirectHitPriority() {
    LearnerMemoryClaimRevision declared = claim(
        10, LearnerMemoryClaimContract.Kind.DECLARED_FACT,
        LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS, null, "准备后端面试", "2026-01-02T00:00:00Z",
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED);
    LearnerMemoryClaimRevision general = claim(
        11, LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.IMPLEMENTATION_AND_ERROR_PATTERN, null,
        "实现时经常遗漏边界条件", "2026-01-03T00:00:00Z", LearnerMemoryEvidenceContract.Grade.STRONG);
    LearnerMemoryClaimRevision tag = claim(
        12, LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT,
        LearnerMemoryClaimContract.Dimension.TAG_MASTERY, 9L, "数组题的双指针使用稳定", "2026-01-01T00:00:00Z",
        LearnerMemoryEvidenceContract.Grade.SUPPORTED);
    LearnerMemoryClaimRevision unrelated = claim(
        13, LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.REVIEW_AND_GROWTH_PERFORMANCE, null,
        "复盘频率稳定", "2026-01-04T00:00:00Z", LearnerMemoryEvidenceContract.Grade.STRONG);
    LearnerMemoryClaimRepository repository = repository(List.of(unrelated, tag, general, declared));
    LearnerMemoryRecallService service = service(repository);
    TrustedProblemTag currentTag = new TrustedProblemTag(9L, "array", "Array", "数组");

    LearnerMemoryRecallService.OpenedSnapshot first = service.openSnapshot(
        7L, "我实现时经常遗漏边界条件，想继续练习", "two-sum", List.of(currentTag), "zh-CN");
    LearnerMemoryRecallService.OpenedSnapshot second = service.openSnapshot(
        7L, "我实现时经常遗漏边界条件，想继续练习", "two-sum", List.of(currentTag), "zh-CN");

    assertThat(first.snapshot().documentRevision()).isEqualTo(second.snapshot().documentRevision());
    assertThat(first.snapshot().scopeRef()).isNotEqualTo(second.snapshot().scopeRef());
    assertThat(first.snapshot().sections()).extracting(LearnerMemoryRecallSnapshot.Section::catalogId)
        .containsExactly("background-goals", "problem-solving", "review-growth", "knowledge-performance");
    assertThat(first.snapshot().directHits()).extracting(hit -> hit.claim().id())
        .containsExactly(declared.id(), tag.id(), general.id());
    assertThat(first.snapshot().directHits()).extracting(LearnerMemoryRecallSnapshot.Statement::sourceSummary)
        .containsExactly("用户明确自述", "当前题目标签：数组", "正式代码复盘");

    first.lease().release();
    second.lease().release();
  }

  @Test
  void degradesToEmptyMetadataSafeSnapshotWhenClaimReadFails() {
    LearnerMemoryClaimRepository repository = new InMemoryClaimRepository(List.of(), true);

    LearnerMemoryRecallService.OpenedSnapshot opened = service(repository)
        .openSnapshot(7L, "请解释", "two-sum", List.of(), "zh-CN");

    assertThat(opened.snapshot().claimCount()).isZero();
    assertThat(opened.snapshot().scopeRef()).isEqualTo("unavailable");
    assertThat(opened.snapshot().documentRevision()).hasSize(64);
  }

  @Test
  void keepsAnOpenedScopeStableWhenTheNextReadSeesNewActiveClaims() {
    LearnerMemoryClaimRevision initial = claim(
        10, LearnerMemoryClaimContract.Kind.DECLARED_FACT,
        LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS, null, "初始目标", "2026-01-02T00:00:00Z",
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED);
    LearnerMemoryClaimRevision replacement = claim(
        11, LearnerMemoryClaimContract.Kind.DECLARED_FACT,
        LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS, null, "更新后的目标", "2026-01-03T00:00:00Z",
        LearnerMemoryEvidenceContract.Grade.USER_AUTHORED);
    InMemoryClaimRepository repository = new InMemoryClaimRepository(List.of(initial), false);
    LearnerMemoryRecallService service = service(repository);

    LearnerMemoryRecallService.OpenedSnapshot first = service.openSnapshot(7L, "给我提示", "two-sum", List.of(), "zh-CN");
    repository.replace(List.of(replacement));
    LearnerMemoryRecallService.OpenedSnapshot second = service.openSnapshot(7L, "给我提示", "two-sum", List.of(), "zh-CN");

    assertThat(first.snapshot().directHits()).extracting(hit -> hit.claim().claimText()).containsExactly("初始目标");
    assertThat(second.snapshot().directHits()).extracting(hit -> hit.claim().claimText()).containsExactly("更新后的目标");
    first.lease().release();
    second.lease().release();
  }

  private static LearnerMemoryRecallService service(LearnerMemoryClaimRepository repository) {
    return new LearnerMemoryRecallService(
        new LearnerMemoryClaimQueryService(repository, new LearnerMemoryClaimSnapshotFactory()),
        TrustedProblemTagCatalog.empty(),
        new LearnerMemorySectionCatalog(),
        new LearnerMemoryDirectHitSelector(),
        new LearnerMemoryRunScopeRegistry());
  }

  private static LearnerMemoryClaimRepository repository(List<LearnerMemoryClaimRevision> claims) {
    return new InMemoryClaimRepository(claims, false);
  }

  private static LearnerMemoryClaimRevision claim(
      long id,
      LearnerMemoryClaimContract.Kind kind,
      LearnerMemoryClaimContract.Dimension dimension,
      Long tagId,
      String text,
      String updatedAt,
      LearnerMemoryEvidenceContract.Grade grade
  ) {
    Instant time = Instant.parse(updatedAt);
    return new LearnerMemoryClaimRevision(
        id,
        new UUID(0L, id),
        7L,
        new LearnerMemoryClaimScope(kind, dimension, tagId),
        1,
        LearnerMemoryClaimContract.RevisionStatus.ACTIVE,
        text,
        ("%064x").formatted(id),
        kind == LearnerMemoryClaimContract.Kind.DECLARED_FACT
            ? LearnerMemoryClaimContract.Origin.USER_EXPLICIT
            : LearnerMemoryClaimContract.Origin.SYSTEM_DERIVED,
        kind == LearnerMemoryClaimContract.Kind.DECLARED_FACT
            ? LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION
            : LearnerMemoryEvidenceContract.Pattern.CROSS_PROBLEM_RECURRENCE,
        grade,
        null,
        1L,
        null,
        time,
        null,
        time,
        time);
  }

  private static final class InMemoryClaimRepository implements LearnerMemoryClaimRepository {

    private List<LearnerMemoryClaimRevision> claims;
    private final boolean failOnRead;

    private InMemoryClaimRepository(List<LearnerMemoryClaimRevision> claims, boolean failOnRead) {
      this.claims = List.copyOf(claims);
      this.failOnRead = failOnRead;
    }

    private void replace(List<LearnerMemoryClaimRevision> claims) {
      this.claims = List.copyOf(claims);
    }

    @Override
    public List<LearnerMemoryClaimRevision> findActiveByUser(long userId) {
      if (failOnRead) {
        throw new IllegalStateException("database unavailable");
      }
      return claims;
    }

    @Override
    public List<LearnerMemoryClaimRevision> findActiveByScopes(
        long userId, java.util.Collection<LearnerMemoryClaimScope> scopes) {
      return List.of();
    }

    @Override
    public List<LearnerMemoryClaimRevision> findActiveByRevisionIds(
        long userId, java.util.Collection<Long> revisionIds) {
      return List.of();
    }

    @Override
    public List<LearnerMemoryClaimRevision> findActiveByUserForUpdate(long userId) {
      return List.of();
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
      return 0;
    }

    @Override
    public long countActiveByScope(long userId, LearnerMemoryClaimScope scope) {
      return 0;
    }

    @Override
    public void lockUser(long userId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public LearnerMemoryClaimRevision insert(
        org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevisionDraft draft) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void markCurrentSuperseded(long revisionId, Instant validTo) {
      throw new UnsupportedOperationException();
    }
  }
}
