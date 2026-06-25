package org.congcong.algomentor.mentor.application.practice;

import java.util.Optional;

public record PracticeReviewResult(
    PracticeReviewStatus status,
    Optional<PracticeCodeReviewDraft> draft,
    String failureCode
) {

  public static final String INVALID_STRUCTURED_OUTPUT = "INVALID_STRUCTURED_OUTPUT";

  public PracticeReviewResult {
    if (status == null) {
      throw new IllegalArgumentException("Practice review status must not be null");
    }
    if (draft == null) {
      throw new IllegalArgumentException("Practice review draft optional must not be null");
    }
    if (status == PracticeReviewStatus.REVIEWED && draft.isEmpty()) {
      throw new IllegalArgumentException("Reviewed practice result must include a draft");
    }
    if (status != PracticeReviewStatus.REVIEWED && draft.isPresent()) {
      throw new IllegalArgumentException("Non-reviewed practice result must not include a draft");
    }
    failureCode = blankToNull(failureCode);
  }

  public static PracticeReviewResult reviewed(PracticeCodeReviewDraft draft) {
    return new PracticeReviewResult(PracticeReviewStatus.REVIEWED, Optional.of(draft), null);
  }

  public static PracticeReviewResult notCompleteSubmission() {
    return new PracticeReviewResult(PracticeReviewStatus.NOT_COMPLETE_SUBMISSION, Optional.empty(), null);
  }

  public static PracticeReviewResult failed(String failureCode) {
    return new PracticeReviewResult(PracticeReviewStatus.FAILED, Optional.empty(), failureCode);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }
}
