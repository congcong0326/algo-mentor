package org.congcong.algomentor.mentor.application.profile.review;

import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;

/** 阶段 A 的瞬时输出分类；不写入 Claim 主表，但必须由 mapper 按 scope 与事实校验。 */
public enum LearnerMemoryReviewObservationType {
  CURRENT_STRENGTH,
  RECOVERED_CHALLENGE,
  ACTIVE_RISK;

  public boolean supports(LearnerMemoryClaimScope scope) {
    if (scope == null) {
      return false;
    }
    return switch (this) {
      case CURRENT_STRENGTH -> scope.kind() == LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION
          || scope.kind() == LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT;
      case RECOVERED_CHALLENGE -> scope.kind() == LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT
          || (scope.kind() == LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION
              && scope.dimension() == LearnerMemoryClaimContract.Dimension.REVIEW_AND_GROWTH_PERFORMANCE);
      case ACTIVE_RISK -> scope.kind() == LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT
          || (scope.kind() == LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION
              && scope.dimension() == LearnerMemoryClaimContract.Dimension.IMPLEMENTATION_AND_ERROR_PATTERN);
    };
  }
}
