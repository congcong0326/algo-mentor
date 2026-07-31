package org.congcong.algomentor.mentor.application.profile.claim.model;

/** 一个 claim 的受信 kind、dimension 与可选标签范围。 */
public record LearnerMemoryClaimScope(
    LearnerMemoryClaimContract.Kind kind,
    LearnerMemoryClaimContract.Dimension dimension,
    Long tagId
) {

  public LearnerMemoryClaimScope {
    LearnerMemoryClaimContract.requireValidScope(kind, dimension, tagId);
  }
}
