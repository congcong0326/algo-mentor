package org.congcong.algomentor.mentor.application.profile.claim.service;

import java.util.Collection;
import java.util.List;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimCapacity;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.repository.LearnerMemoryClaimRepository;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;

/** Claim 查询、稳定快照和容量计算门面。 */
public final class LearnerMemoryClaimQueryService {

  private final LearnerMemoryClaimRepository repository;
  private final LearnerMemoryClaimSnapshotFactory snapshotFactory;
  private final LearnerMemoryMetrics metrics;

  public LearnerMemoryClaimQueryService(
      LearnerMemoryClaimRepository repository,
      LearnerMemoryClaimSnapshotFactory snapshotFactory) {
    this(repository, snapshotFactory, LearnerMemoryMetrics.NOOP);
  }

  public LearnerMemoryClaimQueryService(
      LearnerMemoryClaimRepository repository,
      LearnerMemoryClaimSnapshotFactory snapshotFactory,
      LearnerMemoryMetrics metrics) {
    this.repository = repository;
    this.snapshotFactory = snapshotFactory;
    this.metrics = metrics == null ? LearnerMemoryMetrics.NOOP : metrics;
  }

  public LearnerMemoryClaimSnapshot snapshot(long userId) {
    return snapshotFactory.create(repository.findActiveByUser(requireUserId(userId)));
  }

  public List<LearnerMemoryClaimRevision> findActiveByScopes(
      long userId, Collection<LearnerMemoryClaimScope> scopes) {
    return snapshotFactory.create(repository.findActiveByScopes(requireUserId(userId), scopes)).activeClaims();
  }

  public List<LearnerMemoryClaimRevision> rereadActiveByRevisionIds(long userId, Collection<Long> revisionIds) {
    return snapshotFactory.create(repository.findActiveByRevisionIds(requireUserId(userId), revisionIds)).activeClaims();
  }

  public LearnerMemoryClaimCapacity capacity(long userId, LearnerMemoryClaimScope scope) {
    long safeUserId = requireUserId(userId);
    if (scope == null) {
      throw new IllegalArgumentException("claim scope 不能为空。");
    }
    long activeCount = repository.countActiveByUser(safeUserId);
    LearnerMemoryClaimContract.CapacityState state = activeCount >= LearnerMemoryClaimContract.USER_ACTIVE_HARD_LIMIT
        ? LearnerMemoryClaimContract.CapacityState.HARD_LIMIT
        : activeCount >= LearnerMemoryClaimContract.USER_ACTIVE_SOFT_LIMIT
            ? LearnerMemoryClaimContract.CapacityState.SOFT_LIMIT
            : LearnerMemoryClaimContract.CapacityState.NORMAL;
    LearnerMemoryClaimCapacity capacity = new LearnerMemoryClaimCapacity(
        activeCount,
        repository.countActiveByScope(safeUserId, scope),
        scopeLimit(scope),
        state);
    if (state == LearnerMemoryClaimContract.CapacityState.SOFT_LIMIT) {
      metrics.recordActiveLimit("SOFT");
    } else if (state == LearnerMemoryClaimContract.CapacityState.HARD_LIMIT) {
      metrics.recordActiveLimit("HARD");
    }
    return capacity;
  }

  private int scopeLimit(LearnerMemoryClaimScope scope) {
    return switch (scope.kind()) {
      case DECLARED_FACT -> LearnerMemoryClaimContract.DECLARED_SCOPE_ACTIVE_LIMIT;
      case GENERAL_OBSERVATION -> LearnerMemoryClaimContract.GENERAL_SCOPE_ACTIVE_LIMIT;
      case TAG_ASSESSMENT -> LearnerMemoryClaimContract.TAG_SCOPE_ACTIVE_LIMIT;
    };
  }

  private long requireUserId(long userId) {
    if (userId <= 0) {
      throw new IllegalArgumentException("user id 必须为正数。");
    }
    return userId;
  }
}
