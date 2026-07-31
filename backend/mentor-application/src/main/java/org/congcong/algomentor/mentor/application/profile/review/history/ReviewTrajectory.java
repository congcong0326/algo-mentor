package org.congcong.algomentor.mentor.application.profile.review.history;

import java.util.List;

/** 同一题目最近正式 Review 的确定性纵向轨迹。 */
public record ReviewTrajectory(String problemSlug, List<ReviewTrajectoryVersion> versions) {

  public ReviewTrajectory {
    if (problemSlug == null || problemSlug.isBlank()) {
      throw new IllegalArgumentException("Review trajectory problem slug must not be blank");
    }
    problemSlug = problemSlug.trim();
    versions = versions == null ? List.of() : List.copyOf(versions);
  }
}
