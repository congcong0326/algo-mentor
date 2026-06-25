package org.congcong.algomentor.api.practice.mapper.model;

public record PracticeCodeReviewSessionLockRow(
    long id,
    long planId,
    int phaseIndex,
    String problemSlug
) {
}
