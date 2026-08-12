package org.congcong.algomentor.api.learningplan.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionPolicy;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionPolicyConstants;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.junit.jupiter.api.Test;

class LearningPlanAiRevisionPolicyContentValidatorTest {
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final GenericPolicyType<LearningPlanAiRevisionPolicy> policyType = GenericPolicyType.of(
      LearningPlanAiRevisionPolicyConstants.TYPE_CODE,
      LearningPlanAiRevisionPolicy.class,
      LearningPlanAiRevisionPolicyContentValidator::validate);

  @Test
  void acceptsExactlyThreeBooleanFields() throws Exception {
    LearningPlanAiRevisionPolicy policy = policyType.deserialize(objectMapper, objectMapper.readTree("""
        {"templateDraftRevisionEnabled":true,"savedPlanRevisionEnabled":false,"personalizedDraftRevisionEnabled":true}
        """));

    assertThat(policy.capabilities().templateDraftRevisionEnabled()).isTrue();
    assertThat(policy.capabilities().savedPlanRevisionEnabled()).isFalse();
    assertThat(policy.capabilities().personalizedDraftRevisionEnabled()).isTrue();
  }

  @Test
  void rejectsMissingUnknownNullAndCoercedFields() throws Exception {
    assertThatThrownBy(() -> policyType.deserialize(objectMapper, objectMapper.readTree("""
        {"templateDraftRevisionEnabled":true,"savedPlanRevisionEnabled":false}
        """))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policyType.deserialize(objectMapper, objectMapper.readTree("""
        {"templateDraftRevisionEnabled":true,"savedPlanRevisionEnabled":false,"personalizedDraftRevisionEnabled":false,"extra":false}
        """))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policyType.deserialize(objectMapper, objectMapper.readTree("""
        {"templateDraftRevisionEnabled":null,"savedPlanRevisionEnabled":false,"personalizedDraftRevisionEnabled":false}
        """))).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policyType.deserialize(objectMapper, objectMapper.readTree("""
        {"templateDraftRevisionEnabled":"true","savedPlanRevisionEnabled":false,"personalizedDraftRevisionEnabled":false}
        """))).isInstanceOf(IllegalArgumentException.class);
  }
}
