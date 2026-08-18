package org.congcong.algomentor.mentor.application.profile.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceReferences;
import org.congcong.algomentor.mentor.application.profile.observability.LearnerMemoryMetrics;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperation;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperationBatch;
import org.congcong.algomentor.mentor.application.profile.review.history.CodeReviewVerification;
import org.congcong.algomentor.mentor.application.profile.review.snapshot.LearnerReviewFactSnapshot;

/** 严格映射模型的 Code Review operation；任何越界字段或目标均拒绝整个批次。 */
public final class LearnerMemoryCodeReviewStructuredOutputMapper {

  private final LearnerMemoryMetrics metrics;

  public LearnerMemoryCodeReviewStructuredOutputMapper() {
    this(LearnerMemoryMetrics.NOOP);
  }

  public LearnerMemoryCodeReviewStructuredOutputMapper(LearnerMemoryMetrics metrics) {
    this.metrics = metrics == null ? LearnerMemoryMetrics.NOOP : metrics;
  }

  public List<LearnerMemoryOperation> map(JsonNode output, LearnerMemoryCodeReviewUpdateAgentInput input) {
    if (output == null || !output.isObject() || output.size() != 1 || !output.has(LearnerMemoryCodeReviewJsonSchema.OPERATIONS)) {
      throw invalid();
    }
    JsonNode operations = output.path(LearnerMemoryCodeReviewJsonSchema.OPERATIONS);
    if (!operations.isArray() || operations.size() > LearnerMemoryOperationBatch.MAX_OPERATIONS) {
      throw invalid();
    }
    Map<Long, LearnerMemoryCodeReviewUpdateAgentInput.ActiveClaim> targets = input.activeClaims().stream()
        .collect(Collectors.toMap(LearnerMemoryCodeReviewUpdateAgentInput.ActiveClaim::revisionId, claim -> claim));
    Set<LearnerMemoryClaimScope> allowedScopes = input.allowedScopes();
    Set<Long> seenTargets = new LinkedHashSet<>();
    List<LearnerMemoryOperation> result = new ArrayList<>();
    for (JsonNode value : operations) {
      String action = requiredText(value, LearnerMemoryCodeReviewJsonSchema.ACTION);
      result.add(switch (action) {
        case "ADD" -> add(value, allowedScopes, input);
        case "CONFIRM" -> confirm(value, targets, seenTargets);
        case "REVISE" -> revise(value, targets, seenTargets, input);
        case "RETIRE" -> retire(value, targets, seenTargets);
        default -> throw invalid();
      });
    }
    return List.copyOf(result);
  }

  private LearnerMemoryOperation.Add add(
      JsonNode value,
      Set<LearnerMemoryClaimScope> allowedScopes,
      LearnerMemoryCodeReviewUpdateAgentInput input
  ) {
    requireFields(value, Set.of(
        LearnerMemoryCodeReviewJsonSchema.ACTION,
        LearnerMemoryCodeReviewJsonSchema.SCOPE,
        LearnerMemoryCodeReviewJsonSchema.CLAIM_TEXT,
        LearnerMemoryCodeReviewJsonSchema.OBSERVATION_TYPE,
        LearnerMemoryCodeReviewJsonSchema.PATTERN,
        LearnerMemoryCodeReviewJsonSchema.REASON,
        LearnerMemoryCodeReviewJsonSchema.REVIEW_EVIDENCE));
    LearnerMemoryClaimScope scope = scope(value.path(LearnerMemoryCodeReviewJsonSchema.SCOPE));
    if (!allowedScopes.contains(scope)) {
      throw invalid();
    }
    LearnerMemoryReviewObservationType observationType = observationType(value);
    validateObservationType(observationType, scope);
    LearnerMemoryEvidenceReferences references = evidence(value);
    validateObservation(observationType, scope, requiredText(value, LearnerMemoryCodeReviewJsonSchema.CLAIM_TEXT), references, input);
    metrics.recordReviewObservation(observationType.name(), "GENERATED");
    return new LearnerMemoryOperation.Add(
        scope,
        requiredText(value, LearnerMemoryCodeReviewJsonSchema.CLAIM_TEXT),
        references,
        requiredText(value, LearnerMemoryCodeReviewJsonSchema.REASON));
  }

  private LearnerMemoryOperation.Confirm confirm(
      JsonNode value,
      Map<Long, LearnerMemoryCodeReviewUpdateAgentInput.ActiveClaim> targets,
      Set<Long> seenTargets
  ) {
    requireFields(value, Set.of(
        LearnerMemoryCodeReviewJsonSchema.ACTION,
        LearnerMemoryCodeReviewJsonSchema.TARGET_REVISION_ID,
        LearnerMemoryCodeReviewJsonSchema.PATTERN,
        LearnerMemoryCodeReviewJsonSchema.REASON,
        LearnerMemoryCodeReviewJsonSchema.REVIEW_EVIDENCE));
    long target = target(value, targets, seenTargets);
    LearnerMemoryEvidenceReferences references = evidence(value);
    requireCompleteEvidence(references, targets.get(target));
    return new LearnerMemoryOperation.Confirm(target, references, requiredText(value, LearnerMemoryCodeReviewJsonSchema.REASON));
  }

  private LearnerMemoryOperation.Revise revise(
      JsonNode value,
      Map<Long, LearnerMemoryCodeReviewUpdateAgentInput.ActiveClaim> targets,
      Set<Long> seenTargets,
      LearnerMemoryCodeReviewUpdateAgentInput input
  ) {
    requireFields(value, Set.of(
        LearnerMemoryCodeReviewJsonSchema.ACTION,
        LearnerMemoryCodeReviewJsonSchema.TARGET_REVISION_ID,
        LearnerMemoryCodeReviewJsonSchema.CLAIM_TEXT,
        LearnerMemoryCodeReviewJsonSchema.OBSERVATION_TYPE,
        LearnerMemoryCodeReviewJsonSchema.PATTERN,
        LearnerMemoryCodeReviewJsonSchema.REASON,
        LearnerMemoryCodeReviewJsonSchema.REVIEW_EVIDENCE));
    long target = target(value, targets, seenTargets);
    LearnerMemoryReviewObservationType observationType = observationType(value);
    LearnerMemoryClaimScope scope = targets.get(target).scope();
    validateObservationType(observationType, scope);
    String claimText = requiredText(value, LearnerMemoryCodeReviewJsonSchema.CLAIM_TEXT);
    LearnerMemoryEvidenceReferences references = evidence(value);
    validateObservation(observationType, scope, claimText, references, input);
    metrics.recordReviewObservation(observationType.name(), "REVISED");
    return new LearnerMemoryOperation.Revise(
        target,
        claimText,
        references,
        requiredText(value, LearnerMemoryCodeReviewJsonSchema.REASON));
  }

  private LearnerMemoryOperation.Retire retire(
      JsonNode value,
      Map<Long, LearnerMemoryCodeReviewUpdateAgentInput.ActiveClaim> targets,
      Set<Long> seenTargets
  ) {
    requireFields(value, Set.of(
        LearnerMemoryCodeReviewJsonSchema.ACTION,
        LearnerMemoryCodeReviewJsonSchema.TARGET_REVISION_ID,
        LearnerMemoryCodeReviewJsonSchema.PATTERN,
        LearnerMemoryCodeReviewJsonSchema.REASON,
        LearnerMemoryCodeReviewJsonSchema.REVIEW_EVIDENCE));
    return new LearnerMemoryOperation.Retire(
        target(value, targets, seenTargets),
        evidence(value),
        requiredText(value, LearnerMemoryCodeReviewJsonSchema.REASON));
  }

  private long target(
      JsonNode value,
      Map<Long, LearnerMemoryCodeReviewUpdateAgentInput.ActiveClaim> targets,
      Set<Long> seenTargets
  ) {
    JsonNode node = value.path(LearnerMemoryCodeReviewJsonSchema.TARGET_REVISION_ID);
    if (!node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() <= 0
        || !targets.containsKey(node.asLong()) || !seenTargets.add(node.asLong())) {
      throw invalid();
    }
    return node.asLong();
  }

  private LearnerMemoryClaimScope scope(JsonNode value) {
    if (!value.isObject()) {
      throw invalid();
    }
    LearnerMemoryClaimContract.Kind kind = enumValue(value.path(LearnerMemoryCodeReviewJsonSchema.KIND),
        LearnerMemoryClaimContract.Kind.class);
    LearnerMemoryClaimContract.Dimension dimension = enumValue(value.path(LearnerMemoryCodeReviewJsonSchema.DIMENSION),
        LearnerMemoryClaimContract.Dimension.class);
    Long tagId = null;
    if (kind == LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT) {
      requireFields(value, Set.of(
          LearnerMemoryCodeReviewJsonSchema.KIND,
          LearnerMemoryCodeReviewJsonSchema.DIMENSION,
          LearnerMemoryCodeReviewJsonSchema.TAG_ID));
      JsonNode node = value.path(LearnerMemoryCodeReviewJsonSchema.TAG_ID);
      if (!node.isIntegralNumber() || !node.canConvertToLong() || node.asLong() <= 0) {
        throw invalid();
      }
      tagId = node.asLong();
    } else {
      requireFields(value, Set.of(LearnerMemoryCodeReviewJsonSchema.KIND, LearnerMemoryCodeReviewJsonSchema.DIMENSION));
    }
    if (kind != LearnerMemoryClaimContract.Kind.GENERAL_OBSERVATION
        && kind != LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT) {
      throw invalid();
    }
    try {
      return new LearnerMemoryClaimScope(kind, dimension, tagId);
    } catch (IllegalArgumentException exception) {
      throw invalid();
    }
  }

  private LearnerMemoryEvidenceReferences evidence(JsonNode value) {
    LearnerMemoryEvidenceContract.Pattern pattern = enumValue(value.path(LearnerMemoryCodeReviewJsonSchema.PATTERN),
        LearnerMemoryEvidenceContract.Pattern.class);
    if (pattern == LearnerMemoryEvidenceContract.Pattern.USER_DECLARATION
        || pattern == LearnerMemoryEvidenceContract.Pattern.USER_CORRECTION) {
      throw invalid();
    }
    JsonNode reviews = value.path(LearnerMemoryCodeReviewJsonSchema.REVIEW_EVIDENCE);
    if (!reviews.isArray() || reviews.isEmpty()) {
      throw invalid();
    }
    List<LearnerMemoryEvidenceReferences.ReviewReference> references = new ArrayList<>();
    for (JsonNode review : reviews) {
      requireFields(review, Set.of(LearnerMemoryCodeReviewJsonSchema.REVIEW_ID, LearnerMemoryCodeReviewJsonSchema.ROLE));
      JsonNode reviewId = review.path(LearnerMemoryCodeReviewJsonSchema.REVIEW_ID);
      if (!reviewId.isIntegralNumber() || !reviewId.canConvertToLong() || reviewId.asLong() <= 0) {
        throw invalid();
      }
      references.add(new LearnerMemoryEvidenceReferences.ReviewReference(
          reviewId.asLong(), enumValue(review.path(LearnerMemoryCodeReviewJsonSchema.ROLE),
              LearnerMemoryEvidenceContract.ReviewRole.class)));
    }
    try {
      return new LearnerMemoryEvidenceReferences(pattern, references, List.of());
    } catch (IllegalArgumentException exception) {
      throw invalid();
    }
  }

  private LearnerMemoryReviewObservationType observationType(JsonNode value) {
    return enumValue(value.path(LearnerMemoryCodeReviewJsonSchema.OBSERVATION_TYPE),
        LearnerMemoryReviewObservationType.class);
  }

  private void validateObservationType(
      LearnerMemoryReviewObservationType observationType,
      LearnerMemoryClaimScope scope
  ) {
    if (!observationType.supports(scope)) {
      metrics.recordReviewObservation(observationType.name(), "REJECTED");
      throw invalid();
    }
  }

  private void validateObservation(
      LearnerMemoryReviewObservationType observationType,
      LearnerMemoryClaimScope scope,
      String claimText,
      LearnerMemoryEvidenceReferences references,
      LearnerMemoryCodeReviewUpdateAgentInput input
  ) {
    LearnerReviewFactSnapshot snapshot = input.reviewFactSnapshot();
    languageGuard(claimText, snapshot);
    Map<Long, CodeReviewVerification> reviewsById = input.evidenceReviews().stream().collect(Collectors.toMap(
        CodeReviewVerification::reviewId, review -> review));
    List<CodeReviewVerification> evidenceReviews = references.reviews().stream()
        .map(reference -> reviewsById.get(reference.reviewId())).toList();
    if (evidenceReviews.stream().anyMatch(java.util.Objects::isNull)) {
      throw invalid();
    }
    Set<Long> snapshotReviewIds = input.scopeReviews().stream()
        .map(CodeReviewVerification::reviewId)
        .collect(Collectors.toSet());
    if (references.reviews().stream().anyMatch(reference -> !snapshotReviewIds.contains(reference.reviewId()))) {
      throw invalid();
    }
    validateEvidenceScope(scope, evidenceReviews);
    switch (observationType) {
      case CURRENT_STRENGTH -> validateCurrentStrength(snapshot, references, evidenceReviews, reviewsById);
      case RECOVERED_CHALLENGE -> validateRecoveredChallenge(snapshot, references, evidenceReviews);
      case ACTIVE_RISK -> validateActiveRisk(snapshot, references, evidenceReviews, reviewsById);
    }
  }

  private void validateCurrentStrength(
      LearnerReviewFactSnapshot snapshot,
      LearnerMemoryEvidenceReferences references,
      List<CodeReviewVerification> evidenceReviews,
      Map<Long, CodeReviewVerification> reviewsById
  ) {
    if (snapshot.overall().latestByProblem().passedCount() == 0 || evidenceReviews.isEmpty()
        || evidenceReviews.stream().anyMatch(review -> !review.passed() || !isLatest(review, reviewsById.values()))) {
      rejectObservation(LearnerMemoryReviewObservationType.CURRENT_STRENGTH);
    }
    if (references.reviews().stream().anyMatch(reference -> reference.role()
        != LearnerMemoryEvidenceContract.ReviewRole.RESOLVED)) {
      rejectObservation(LearnerMemoryReviewObservationType.CURRENT_STRENGTH);
    }
  }

  private void validateRecoveredChallenge(
      LearnerReviewFactSnapshot snapshot,
      LearnerMemoryEvidenceReferences references,
      List<CodeReviewVerification> evidenceReviews
  ) {
    if (snapshot.overall().recoveredFailureCount() == 0
        || !isRecoveryPattern(references.pattern())) {
      rejectObservation(LearnerMemoryReviewObservationType.RECOVERED_CHALLENGE);
    }
    Map<Long, LearnerMemoryEvidenceContract.ReviewRole> roles = references.reviews().stream().collect(Collectors.toMap(
        LearnerMemoryEvidenceReferences.ReviewReference::reviewId,
        LearnerMemoryEvidenceReferences.ReviewReference::role));
    Map<String, List<CodeReviewVerification>> reviewsByProblem = evidenceReviews.stream().collect(Collectors.groupingBy(
        CodeReviewVerification::problemSlug));
    boolean recovered = switch (references.pattern()) {
      case SAME_PROBLEM_RECOVERY -> reviewsByProblem.size() == 1
          && hasRecoveryTrajectory(evidenceReviews, roles);
      case CROSS_PROBLEM_RECOVERY -> reviewsByProblem.size() >= 2
          && reviewsByProblem.values().stream().allMatch(problemReviews -> hasRecoveryTrajectory(problemReviews, roles));
      default -> false;
    };
    if (!recovered) {
      rejectObservation(LearnerMemoryReviewObservationType.RECOVERED_CHALLENGE);
    }
  }

  private boolean isRecoveryPattern(LearnerMemoryEvidenceContract.Pattern pattern) {
    return pattern == LearnerMemoryEvidenceContract.Pattern.SAME_PROBLEM_RECOVERY
        || pattern == LearnerMemoryEvidenceContract.Pattern.CROSS_PROBLEM_RECOVERY;
  }

  private boolean hasRecoveryTrajectory(
      List<CodeReviewVerification> reviews,
      Map<Long, LearnerMemoryEvidenceContract.ReviewRole> roles
  ) {
    return reviews.stream().filter(review -> !review.passed()
            && roles.get(review.reviewId()) == LearnerMemoryEvidenceContract.ReviewRole.OBSERVED)
        .anyMatch(failed -> reviews.stream().anyMatch(passed -> passed.passed()
            && roles.get(passed.reviewId()) == LearnerMemoryEvidenceContract.ReviewRole.RESOLVED
            && sameProblemLater(failed, passed)));
  }

  private void validateActiveRisk(
      LearnerReviewFactSnapshot snapshot,
      LearnerMemoryEvidenceReferences references,
      List<CodeReviewVerification> evidenceReviews,
      Map<Long, CodeReviewVerification> reviewsById
  ) {
    if (snapshot.overall().unresolvedFailureCount() == 0 || evidenceReviews.stream()
        .noneMatch(review -> !review.passed() && isLatest(review, reviewsById.values()))) {
      rejectObservation(LearnerMemoryReviewObservationType.ACTIVE_RISK);
    }
    boolean hasObservedLatestFailure = references.reviews().stream().anyMatch(reference -> {
      CodeReviewVerification review = reviewsById.get(reference.reviewId());
      return reference.role() == LearnerMemoryEvidenceContract.ReviewRole.OBSERVED
          && review != null
          && !review.passed()
          && isLatest(review, reviewsById.values());
    });
    if (!hasObservedLatestFailure) {
      rejectObservation(LearnerMemoryReviewObservationType.ACTIVE_RISK);
    }
  }

  private void validateEvidenceScope(
      LearnerMemoryClaimScope scope,
      List<CodeReviewVerification> evidenceReviews
  ) {
    if (scope.kind() == LearnerMemoryClaimContract.Kind.TAG_ASSESSMENT && evidenceReviews.stream()
        .anyMatch(review -> !review.affectedTagIds().contains(scope.tagId()))) {
      throw invalid();
    }
  }

  private boolean sameProblemLater(CodeReviewVerification first, CodeReviewVerification second) {
    return first.problemSlug().equals(second.problemSlug()) && reviewOrder().compare(first, second) < 0;
  }

  private boolean isLatest(CodeReviewVerification review, java.util.Collection<CodeReviewVerification> allReviews) {
    return allReviews.stream().filter(candidate -> candidate.problemSlug().equals(review.problemSlug()))
        .max(reviewOrder()).map(review::equals).orElse(false);
  }

  private Comparator<CodeReviewVerification> reviewOrder() {
    return Comparator.comparing(CodeReviewVerification::createdAt)
        .thenComparingInt(CodeReviewVerification::versionNo)
        .thenComparingLong(CodeReviewVerification::reviewId);
  }

  private void languageGuard(String claimText, LearnerReviewFactSnapshot snapshot) {
    String normalized = claimText.toLowerCase(Locale.ROOT);
    boolean earlySampleStrongClaim = snapshot.coverage().historyDepth() == LearnerReviewFactSnapshot.HistoryDepth.EARLY_SAMPLE
        && (containsEnglishWord(normalized, "long-term", "always", "consistently", "stable")
            || containsAny(normalized, "长期", "一贯", "持续", "通常", "稳定"));
    boolean unsupportedFastFirstAttempt = snapshot.overall().firstAttemptByProblem().passedCount()
        < snapshot.overall().firstAttemptByProblem().totalCount()
        && containsAny(normalized, "usually fast", "quickly identify", "fast recognition", "通常能快速", "快速识别");
    boolean hidesFunctionalFailures = snapshot.overall().functionalFailureCount() > 0
        && (containsAny(normalized, "no functional issue", "no functional problem", "没有功能性问题", "未出现功能性问题")
            || (containsAny(normalized, "only", "仅", "只有")
                && containsAny(normalized, "format", "naming", "non-functional", "格式", "命名", "非功能")));
    if (earlySampleStrongClaim || unsupportedFastFirstAttempt || hidesFunctionalFailures) {
      metrics.recordLanguageGuardRejected();
      throw invalid();
    }
  }

  private boolean containsAny(String value, String... candidates) {
    for (String candidate : candidates) {
      if (value.contains(candidate)) {
        return true;
      }
    }
    return false;
  }

  private boolean containsEnglishWord(String value, String... candidates) {
    for (String candidate : candidates) {
      if (value.matches(".*\\b" + java.util.regex.Pattern.quote(candidate) + "\\b.*")) {
        return true;
      }
    }
    return false;
  }

  private void rejectObservation(LearnerMemoryReviewObservationType observationType) {
    metrics.recordReviewObservation(observationType.name(), "REJECTED");
    throw invalid();
  }

  private void requireCompleteEvidence(
      LearnerMemoryEvidenceReferences references,
      LearnerMemoryCodeReviewUpdateAgentInput.ActiveClaim claim
  ) {
    Map<Long, LearnerMemoryEvidenceContract.ReviewRole> supplied = references.reviews().stream()
        .collect(Collectors.toMap(
            LearnerMemoryEvidenceReferences.ReviewReference::reviewId,
            LearnerMemoryEvidenceReferences.ReviewReference::role));
    for (LearnerMemoryCodeReviewUpdateAgentInput.ReviewEvidence current : claim.existingReviewEvidence()) {
      if (supplied.get(current.reviewId()) != current.role()) {
        throw invalid();
      }
    }
  }

  private static void requireFields(JsonNode value, Set<String> fields) {
    if (!value.isObject() || value.size() != fields.size()) {
      throw invalid();
    }
    Set<String> actual = new LinkedHashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(fields)) {
      throw invalid();
    }
  }

  private static String requiredText(JsonNode value, String field) {
    JsonNode node = value.path(field);
    if (!node.isTextual() || node.asText().isBlank()) {
      throw invalid();
    }
    return node.asText().trim();
  }

  private static <T extends Enum<T>> T enumValue(JsonNode value, Class<T> type) {
    if (!value.isTextual()) {
      throw invalid();
    }
    try {
      return Enum.valueOf(type, value.asText());
    } catch (IllegalArgumentException exception) {
      throw invalid();
    }
  }

  private static IllegalArgumentException invalid() {
    return new IllegalArgumentException("Code review memory structured output is invalid");
  }
}
