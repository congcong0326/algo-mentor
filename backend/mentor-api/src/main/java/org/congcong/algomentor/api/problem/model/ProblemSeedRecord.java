package org.congcong.algomentor.api.problem.model;

import java.util.List;

public record ProblemSeedRecord(
    String slug,
    Integer frontendId,
    String frontendDisplayId,
    String titleEn,
    String titleZh,
    ProblemDifficulty difficulty,
    List<String> tagValues,
    List<String> tagLabelsEn,
    List<String> tagLabelsZh,
    String contentMarkdownEn,
    String contentMarkdownZh,
    String contentStatus,
    String sourceSite,
    String leetcodeUrl,
    String sampleTestCase,
    String python3Template,
    String sourceCommit,
    String recommendationReasonEn,
    String recommendationReasonZh
) {

  public ProblemSeedRecord {
    tagValues = tagValues == null ? List.of() : List.copyOf(tagValues);
    tagLabelsEn = tagLabelsEn == null ? List.of() : List.copyOf(tagLabelsEn);
    tagLabelsZh = tagLabelsZh == null ? List.of() : List.copyOf(tagLabelsZh);
  }

  public ProblemSeedRecord withRecommendationReasons(String reasonEn, String reasonZh) {
    return new ProblemSeedRecord(
        slug,
        frontendId,
        frontendDisplayId,
        titleEn,
        titleZh,
        difficulty,
        tagValues,
        tagLabelsEn,
        tagLabelsZh,
        contentMarkdownEn,
        contentMarkdownZh,
        contentStatus,
        sourceSite,
        leetcodeUrl,
        sampleTestCase,
        python3Template,
        sourceCommit,
        reasonEn,
        reasonZh);
  }
}
