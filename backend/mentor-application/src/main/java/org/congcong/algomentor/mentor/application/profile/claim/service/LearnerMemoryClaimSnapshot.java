package org.congcong.algomentor.mentor.application.profile.claim.service;

import java.util.List;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimRevision;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemorySnapshotToken;

/** 某次读取固定的 ACTIVE claim 集合和对应快照令牌。 */
public record LearnerMemoryClaimSnapshot(
    LearnerMemorySnapshotToken token,
    List<LearnerMemoryClaimRevision> activeClaims
) {

  public LearnerMemoryClaimSnapshot {
    if (token == null || activeClaims == null || activeClaims.stream().anyMatch(claim -> claim == null
        || claim.status() != org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract.RevisionStatus.ACTIVE)) {
      throw new IllegalArgumentException("snapshot 只能包含 ACTIVE claim。");
    }
    activeClaims = List.copyOf(activeClaims);
  }
}
