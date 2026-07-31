package org.congcong.algomentor.mentor.application.profile.evidence.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceReferences;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceValidationContext;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryOperationFailure;

/** 按受信来源、窗口和 pattern 结构校验 evidence；grade 由独立计算器派生。 */
public final class LearnerMemoryEvidenceValidator {

  public void validate(
      LearnerMemoryEvidenceReferences references,
      LearnerMemoryClaimScope scope,
      LearnerMemoryEvidenceValidationContext context) {
    if (references == null || scope == null || context == null) {
      fail();
    }
    List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews = references.reviews().stream()
        .map(reference -> context.review(reference.reviewId()))
        .toList();
    List<LearnerMemoryEvidenceValidationContext.MessageSource> messages = references.messages().stream()
        .map(reference -> context.message(reference.messageId()))
        .toList();
    if (reviews.stream().anyMatch(java.util.Objects::isNull)
        || messages.stream().anyMatch(java.util.Objects::isNull)) {
      fail();
    }

    switch (references.pattern()) {
      case USER_DECLARATION -> {
        requireUserOnly(references, reviews);
        requireMessageRole(references, LearnerMemoryEvidenceContract.MessageRole.DECLARED);
      }
      case USER_CORRECTION -> {
        requireUserOnly(references, reviews);
        requireMessageRole(references, LearnerMemoryEvidenceContract.MessageRole.CORRECTED);
      }
      case SINGLE_REVIEW -> {
        requireReviewOnly(references, messages);
        if (scope.kind() != LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT || scope.tagId() == null
            || reviews.size() != 1 || !reviews.get(0).tagIds().contains(scope.tagId())) {
          fail();
        }
      }
      case SAME_PROBLEM_PERSISTENCE -> {
        requireReviewOnly(references, messages);
        requireSameProblemDistinctVersions(reviews, 2);
      }
      case SAME_PROBLEM_RECOVERY -> {
        requireReviewOnly(references, messages);
        requireSameProblemDistinctVersions(reviews, 2);
        requireOrderedReviewRoles(references, reviews,
            LearnerMemoryEvidenceContract.ReviewRole.OBSERVED,
            LearnerMemoryEvidenceContract.ReviewRole.RESOLVED);
      }
      case SAME_PROBLEM_REGRESSION -> {
        requireReviewOnly(references, messages);
        requireRegression(references, reviews);
      }
      case CROSS_PROBLEM_RECURRENCE -> {
        requireReviewOnly(references, messages);
        requireDistinctProblemCount(reviews, 2);
      }
      case CROSS_PROBLEM_LONGITUDINAL -> {
        requireReviewOnly(references, messages);
        requireDistinctProblemCount(reviews, 2);
        if (groupByProblem(reviews).values().stream().noneMatch(this::hasMultipleVersions)) {
          fail();
        }
      }
      case TAG_BREADTH -> {
        requireReviewOnly(references, messages);
        if (scope.kind() != LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT || scope.tagId() == null) {
          fail();
        }
        requireDistinctProblemCount(reviews, 2);
        if (reviews.stream().anyMatch(review -> !review.tagIds().contains(scope.tagId()))) {
          fail();
        }
      }
    }
  }

  private void requireUserOnly(
      LearnerMemoryEvidenceReferences references,
      List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews) {
    if (!reviews.isEmpty() || references.messages().isEmpty()) {
      fail();
    }
  }

  private void requireReviewOnly(
      LearnerMemoryEvidenceReferences references,
      List<LearnerMemoryEvidenceValidationContext.MessageSource> messages) {
    if (references.reviews().isEmpty() || !messages.isEmpty()) {
      fail();
    }
  }

  private void requireMessageRole(
      LearnerMemoryEvidenceReferences references,
      LearnerMemoryEvidenceContract.MessageRole role) {
    if (references.messages().stream().noneMatch(reference -> reference.role() == role)) {
      fail();
    }
  }

  private void requireOrderedReviewRoles(
      LearnerMemoryEvidenceReferences references,
      List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews,
      LearnerMemoryEvidenceContract.ReviewRole first,
      LearnerMemoryEvidenceContract.ReviewRole second) {
    List<LearnerMemoryEvidenceContract.ReviewRole> ordered = orderedRoles(references, reviews);
    if (ordered.indexOf(first) < 0 || ordered.indexOf(second) <= ordered.indexOf(first)) {
      fail();
    }
  }

  private void requireSameProblemDistinctVersions(
      List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews,
      int minimumVersions) {
    if (reviews.isEmpty() || groupByProblem(reviews).size() != 1
        || reviews.stream().map(LearnerMemoryEvidenceValidationContext.ReviewSource::versionNo).distinct().count()
        < minimumVersions) {
      fail();
    }
  }

  private void requireRegression(
      LearnerMemoryEvidenceReferences references,
      List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews) {
    requireSameProblemDistinctVersions(reviews, 3);
    List<LearnerMemoryEvidenceContract.ReviewRole> ordered = orderedRoles(references, reviews);
    if (ordered.indexOf(LearnerMemoryEvidenceContract.ReviewRole.OBSERVED) < 0
        || ordered.indexOf(LearnerMemoryEvidenceContract.ReviewRole.RESOLVED)
            <= ordered.indexOf(LearnerMemoryEvidenceContract.ReviewRole.OBSERVED)
        || ordered.lastIndexOf(LearnerMemoryEvidenceContract.ReviewRole.REGRESSED)
            <= ordered.indexOf(LearnerMemoryEvidenceContract.ReviewRole.RESOLVED)) {
      fail();
    }
  }

  private void requireDistinctProblemCount(
      List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews,
      int minimumProblems) {
    if (reviews.stream().map(LearnerMemoryEvidenceValidationContext.ReviewSource::problemSlug).distinct().count()
        < minimumProblems) {
      fail();
    }
  }

  private List<LearnerMemoryEvidenceContract.ReviewRole> orderedRoles(
      LearnerMemoryEvidenceReferences references,
      List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews) {
    Map<Long, LearnerMemoryEvidenceContract.ReviewRole> roles = references.reviews().stream()
        .collect(Collectors.toMap(
            LearnerMemoryEvidenceReferences.ReviewReference::reviewId,
            LearnerMemoryEvidenceReferences.ReviewReference::role));
    return reviews.stream()
        .sorted(Comparator.comparingInt(LearnerMemoryEvidenceValidationContext.ReviewSource::versionNo)
            .thenComparingLong(LearnerMemoryEvidenceValidationContext.ReviewSource::reviewId))
        .map(review -> roles.get(review.reviewId()))
        .toList();
  }

  private Map<String, List<LearnerMemoryEvidenceValidationContext.ReviewSource>> groupByProblem(
      List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews) {
    return reviews.stream().collect(Collectors.groupingBy(
        LearnerMemoryEvidenceValidationContext.ReviewSource::problemSlug));
  }

  private boolean hasMultipleVersions(List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews) {
    return reviews.stream().map(LearnerMemoryEvidenceValidationContext.ReviewSource::versionNo).distinct().count() >= 2;
  }

  private static void fail() {
    throw new LearnerMemoryOperationFailure(LearnerMemoryOperationFailure.Code.INVALID_EVIDENCE);
  }
}
