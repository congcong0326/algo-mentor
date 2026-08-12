package org.congcong.algomentor.api.learningplan.policy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionPolicy;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionPolicyConstants;

public final class LearningPlanAiRevisionPolicyContentValidator {
  private static final Set<String> FIELDS = Set.of(
      LearningPlanAiRevisionPolicyConstants.TEMPLATE_DRAFT_REVISION_ENABLED_FIELD,
      LearningPlanAiRevisionPolicyConstants.SAVED_PLAN_REVISION_ENABLED_FIELD,
      LearningPlanAiRevisionPolicyConstants.PERSONALIZED_DRAFT_REVISION_ENABLED_FIELD);

  private LearningPlanAiRevisionPolicyContentValidator() {
  }

  public static void validate(JsonNode content, LearningPlanAiRevisionPolicy policy) {
    if (content == null || !content.isObject() || content.size() != FIELDS.size()) {
      throw new IllegalArgumentException("Learning plan AI revision policy must contain exactly three fields.");
    }
    content.fieldNames().forEachRemaining(field -> {
      if (!FIELDS.contains(field)) {
        throw new IllegalArgumentException("Learning plan AI revision policy contains an unsupported field.");
      }
      if (!content.get(field).isBoolean()) {
        throw new IllegalArgumentException(field + " must be a JSON boolean without coercion.");
      }
    });
    check(content, LearningPlanAiRevisionPolicyConstants.TEMPLATE_DRAFT_REVISION_ENABLED_FIELD,
        policy.templateDraftRevisionEnabled());
    check(content, LearningPlanAiRevisionPolicyConstants.SAVED_PLAN_REVISION_ENABLED_FIELD,
        policy.savedPlanRevisionEnabled());
    check(content, LearningPlanAiRevisionPolicyConstants.PERSONALIZED_DRAFT_REVISION_ENABLED_FIELD,
        policy.personalizedDraftRevisionEnabled());
  }

  private static void check(JsonNode content, String field, boolean expected) {
    JsonNode value = content.get(field);
    if (value == null || !value.isBoolean() || value.booleanValue() != expected) {
      throw new IllegalArgumentException(field + " must be a JSON boolean without coercion.");
    }
  }
}
