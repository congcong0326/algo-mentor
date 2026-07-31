package org.congcong.algomentor.mentor.application.profile.evidence.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceReferences;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceValidationContext;
import org.congcong.algomentor.mentor.application.profile.operation.service.LearnerMemoryOperationFailure;
import org.junit.jupiter.api.Test;

class LearnerMemoryEvidenceValidatorTest {

  private final LearnerMemoryEvidenceValidator validator = new LearnerMemoryEvidenceValidator();
  private final LearnerMemoryEvidenceGradeCalculator grades = new LearnerMemoryEvidenceGradeCalculator();
  private final LearnerMemoryEvidenceValidationContext context = new LearnerMemoryEvidenceValidationContext(
      List.of(
          review(1L, "one", 1, Set.of(9L)), review(2L, "one", 2, Set.of(9L)),
          review(3L, "one", 3, Set.of(9L)), review(4L, "two", 1, Set.of(9L))),
      List.of(message(11L), message(12L)));

  @Test
  void validatesEveryPatternAndCalculatesOnlyServerDerivedGrades() {
    LearnerMemoryClaimScope declared = scope(
        LearnerMemoryClaimContract.Kind.DECLARED_FACT,
        LearnerMemoryClaimContract.Dimension.GOALS_AND_INTENTS,
        null);
    LearnerMemoryClaimScope general = scope(
        LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.PROBLEM_SOLVING_APPROACH,
        null);
    LearnerMemoryClaimScope tag = scope(
        LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT,
        LearnerMemoryClaimContract.Dimension.TAG_MASTERY,
        9L);

    LearnerMemoryEvidenceReferences declaration = messages(
        LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION, 11L,
        LearnerMemoryEvidenceContract.MessageRole.DECLARED);
    LearnerMemoryEvidenceReferences correction = messages(
        LearnerMemoryEvidenceContract.Pattern.USER_CORRECTION, 12L,
        LearnerMemoryEvidenceContract.MessageRole.CORRECTED);
    LearnerMemoryEvidenceReferences single = reviews(
        LearnerMemoryEvidenceContract.Pattern.SINGLE_REVIEW, reference(1L, "OBSERVED"));
    LearnerMemoryEvidenceReferences persistence = reviews(
        LearnerMemoryEvidenceContract.Pattern.SAME_PROBLEM_PERSISTENCE,
        reference(1L, "OBSERVED"), reference(2L, "PERSISTED"));
    LearnerMemoryEvidenceReferences recovery = reviews(
        LearnerMemoryEvidenceContract.Pattern.SAME_PROBLEM_RECOVERY,
        reference(1L, "OBSERVED"), reference(2L, "RESOLVED"));
    LearnerMemoryEvidenceReferences regression = reviews(
        LearnerMemoryEvidenceContract.Pattern.SAME_PROBLEM_REGRESSION,
        reference(1L, "OBSERVED"), reference(2L, "RESOLVED"), reference(3L, "REGRESSED"));
    LearnerMemoryEvidenceReferences recurrence = reviews(
        LearnerMemoryEvidenceContract.Pattern.CROSS_PROBLEM_RECURRENCE,
        reference(1L, "OBSERVED"), reference(4L, "PERSISTED"));
    LearnerMemoryEvidenceReferences longitudinal = reviews(
        LearnerMemoryEvidenceContract.Pattern.CROSS_PROBLEM_LONGITUDINAL,
        reference(1L, "OBSERVED"), reference(2L, "PERSISTED"), reference(4L, "OBSERVED"));
    LearnerMemoryEvidenceReferences breadth = reviews(
        LearnerMemoryEvidenceContract.Pattern.TAG_BREADTH,
        reference(1L, "OBSERVED"), reference(4L, "PERSISTED"));

    assertThatCode(() -> validator.validate(declaration, declared, context)).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(correction, declared, context)).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(single, tag, context)).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(persistence, general, context)).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(recovery, general, context)).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(regression, general, context)).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(recurrence, general, context)).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(longitudinal, general, context)).doesNotThrowAnyException();
    assertThatCode(() -> validator.validate(breadth, tag, context)).doesNotThrowAnyException();

    assertThat(grades.calculate(declaration, context)).isEqualTo(LearnerMemoryEvidenceContract.Grade.USER_AUTHORED);
    assertThat(grades.calculate(single, context)).isEqualTo(LearnerMemoryEvidenceContract.Grade.LIMITED);
    assertThat(grades.calculate(persistence, context)).isEqualTo(LearnerMemoryEvidenceContract.Grade.SUPPORTED);
    assertThat(grades.calculate(longitudinal, context)).isEqualTo(LearnerMemoryEvidenceContract.Grade.STRONG);
  }

  @Test
  void rejectsScopeMismatchAndEvidenceOutsideTrustedContext() {
    LearnerMemoryEvidenceReferences single = reviews(
        LearnerMemoryEvidenceContract.Pattern.SINGLE_REVIEW, reference(1L, "OBSERVED"));
    LearnerMemoryClaimScope general = scope(
        LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.PROBLEM_SOLVING_APPROACH,
        null);

    assertThatThrownBy(() -> validator.validate(single, general, context))
        .isInstanceOf(LearnerMemoryOperationFailure.class)
        .extracting(error -> ((LearnerMemoryOperationFailure) error).code())
        .isEqualTo(LearnerMemoryOperationFailure.Code.INVALID_EVIDENCE);
    assertThatThrownBy(() -> validator.validate(reviews(
        LearnerMemoryEvidenceContract.Pattern.CROSS_PROBLEM_RECURRENCE,
        reference(1L, "OBSERVED"), reference(99L, "PERSISTED")), general, context))
        .isInstanceOf(LearnerMemoryOperationFailure.class);
  }

  @Test
  void rejectsSingleReviewWithoutTargetTagAndReversedRecovery() {
    LearnerMemoryClaimScope tag = scope(
        LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT,
        LearnerMemoryClaimContract.Dimension.TAG_MASTERY,
        10L);
    LearnerMemoryClaimScope general = scope(
        LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION,
        LearnerMemoryClaimContract.Dimension.PROBLEM_SOLVING_APPROACH,
        null);

    assertThatThrownBy(() -> validator.validate(reviews(
        LearnerMemoryEvidenceContract.Pattern.SINGLE_REVIEW, reference(1L, "OBSERVED")), tag, context))
        .isInstanceOf(LearnerMemoryOperationFailure.class)
        .extracting(error -> ((LearnerMemoryOperationFailure) error).code())
        .isEqualTo(LearnerMemoryOperationFailure.Code.INVALID_EVIDENCE);
    assertThatThrownBy(() -> validator.validate(reviews(
        LearnerMemoryEvidenceContract.Pattern.SAME_PROBLEM_RECOVERY,
        reference(1L, "RESOLVED"), reference(2L, "OBSERVED")), general, context))
        .isInstanceOf(LearnerMemoryOperationFailure.class)
        .extracting(error -> ((LearnerMemoryOperationFailure) error).code())
        .isEqualTo(LearnerMemoryOperationFailure.Code.INVALID_EVIDENCE);
  }

  private static LearnerMemoryClaimScope scope(
      LearnerMemoryClaimContract.Kind kind,
      LearnerMemoryClaimContract.Dimension dimension,
      Long tagId) {
    return new LearnerMemoryClaimScope(kind, dimension, tagId);
  }

  private static LearnerMemoryEvidenceReferences messages(
      LearnerMemoryEvidenceContract.Pattern pattern,
      long id,
      LearnerMemoryEvidenceContract.MessageRole role) {
    return new LearnerMemoryEvidenceReferences(pattern, List.of(), List.of(
        new LearnerMemoryEvidenceReferences.MessageReference(id, role)));
  }

  private static LearnerMemoryEvidenceReferences reviews(
      LearnerMemoryEvidenceContract.Pattern pattern,
      LearnerMemoryEvidenceReferences.ReviewReference... references) {
    return new LearnerMemoryEvidenceReferences(pattern, List.of(references), List.of());
  }

  private static LearnerMemoryEvidenceReferences.ReviewReference reference(long id, String role) {
    return new LearnerMemoryEvidenceReferences.ReviewReference(
        id, LearnerMemoryEvidenceContract.ReviewRole.valueOf(role));
  }

  private static LearnerMemoryEvidenceValidationContext.ReviewSource review(
      long id, String slug, int version, Set<Long> tags) {
    return new LearnerMemoryEvidenceValidationContext.ReviewSource(
        id, slug, version, tags, Instant.parse("2026-07-30T00:00:00Z").plusSeconds(id));
  }

  private static LearnerMemoryEvidenceValidationContext.MessageSource message(long id) {
    return new LearnerMemoryEvidenceValidationContext.MessageSource(
        id, Instant.parse("2026-07-30T00:00:00Z").plusSeconds(id));
  }
}
