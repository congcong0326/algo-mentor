package org.congcong.algomentor.mentor.application.profile.evidence.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceReferences;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceValidationContext;

/** 只从已校验的 evidence 结构派生 grade，不接收模型 confidence 或 grade。 */
public final class LearnerMemoryEvidenceGradeCalculator {

  public LearnerMemoryEvidenceContract.Grade calculate(
      LearnerMemoryEvidenceReferences references,
      LearnerMemoryEvidenceValidationContext context) {
    if (references.pattern() == LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION
        || references.pattern() == LearnerMemoryEvidenceContract.Pattern.USER_CORRECTION) {
      return LearnerMemoryEvidenceContract.Grade.USER_AUTHORED;
    }
    List<LearnerMemoryEvidenceValidationContext.ReviewSource> reviews = references.reviews().stream()
        .map(reference -> context.review(reference.reviewId()))
        .toList();
    long problemCount = reviews.stream()
        .map(LearnerMemoryEvidenceValidationContext.ReviewSource::problemSlug)
        .distinct()
        .count();
    Map<String, Long> versionsByProblem = reviews.stream().collect(Collectors.groupingBy(
        LearnerMemoryEvidenceValidationContext.ReviewSource::problemSlug,
        Collectors.mapping(LearnerMemoryEvidenceValidationContext.ReviewSource::versionNo,
            Collectors.toSet())))
        .entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> (long) entry.getValue().size()));
    boolean hasLongitudinal = versionsByProblem.values().stream().anyMatch(count -> count >= 2);
    if (problemCount >= 2 && hasLongitudinal) {
      return LearnerMemoryEvidenceContract.Grade.STRONG;
    }
    if (problemCount >= 2 || hasLongitudinal) {
      return LearnerMemoryEvidenceContract.Grade.SUPPORTED;
    }
    return LearnerMemoryEvidenceContract.Grade.LIMITED;
  }
}
