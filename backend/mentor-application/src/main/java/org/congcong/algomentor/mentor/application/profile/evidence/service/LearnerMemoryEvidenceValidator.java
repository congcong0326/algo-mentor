package org.congcong.algomentor.mentor.application.profile.evidence.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceReferences;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceValidationContext;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryOperationFailure;

/** 按受信来源、窗口和 pattern 结构校验 evidence；grade 由独立计算器派生。 */
public final class LearnerMemoryEvidenceValidator {

  private static final Comparator<LearnerMemoryEvidenceValidationContext.ReviewSource> REVIEW_ORDER =
      Comparator.comparing(LearnerMemoryEvidenceValidationContext.ReviewSource::createdAt)
          .thenComparingInt(LearnerMemoryEvidenceValidationContext.ReviewSource::versionNo)
          .thenComparingLong(LearnerMemoryEvidenceValidationContext.ReviewSource::reviewId);

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
        requireSameProblemDistinctAttempts(reviews, 2);
      }
      case SAME_PROBLEM_RECOVERY -> {
        requireReviewOnly(references, messages);
        requireSameProblemDistinctAttempts(reviews, 2);
        if (!hasRecoveryTrajectory(references, reviews)) {
          fail();
        }
      }
      case SAME_PROBLEM_REGRESSION -> {
        requireReviewOnly(references, messages);
        requireRegression(references, reviews);
      }
      case CROSS_PROBLEM_RECOVERY -> {
        requireReviewOnly(references, messages);
        requireDistinctProblemCount(reviews, 2);
        if (groupByProblem(reviews).values().stream()
            .anyMatch(problemReviews -> !hasRecoveryTrajectory(references, problemReviews))) {
          fail();
        }
      }
      case CROSS_PROBLEM_RECURRENCE -> {
        requireReviewOnly(references, messages);
        requireDistinctProblemCount(reviews, 2);
      }
      case CROSS_PROBLEM_LONGITUDINAL -> {
        requireReviewOnly(references, messages);
        requireDistinctProblemCount(reviews, 2);
        if (groupByProblem(reviews).values().stream().noneMatch(this::hasMultipleAttempts)) {
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

  /** Review 的 versionNo 仅在一次训练 session 内递增，跨 session 重试必须按独立尝试处理。 */
  private void requireSameProblemDistinctAttempts(
      List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews,
      int minimumAttempts) {
    if (reviews.isEmpty() || groupByProblem(reviews).size() != 1
        || reviews.size() < minimumAttempts) {
      fail();
    }
  }

  private void requireRegression(
      LearnerMemoryEvidenceReferences references,
      List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews) {
    requireSameProblemDistinctAttempts(reviews, 3);
    Map<Long, LearnerMemoryEvidenceContract.ReviewRole> roles = reviewRoles(references);
    boolean regressed = reviews.stream()
        .filter(review -> !review.passed()
            && roles.get(review.reviewId()) == LearnerMemoryEvidenceContract.ReviewRole.OBSERVED)
        .anyMatch(observed -> reviews.stream()
            .filter(review -> review.passed()
                && roles.get(review.reviewId()) == LearnerMemoryEvidenceContract.ReviewRole.RESOLVED
                && REVIEW_ORDER.compare(observed, review) < 0)
            .anyMatch(resolved -> reviews.stream().anyMatch(review -> !review.passed()
                && roles.get(review.reviewId()) == LearnerMemoryEvidenceContract.ReviewRole.REGRESSED
                && REVIEW_ORDER.compare(resolved, review) < 0)));
    if (!regressed) {
      fail();
    }
  }

  private boolean hasRecoveryTrajectory(
      LearnerMemoryEvidenceReferences references,
      List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews) {
    Map<Long, LearnerMemoryEvidenceContract.ReviewRole> roles = reviewRoles(references);
    return reviews.stream().filter(review -> !review.passed()
            && roles.get(review.reviewId()) == LearnerMemoryEvidenceContract.ReviewRole.OBSERVED)
        .anyMatch(observed -> reviews.stream().anyMatch(resolved -> resolved.passed()
            && roles.get(resolved.reviewId()) == LearnerMemoryEvidenceContract.ReviewRole.RESOLVED
            && REVIEW_ORDER.compare(observed, resolved) < 0));
  }

  private void requireDistinctProblemCount(
      List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews,
      int minimumProblems) {
    if (reviews.stream().map(LearnerMemoryEvidenceValidationContext.ReviewSource::problemSlug).distinct().count()
        < minimumProblems) {
      fail();
    }
  }

  private Map<Long, LearnerMemoryEvidenceContract.ReviewRole> reviewRoles(
      LearnerMemoryEvidenceReferences references) {
    return references.reviews().stream()
        .collect(Collectors.toMap(
            LearnerMemoryEvidenceReferences.ReviewReference::reviewId,
            LearnerMemoryEvidenceReferences.ReviewReference::role));
  }

  private Map<String, List<LearnerMemoryEvidenceValidationContext.ReviewSource>> groupByProblem(
      List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews) {
    return reviews.stream().collect(Collectors.groupingBy(
        LearnerMemoryEvidenceValidationContext.ReviewSource::problemSlug));
  }

  private boolean hasMultipleAttempts(List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews) {
    return reviews.size() >= 2;
  }

  private static void fail() {
    throw new LearnerMemoryOperationFailure(LearnerMemoryOperationFailure.Code.INVALID_EVIDENCE);
  }
}
