package org.congcong.algomentor.mentor.application.practice;

import java.util.List;
import java.util.Map;

public record PracticeTurnClassification(
    boolean codeSubmissionCandidate,
    boolean idempotentReplay,
    String languageHint,
    String extractedCode,
    String originalMessage,
    Map<String, Object> metadata,
    List<PracticeCodeReviewEvidence> evidence
) {

  public PracticeTurnClassification {
    languageHint = blankToNull(languageHint);
    extractedCode = extractedCode == null ? "" : extractedCode.strip();
    originalMessage = originalMessage == null ? "" : originalMessage.strip();
    metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    evidence = evidence == null ? List.of() : List.copyOf(evidence);
  }

  public static PracticeTurnClassification codeLike(
      String languageHint,
      String extractedCode,
      String originalMessage,
      Map<String, Object> metadata,
      PracticeCodeReviewEvidence... evidence
  ) {
    return new PracticeTurnClassification(
        true,
        false,
        languageHint,
        extractedCode,
        originalMessage,
        metadata,
        evidence == null ? List.of() : List.of(evidence));
  }

  public static PracticeTurnClassification notCodeLike(
      String originalMessage,
      String extractedCode,
      Map<String, Object> metadata,
      PracticeCodeReviewEvidence... evidence
  ) {
    return new PracticeTurnClassification(
        false,
        false,
        null,
        extractedCode,
        originalMessage,
        metadata,
        evidence == null ? List.of() : List.of(evidence));
  }

  public PracticeTurnClassification asIdempotentReplay() {
    return new PracticeTurnClassification(
        codeSubmissionCandidate,
        true,
        languageHint,
        extractedCode,
        originalMessage,
        metadata,
        evidence);
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim().toLowerCase();
  }
}
