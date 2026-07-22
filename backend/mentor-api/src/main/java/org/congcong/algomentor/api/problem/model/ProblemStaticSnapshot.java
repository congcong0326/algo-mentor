package org.congcong.algomentor.api.problem.model;

import java.util.List;
import java.util.Objects;

/** 发布期不可变的题目双语原始快照。 */
public record ProblemStaticSnapshot(
    String slug,
    Integer frontendId,
    String frontendDisplayId,
    String titleEn,
    String titleZh,
    ProblemDifficulty difficulty,
    List<TrustedProblemTag> tags,
    String contentMarkdownEn,
    String contentMarkdownZh,
    String contentStatus,
    String leetcodeUrl,
    String sampleTestCase,
    String python3Template,
    String sourceCommit,
    String recommendationReasonEn,
    String recommendationReasonZh
) {

  public ProblemStaticSnapshot {
    slug = Objects.requireNonNull(slug, "slug must not be null");
    tags = List.copyOf(Objects.requireNonNull(tags, "tags must not be null"));
  }
}
