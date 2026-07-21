package org.congcong.algomentor.mentor.application.profile.review;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileDimension;
import org.congcong.algomentor.mentor.application.profile.LearnerProfileIdentity;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateAction;
import org.congcong.algomentor.mentor.application.profile.ProfileUpdateDecision;

/** 对模型输出执行全量白名单校验，任何越界决定都会拒绝整个批次。 */
public final class CodeReviewProfileStructuredOutputMapper {

  public List<ProfileUpdateDecision> map(
      JsonNode output,
      List<CodeReviewProfilePromptBuilder.Candidate> candidates
  ) {
    if (output == null || !output.isObject() || output.size() != 2
        || !output.has(CodeReviewProfileJsonSchema.GENERAL_OBSERVATIONS)
        || !output.has(CodeReviewProfileJsonSchema.TAG_ASSESSMENTS)) {
      throw new IllegalArgumentException("Code review profile structured output is invalid");
    }
    List<CodeReviewProfilePromptBuilder.Candidate> general = candidates.stream()
        .filter(candidate -> candidate.snapshot().identity().tagId() == null).toList();
    List<CodeReviewProfilePromptBuilder.Candidate> tags = candidates.stream()
        .filter(candidate -> candidate.snapshot().identity().tagId() != null).toList();
    Map<LearnerProfileIdentity, ProfileUpdateDecision> decisions = new java.util.HashMap<>();
    mapGeneral(output.path(CodeReviewProfileJsonSchema.GENERAL_OBSERVATIONS), general, decisions);
    mapTags(output.path(CodeReviewProfileJsonSchema.TAG_ASSESSMENTS), tags, decisions);
    if (decisions.size() != candidates.size()) {
      throw new IllegalArgumentException("Code review profile structured output is incomplete");
    }
    return candidates.stream().map(candidate -> decisions.get(candidate.snapshot().identity())).toList();
  }

  private void mapGeneral(
      JsonNode values,
      List<CodeReviewProfilePromptBuilder.Candidate> candidates,
      Map<LearnerProfileIdentity, ProfileUpdateDecision> decisions
  ) {
    Map<LearnerProfileDimension, LearnerProfileIdentity> allowed = candidates.stream()
        .collect(java.util.stream.Collectors.toMap(
            candidate -> candidate.snapshot().identity().dimension(),
            candidate -> candidate.snapshot().identity()));
    if (!values.isArray() || values.size() != allowed.size()) {
      throw new IllegalArgumentException("Code review profile general decision count is invalid");
    }
    for (JsonNode value : values) {
      requireFields(value, Set.of(
          CodeReviewProfileJsonSchema.DIMENSION,
          CodeReviewProfileJsonSchema.ACTION,
          CodeReviewProfileJsonSchema.CONTENT,
          CodeReviewProfileJsonSchema.REASON));
      LearnerProfileDimension dimension = enumValue(value.path(CodeReviewProfileJsonSchema.DIMENSION),
          LearnerProfileDimension.class);
      LearnerProfileIdentity identity = allowed.get(dimension);
      if (identity == null) {
        throw new IllegalArgumentException("Code review profile general dimension is not allowed");
      }
      put(decisions, identity, decision(value));
    }
  }

  private void mapTags(
      JsonNode values,
      List<CodeReviewProfilePromptBuilder.Candidate> candidates,
      Map<LearnerProfileIdentity, ProfileUpdateDecision> decisions
  ) {
    Map<Long, LearnerProfileIdentity> allowed = candidates.stream()
        .collect(java.util.stream.Collectors.toMap(
            candidate -> candidate.snapshot().identity().tagId(),
            candidate -> candidate.snapshot().identity()));
    if (!values.isArray() || values.size() != allowed.size()) {
      throw new IllegalArgumentException("Code review profile tag decision count is invalid");
    }
    for (JsonNode value : values) {
      requireFields(value, Set.of(
          CodeReviewProfileJsonSchema.TAG_ID,
          CodeReviewProfileJsonSchema.ACTION,
          CodeReviewProfileJsonSchema.CONTENT,
          CodeReviewProfileJsonSchema.REASON));
      JsonNode tagId = value.path(CodeReviewProfileJsonSchema.TAG_ID);
      if (!tagId.isIntegralNumber() || !tagId.canConvertToLong() || tagId.asLong() < 1) {
        throw new IllegalArgumentException("Code review profile tag id is invalid");
      }
      LearnerProfileIdentity identity = allowed.get(tagId.asLong());
      if (identity == null) {
        throw new IllegalArgumentException("Code review profile tag id is not allowed");
      }
      put(decisions, identity, decision(value));
    }
  }

  private ProfileUpdateDecision decision(JsonNode value) {
    ProfileUpdateAction action = enumValue(value.path(CodeReviewProfileJsonSchema.ACTION), ProfileUpdateAction.class);
    JsonNode content = value.path(CodeReviewProfileJsonSchema.CONTENT);
    JsonNode reason = value.path(CodeReviewProfileJsonSchema.REASON);
    if (!content.isTextual() || !reason.isTextual()) {
      throw new IllegalArgumentException("Code review profile decision fields are invalid");
    }
    String normalized = content.asText().trim();
    if (action == ProfileUpdateAction.REPLACE && normalized.isBlank()) {
      throw new IllegalArgumentException("Code review profile replacement content is blank");
    }
    return new ProfileUpdateDecision(action, action == ProfileUpdateAction.REPLACE ? normalized : null, reason.asText().trim());
  }

  private void put(
      Map<LearnerProfileIdentity, ProfileUpdateDecision> decisions,
      LearnerProfileIdentity identity,
      ProfileUpdateDecision decision
  ) {
    if (decisions.putIfAbsent(identity, decision) != null) {
      throw new IllegalArgumentException("Code review profile output has duplicate decision");
    }
  }

  private void requireFields(JsonNode value, Set<String> fields) {
    if (!value.isObject() || value.size() != fields.size()) {
      throw new IllegalArgumentException("Code review profile output item is invalid");
    }
    LinkedHashSet<String> actual = new LinkedHashSet<>();
    value.fieldNames().forEachRemaining(actual::add);
    if (!actual.equals(fields)) {
      throw new IllegalArgumentException("Code review profile output has unsupported fields");
    }
  }

  private <T extends Enum<T>> T enumValue(JsonNode value, Class<T> type) {
    if (!value.isTextual()) {
      throw new IllegalArgumentException("Code review profile enum is invalid");
    }
    try {
      return Enum.valueOf(type, value.asText());
    } catch (IllegalArgumentException exception) {
      throw new IllegalArgumentException("Code review profile enum is not allowed", exception);
    }
  }
}
