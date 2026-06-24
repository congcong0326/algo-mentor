package org.congcong.algomentor.api.problem.model;

import java.util.List;

public record ProblemSeedRecord(
    String slug,
    Integer frontendId,
    String titleEn,
    String titleZh,
    ProblemDifficulty difficulty,
    List<String> tagValues,
    List<String> tagLabelsEn,
    List<String> tagLabelsZh,
    String contentMarkdownEn,
    String contentMarkdownZh,
    String leetcodeUrl,
    String sampleTestCase,
    String python3Template,
    String sourceCommit
) {

  public ProblemSeedRecord {
    tagValues = tagValues == null ? List.of() : List.copyOf(tagValues);
    tagLabelsEn = tagLabelsEn == null ? List.of() : List.copyOf(tagLabelsEn);
    tagLabelsZh = tagLabelsZh == null ? List.of() : List.copyOf(tagLabelsZh);
  }
}
