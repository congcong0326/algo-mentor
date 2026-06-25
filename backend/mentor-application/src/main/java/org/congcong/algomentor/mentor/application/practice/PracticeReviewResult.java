package org.congcong.algomentor.mentor.application.practice;

import java.util.Map;
import java.util.Optional;

public record PracticeReviewResult(
    PracticeReviewStatus status,
    Optional<PracticeCodeReviewDraft> draft,
    String failureCode,
    Map<String, Object> metadata
) {

  public static final String INVALID_STRUCTURED_OUTPUT = "INVALID_STRUCTURED_OUTPUT";

  public PracticeReviewResult {
    if (status == null) {
      throw new IllegalArgumentException("Practice review status must not be null");
    }
    if (draft == null) {
      throw new IllegalArgumentException("Practice review draft optional must not be null");
    }
    if (metadata == null) {
      throw new IllegalArgumentException("Practice review metadata must not be null");
    }
    if (status == PracticeReviewStatus.REVIEWED && draft.isEmpty()) {
      throw new IllegalArgumentException("Reviewed practice result must include a draft");
    }
    if (status != PracticeReviewStatus.REVIEWED && draft.isPresent()) {
      throw new IllegalArgumentException("Non-reviewed practice result must not include a draft");
    }
    failureCode = blankToNull(failureCode);
    metadata = Map.copyOf(metadata);
  }

  public PracticeReviewResult(PracticeReviewStatus status, Optional<PracticeCodeReviewDraft> draft, String failureCode) {
    this(status, draft, failureCode, Map.of());
  }

  public static PracticeReviewResult reviewed(PracticeCodeReviewDraft draft) {
    return new PracticeReviewResult(PracticeReviewStatus.REVIEWED, Optional.of(draft), null, Map.of());
  }

  public static PracticeReviewResult saved(PracticeCodeReview review) {
    return saved(review, Map.of());
  }

  public static PracticeReviewResult saved(PracticeCodeReview review, Map<String, Object> metadata) {
    if (review == null) {
      throw new IllegalArgumentException("Saved practice review must not be null");
    }
    return new PracticeReviewResult(
        PracticeReviewStatus.SAVED,
        Optional.empty(),
        null,
        withReviewMetadata(review, metadata));
  }

  public static PracticeReviewResult notCompleteSubmission() {
    return new PracticeReviewResult(PracticeReviewStatus.NOT_COMPLETE_SUBMISSION, Optional.empty(), null, Map.of());
  }

  public static PracticeReviewResult notCodeLike() {
    return new PracticeReviewResult(PracticeReviewStatus.NOT_CODE_LIKE, Optional.empty(), null, Map.of());
  }

  public static PracticeReviewResult failed(String failureCode) {
    return new PracticeReviewResult(PracticeReviewStatus.FAILED, Optional.empty(), failureCode, Map.of());
  }

  public static PracticeReviewResult failed(String failureCode, Map<String, Object> metadata) {
    return new PracticeReviewResult(PracticeReviewStatus.FAILED, Optional.empty(), failureCode, metadata);
  }

  private static Map<String, Object> withReviewMetadata(PracticeCodeReview review, Map<String, Object> metadata) {
    java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();
    if (metadata != null) {
      values.putAll(metadata);
    }
    values.put("reviewId", review.id());
    values.put("versionNo", review.versionNo());
    values.put("totalScore", review.score().total().toPlainString());
    values.put("passed", review.passed());
    values.put("language", review.language());
    return values;
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
