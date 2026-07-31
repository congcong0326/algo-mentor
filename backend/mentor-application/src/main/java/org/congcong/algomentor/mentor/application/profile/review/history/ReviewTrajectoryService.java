package org.congcong.algomentor.mentor.application.profile.review.history;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** 从同题、升序的正式 Review 计算分数和 finding 的纵向变化。 */
public final class ReviewTrajectoryService {

  public static final int MAX_VERSIONS = 5;

  public ReviewTrajectory calculate(List<CodeReviewHistory> reviews) {
    List<CodeReviewHistory> ordered = normalizedReviews(reviews);
    if (ordered.isEmpty()) {
      throw new IllegalArgumentException("Review trajectory requires at least one review");
    }

    String problemSlug = ordered.get(0).problemSlug();
    List<ReviewTrajectoryVersion> versions = new ArrayList<>(ordered.size());
    Set<String> previousFindings = Set.of();
    CodeReviewHistory previousReview = null;
    for (CodeReviewHistory review : ordered) {
      Set<String> currentFindings = normalizedFindings(review.deductionReasons());
      versions.add(new ReviewTrajectoryVersion(
          review,
          previousReview == null ? null : review.totalScore().subtract(previousReview.totalScore()),
          intersection(previousFindings, currentFindings),
          difference(previousFindings, currentFindings),
          difference(currentFindings, previousFindings)));
      previousFindings = currentFindings;
      previousReview = review;
    }
    return new ReviewTrajectory(problemSlug, versions);
  }

  private static List<CodeReviewHistory> normalizedReviews(List<CodeReviewHistory> reviews) {
    if (reviews == null || reviews.isEmpty() || reviews.size() > MAX_VERSIONS) {
      throw new IllegalArgumentException("Review trajectory must contain between one and five reviews");
    }
    List<CodeReviewHistory> ordered = reviews.stream().filter(Objects::nonNull)
        .sorted(Comparator.comparingInt(CodeReviewHistory::versionNo).thenComparingLong(CodeReviewHistory::reviewId))
        .toList();
    if (ordered.size() != reviews.size()) {
      throw new IllegalArgumentException("Review trajectory must not contain null reviews");
    }
    String problemSlug = ordered.get(0).problemSlug();
    int previousVersion = 0;
    for (CodeReviewHistory review : ordered) {
      if (!problemSlug.equals(review.problemSlug()) || review.versionNo() <= previousVersion) {
        throw new IllegalArgumentException("Review trajectory requires one problem and strictly increasing versions");
      }
      previousVersion = review.versionNo();
    }
    return ordered;
  }

  private static Set<String> normalizedFindings(Collection<String> findings) {
    LinkedHashSet<String> normalized = new LinkedHashSet<>();
    if (findings != null) {
      findings.stream().map(ReviewTrajectoryService::normalizeFinding)
          .filter(value -> !value.isEmpty()).sorted().forEach(normalized::add);
    }
    return Set.copyOf(normalized);
  }

  private static String normalizeFinding(String value) {
    return value == null ? "" : value.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
  }

  private static List<String> intersection(Set<String> left, Set<String> right) {
    return left.stream().filter(right::contains).sorted().toList();
  }

  private static List<String> difference(Set<String> left, Set<String> right) {
    return left.stream().filter(value -> !right.contains(value)).sorted().toList();
  }
}
