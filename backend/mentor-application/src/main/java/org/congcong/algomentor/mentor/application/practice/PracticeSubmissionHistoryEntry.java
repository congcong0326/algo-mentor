package org.congcong.algomentor.mentor.application.practice;

import java.util.List;

/** Practice Chat 历史提交索引的最小条目，不携带题目 slug、代码或评分。 */
public record PracticeSubmissionHistoryEntry(
    String problemRef,
    String title,
    List<String> tags,
    String reviewHistorySummary
) {

  public PracticeSubmissionHistoryEntry {
    if (problemRef == null || problemRef.isBlank() || title == null || title.isBlank()) {
      throw new IllegalArgumentException("Practice submission history entry requires a reference and title");
    }
    problemRef = problemRef.trim();
    title = title.trim();
    tags = tags == null ? List.of() : tags.stream()
        .filter(tag -> tag != null && !tag.isBlank()).map(String::trim).distinct().toList();
    reviewHistorySummary = normalizeSummary(reviewHistorySummary);
  }

  private static String normalizeSummary(String value) {
    if (value == null) {
      return null;
    }
    String normalized = value.replaceAll("\\s+", " ").trim();
    if (normalized.isEmpty() || normalized.length() > PracticeCodeReviewConstants.REVIEW_HISTORY_SUMMARY_MAX_LENGTH) {
      return null;
    }
    return normalized;
  }
}
