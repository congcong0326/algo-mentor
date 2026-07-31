package org.congcong.algomentor.mentor.application.profile.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimContract;
import org.congcong.algomentor.mentor.application.profile.claim.model.LearnerMemoryClaimScope;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceContract;
import org.congcong.algomentor.mentor.application.profile.evidence.model.LearnerMemoryEvidenceReferences;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperation;
import org.congcong.algomentor.mentor.application.profile.operation.model.LearnerMemoryOperationBatch;

/** 严格映射模型的 Code Review operation；任何越界字段或目标均拒绝整个批次。 */
public final class LearnerMemoryCodeReviewStructuredOutputMapper {

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
        case "ADD" -> add(value, allowedScopes);
        case "CONFIRM" -> confirm(value, targets, seenTargets);
        case "REVISE" -> revise(value, targets, seenTargets);
        case "RETIRE" -> retire(value, targets, seenTargets);
        default -> throw invalid();
      });
    }
    return List.copyOf(result);
  }

  private LearnerMemoryOperation.Add add(JsonNode value, Set<LearnerMemoryClaimScope> allowedScopes) {
    requireFields(value, Set.of(
        LearnerMemoryCodeReviewJsonSchema.ACTION,
        LearnerMemoryCodeReviewJsonSchema.SCOPE,
        LearnerMemoryCodeReviewJsonSchema.CLAIM_TEXT,
        LearnerMemoryCodeReviewJsonSchema.PATTERN,
        LearnerMemoryCodeReviewJsonSchema.REASON,
        LearnerMemoryCodeReviewJsonSchema.REVIEW_EVIDENCE));
    LearnerMemoryClaimScope scope = scope(value.path(LearnerMemoryCodeReviewJsonSchema.SCOPE));
    if (!allowedScopes.contains(scope)) {
      throw invalid();
    }
    return new LearnerMemoryOperation.Add(
        scope,
        requiredText(value, LearnerMemoryCodeReviewJsonSchema.CLAIM_TEXT),
        evidence(value),
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
      Set<Long> seenTargets
  ) {
    requireFields(value, Set.of(
        LearnerMemoryCodeReviewJsonSchema.ACTION,
        LearnerMemoryCodeReviewJsonSchema.TARGET_REVISION_ID,
        LearnerMemoryCodeReviewJsonSchema.CLAIM_TEXT,
        LearnerMemoryCodeReviewJsonSchema.PATTERN,
        LearnerMemoryCodeReviewJsonSchema.REASON,
        LearnerMemoryCodeReviewJsonSchema.REVIEW_EVIDENCE));
    return new LearnerMemoryOperation.Revise(
        target(value, targets, seenTargets),
        requiredText(value, LearnerMemoryCodeReviewJsonSchema.CLAIM_TEXT),
        evidence(value),
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
