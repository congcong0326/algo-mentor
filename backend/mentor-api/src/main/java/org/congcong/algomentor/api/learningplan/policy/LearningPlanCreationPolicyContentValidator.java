package org.congcong.algomentor.api.learningplan.policy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Set;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicy;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyConstants;

/** 拒绝学习计划创建策略中的未知字段和 Jackson 数值强制转换。 */
public final class LearningPlanCreationPolicyContentValidator {

  private static final Set<String> CONTENT_FIELDS = Set.of(
      LearningPlanCreationPolicyConstants.MAX_SAVED_PLANS_FIELD,
      LearningPlanCreationPolicyConstants.DAILY_DRAFT_CREATION_LIMIT_FIELD,
      LearningPlanCreationPolicyConstants.DRAFT_RETENTION_DAYS_FIELD);

  private LearningPlanCreationPolicyContentValidator() {
  }

  public static void validate(JsonNode content, LearningPlanCreationPolicy policy) {
    if (content == null || !content.isObject() || content.size() != CONTENT_FIELDS.size()) {
      throw new IllegalArgumentException(
          "Learning plan creation policy content must contain exactly the supported fields.");
    }
    content.fieldNames().forEachRemaining(field -> {
      if (!CONTENT_FIELDS.contains(field)) {
        throw new IllegalArgumentException("Learning plan creation policy contains an unsupported field.");
      }
    });
    validateIntegralValue(
        content,
        LearningPlanCreationPolicyConstants.MAX_SAVED_PLANS_FIELD,
        policy.maxSavedPlans());
    validateIntegralValue(
        content,
        LearningPlanCreationPolicyConstants.DAILY_DRAFT_CREATION_LIMIT_FIELD,
        policy.dailyDraftCreationLimit());
    validateIntegralValue(
        content,
        LearningPlanCreationPolicyConstants.DRAFT_RETENTION_DAYS_FIELD,
        policy.draftRetentionDays());
  }

  private static void validateIntegralValue(JsonNode content, String field, int expected) {
    JsonNode value = content.get(field);
    if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()
        || value.intValue() != expected) {
      throw new IllegalArgumentException(field + " must be an integer without coercion.");
    }
  }
}
