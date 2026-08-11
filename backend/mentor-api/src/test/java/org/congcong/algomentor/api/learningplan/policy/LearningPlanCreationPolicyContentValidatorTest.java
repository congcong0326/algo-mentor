package org.congcong.algomentor.api.learningplan.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicy;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyConstants;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.junit.jupiter.api.Test;

class LearningPlanCreationPolicyContentValidatorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final GenericPolicyType<LearningPlanCreationPolicy> policyType = GenericPolicyType.of(
      LearningPlanCreationPolicyConstants.TYPE_CODE,
      LearningPlanCreationPolicy.class,
      LearningPlanCreationPolicyContentValidator::validate);

  @Test
  void acceptsExactIntegralPolicyShape() throws Exception {
    LearningPlanCreationPolicy policy = policyType.deserialize(objectMapper, objectMapper.readTree("""
        {"maxSavedPlans":30,"dailyDraftCreationLimit":5,"draftRetentionDays":14}
        """));

    assertThat(policy).isEqualTo(LearningPlanCreationPolicy.defaults());
  }

  @Test
  void rejectsUnknownAndCoercedFields() throws Exception {
    assertThatThrownBy(() -> policyType.deserialize(objectMapper, objectMapper.readTree("""
        {"maxSavedPlans":30,"dailyDraftCreationLimit":5,"draftRetentionDays":14,"unknown":1}
        """)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policyType.deserialize(objectMapper, objectMapper.readTree("""
        {"maxSavedPlans":"30","dailyDraftCreationLimit":5,"draftRetentionDays":14}
        """)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
