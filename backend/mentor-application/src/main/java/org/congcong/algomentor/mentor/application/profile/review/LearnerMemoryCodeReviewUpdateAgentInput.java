package org.congcong.algomentor.mentor.application.profile.review;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemorySnapshotToken;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification;
import org.congcong.algomentor.mentor.application.profile.tool.LearnerMemoryRunScopeRegistry;

/** Code Review Claim 更新 Agent 的受信输入；scope 只在 Definition 准备请求时创建。 */
public final class LearnerMemoryCodeReviewUpdateAgentInput {

  private final long userId;
  private final List<LearnerMemoryCodeReviewFact> windowFacts;
  private final List<CodeReviewVerification> scopeReviews;
  private final List<CodeReviewVerification> evidenceReviews;
  private final List<ActiveClaim> activeClaims;
  private final List<ScopeCapacity> capacities;
  private final LearnerMemorySnapshotToken snapshotToken;
  private final int activeClaimCount;
  private final LearnerMemoryClaimContract.CapacityState capacityState;
  private final String idempotencyKey;
  private final Long retryOfRunId;
  private final AtomicReference<LearnerMemoryRunScopeRegistry.ScopeLease> scopeLease = new AtomicReference<>();

  public LearnerMemoryCodeReviewUpdateAgentInput(
      long userId,
      List<LearnerMemoryCodeReviewFact> windowFacts,
      List<CodeReviewVerification> scopeReviews,
      List<CodeReviewVerification> evidenceReviews,
      List<ActiveClaim> activeClaims,
      List<ScopeCapacity> capacities,
      LearnerMemorySnapshotToken snapshotToken,
      int activeClaimCount,
      LearnerMemoryClaimContract.CapacityState capacityState,
      String idempotencyKey,
      Long retryOfRunId
  ) {
    if (userId < 1) {
      throw new IllegalArgumentException("Code review memory Agent user id must be positive");
    }
    this.userId = userId;
    this.windowFacts = immutableDistinct(windowFacts, LearnerMemoryCodeReviewFact::problemSlug, "window facts");
    if (this.windowFacts.isEmpty() || this.windowFacts.size() > LearnerMemoryCodeReviewConsumerConstants.MAX_DISTINCT_PROBLEMS) {
      throw new IllegalArgumentException("Code review memory Agent window facts are invalid");
    }
    this.scopeReviews = immutableDistinct(scopeReviews, CodeReviewVerification::reviewId, "scope reviews");
    if (this.scopeReviews.isEmpty()) {
      throw new IllegalArgumentException("Code review memory Agent scope reviews must not be empty");
    }
    this.evidenceReviews = immutableDistinct(evidenceReviews, CodeReviewVerification::reviewId, "evidence reviews");
    if (!this.evidenceReviews.stream().map(CodeReviewVerification::reviewId).collect(java.util.stream.Collectors.toSet())
        .containsAll(this.scopeReviews.stream().map(CodeReviewVerification::reviewId).toList())) {
      throw new IllegalArgumentException("Code review memory Agent evidence reviews must include scope reviews");
    }
    this.activeClaims = immutableDistinct(activeClaims, ActiveClaim::revisionId, "active claims");
    this.capacities = immutableDistinct(capacities, ScopeCapacity::scope, "scope capacities");
    if (this.capacities.isEmpty() || !this.capacities.stream().map(ScopeCapacity::scope).collect(java.util.stream.Collectors.toSet())
        .containsAll(this.activeClaims.stream().map(ActiveClaim::scope).toList())) {
      throw new IllegalArgumentException("Code review memory Agent scopes are invalid");
    }
    this.snapshotToken = Objects.requireNonNull(snapshotToken, "snapshotToken");
    if (activeClaimCount < 0 || capacityState == null) {
      throw new IllegalArgumentException("Code review memory Agent capacity is invalid");
    }
    this.activeClaimCount = activeClaimCount;
    this.capacityState = capacityState;
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new IllegalArgumentException("Code review memory Agent idempotency key must not be blank");
    }
    this.idempotencyKey = idempotencyKey.trim();
    if (retryOfRunId != null && retryOfRunId < 1) {
      throw new IllegalArgumentException("Code review memory Agent retry source run id must be positive");
    }
    this.retryOfRunId = retryOfRunId;
  }

  public long userId() {
    return userId;
  }

  public List<LearnerMemoryCodeReviewFact> windowFacts() {
    return windowFacts;
  }

  public List<CodeReviewVerification> scopeReviews() {
    return scopeReviews;
  }

  public List<CodeReviewVerification> evidenceReviews() {
    return evidenceReviews;
  }

  public List<ActiveClaim> activeClaims() {
    return activeClaims;
  }

  public List<ScopeCapacity> capacities() {
    return capacities;
  }

  public LearnerMemorySnapshotToken snapshotToken() {
    return snapshotToken;
  }

  public int activeClaimCount() {
    return activeClaimCount;
  }

  public LearnerMemoryClaimContract.CapacityState capacityState() {
    return capacityState;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public Long retryOfRunId() {
    return retryOfRunId;
  }

  public Set<LearnerMemoryClaimScope> allowedScopes() {
    return capacities.stream().map(ScopeCapacity::scope).collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  /** Definition 唯一调用此方法；同一输入不能被重复准备，从而避免复用已释放的 capability。 */
  public LearnerMemoryRunScopeRegistry.ScopeLease openScope(LearnerMemoryRunScopeRegistry registry) {
    LearnerMemoryRunScopeRegistry.ScopeLease created = Objects.requireNonNull(registry, "scopeRegistry")
        .openUpdateScope(userId, scopeReviews);
    if (!scopeLease.compareAndSet(null, created)) {
      created.release();
      throw new IllegalStateException("Code review memory Agent input scope is already prepared");
    }
    return created;
  }

  public int toolCallCount() {
    LearnerMemoryRunScopeRegistry.ScopeLease lease = scopeLease.get();
    return lease == null ? 0 : lease.toolCallCount();
  }

  private static <T, K> List<T> immutableDistinct(
      List<T> values,
      java.util.function.Function<T, K> key,
      String field
  ) {
    if (values == null || values.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("Code review memory Agent " + field + " must not contain null");
    }
    Set<K> keys = new LinkedHashSet<>();
    for (T value : values) {
      if (!keys.add(key.apply(value))) {
        throw new IllegalArgumentException("Code review memory Agent " + field + " must be distinct");
      }
    }
    return List.copyOf(values);
  }

  /** 可更新的当前 revision；existing evidence 供 CONFIRM 输出完整证据集。 */
  public record ActiveClaim(
      long revisionId,
      LearnerMemoryClaimScope scope,
      String claimText,
      List<ReviewEvidence> existingReviewEvidence
  ) {
    public ActiveClaim {
      if (revisionId <= 0 || scope == null || claimText == null || claimText.isBlank()) {
        throw new IllegalArgumentException("Code review memory active claim is invalid");
      }
      claimText = claimText.trim();
      existingReviewEvidence = immutableDistinct(
          existingReviewEvidence == null ? List.of() : existingReviewEvidence,
          ReviewEvidence::reviewId,
          "existing review evidence");
    }
  }

  /** 服务端已知的 claim Review 证据，仅提供 Review ID 与 role，不包含正文。 */
  public record ReviewEvidence(long reviewId, org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract.ReviewRole role) {
    public ReviewEvidence {
      if (reviewId <= 0 || role == null) {
        throw new IllegalArgumentException("Code review memory review evidence is invalid");
      }
    }
  }

  /** 每个允许 scope 的当前 ACTIVE 数量和上限，容量状态由服务端计算。 */
  public record ScopeCapacity(LearnerMemoryClaimScope scope, int activeCount, int limit) {
    public ScopeCapacity {
      if (scope == null || activeCount < 0 || limit < 1) {
        throw new IllegalArgumentException("Code review memory scope capacity is invalid");
      }
    }
  }
}
