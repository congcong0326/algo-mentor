package org.congcong.algomentor.api.learningplan.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionPolicy;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionPolicyConstants;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.junit.jupiter.api.Test;

class PolicyBackedLearningPlanAiRevisionPolicyResolverTest {
  private final GenericPolicyType<LearningPlanAiRevisionPolicy> policyType = GenericPolicyType.of(
      LearningPlanAiRevisionPolicyConstants.TYPE_CODE,
      LearningPlanAiRevisionPolicy.class,
      LearningPlanAiRevisionPolicyContentValidator::validate);

  @Test
  void returnsDefaultCapabilitiesWhenNoPolicyMatches() {
    GenericPolicyQueryService queryService = mock(GenericPolicyQueryService.class);
    when(queryService.resolve(policyType, 7L)).thenReturn(Optional.empty());

    assertThat(new PolicyBackedLearningPlanAiRevisionPolicyResolver(queryService, policyType)
        .resolve(7L)).isEqualTo(new org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanAiRevisionCapabilities(false, false, true));
  }

  @Test
  void failsClosedWithUnavailableError() {
    GenericPolicyQueryService queryService = mock(GenericPolicyQueryService.class);
    when(queryService.resolve(policyType, 7L)).thenThrow(new IllegalStateException("unavailable"));

    assertThatThrownBy(() -> new PolicyBackedLearningPlanAiRevisionPolicyResolver(queryService, policyType).resolve(7L))
        .isInstanceOfSatisfying(LearningPlanException.class, error ->
            assertThat(error.code()).isEqualTo(LearningPlanAiRevisionPolicyConstants.POLICY_UNAVAILABLE_CODE));
  }
}
