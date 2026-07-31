package org.congcong.algomentor.mentor.application.profile.claim.model;

/** 用户和 scope 的 ACTIVE claim 容量快照。 */
public record LearnerMemoryClaimCapacity(
    long userActiveCount,
    long scopeActiveCount,
    int scopeActiveLimit,
    LearnerMemoryClaimContract.CapacityState state
) {

  public LearnerMemoryClaimCapacity {
    if (userActiveCount < 0 || scopeActiveCount < 0 || scopeActiveLimit <= 0 || state == null) {
      throw new IllegalArgumentException("claim capacity 字段非法。");
    }
  }

  public boolean isScopeLimitReached() {
    return scopeActiveCount >= scopeActiveLimit;
  }
}
