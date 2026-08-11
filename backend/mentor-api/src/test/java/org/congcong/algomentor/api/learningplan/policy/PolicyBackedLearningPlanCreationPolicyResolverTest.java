package org.congcong.algomentor.api.learningplan.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.congcong.algomentor.mentor.application.learningplan.LearningPlanException;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicy;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyConstants;
import org.congcong.algomentor.policy.model.PolicyMatchSource;
import org.congcong.algomentor.policy.model.ResolvedPolicy;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.junit.jupiter.api.Test;

class PolicyBackedLearningPlanCreationPolicyResolverTest {

  private final GenericPolicyType<LearningPlanCreationPolicy> policyType = GenericPolicyType.of(
      LearningPlanCreationPolicyConstants.TYPE_CODE,
      LearningPlanCreationPolicy.class,
      LearningPlanCreationPolicyContentValidator::validate);

  @Test
  void returnsCodeDefaultsWhenNoPolicyMatches() {
    GenericPolicyQueryService queryService = mock(GenericPolicyQueryService.class);
    when(queryService.resolve(policyType, 7L)).thenReturn(Optional.empty());

    LearningPlanCreationPolicy resolved = new PolicyBackedLearningPlanCreationPolicyResolver(
        queryService, policyType).resolve(7L);

    assertThat(resolved).isEqualTo(LearningPlanCreationPolicy.defaults());
  }

  @Test
  void returnsUserSpecificPolicyContent() {
    GenericPolicyQueryService queryService = mock(GenericPolicyQueryService.class);
    LearningPlanCreationPolicy userPolicy = new LearningPlanCreationPolicy(80, 12, 30);
    when(queryService.resolve(policyType, 7L)).thenReturn(Optional.of(new ResolvedPolicy<>(
        10L,
        LearningPlanCreationPolicyConstants.TYPE_CODE,
        "internal user capacity",
        1,
        userPolicy,
        PolicyMatchSource.USER,
        7L,
        2L)));

    LearningPlanCreationPolicy resolved = new PolicyBackedLearningPlanCreationPolicyResolver(
        queryService, policyType).resolve(7L);

    assertThat(resolved).isEqualTo(userPolicy);
  }

  @Test
  void failsClosedWhenGenericPolicyResolutionFails() {
    GenericPolicyQueryService queryService = mock(GenericPolicyQueryService.class);
    when(queryService.resolve(policyType, 7L)).thenThrow(new IllegalStateException("database unavailable"));

    assertThatThrownBy(() -> new PolicyBackedLearningPlanCreationPolicyResolver(
        queryService, policyType).resolve(7L))
        .isInstanceOfSatisfying(LearningPlanException.class, exception ->
            assertThat(exception.code()).isEqualTo(
                LearningPlanCreationPolicyConstants.POLICY_UNAVAILABLE_CODE));
  }
}
