package org.congcong.algomentor.api.practice.mapper.model;

public record CodeReviewSubmissionVersionRow(
    long reviewId,
    String problemSlug,
    int versionNo,
    String normalizedCode
) {
}
