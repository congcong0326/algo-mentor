package org.congcong.algomentor.api.learningplan.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.congcong.algomentor.mentor.application.learningplan.policy.LearningPlanCreationPolicyConstants;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationMetrics;
import org.congcong.algomentor.mentor.application.learningplan.personalization.MicrometerLearningPlanPersonalizationMetrics;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.congcong.algomentor.policy.type.GenericPolicyTypeExposure;
import org.congcong.algomentor.policy.type.GenericPolicyTypeRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class LearningPlanConfigurationTest {

  @Test
  void registersLearningPlanCreationAsAnInternalGenericPolicyType() {
    GenericPolicyType<?> policyType = new LearningPlanConfiguration().learningPlanCreationPolicyType();
    GenericPolicyTypeRegistry registry = new GenericPolicyTypeRegistry(List.of(policyType));

    assertThat(policyType.typeCode()).isEqualTo(LearningPlanCreationPolicyConstants.TYPE_CODE);
    assertThat(policyType.exposure()).isEqualTo(GenericPolicyTypeExposure.INTERNAL_ONLY);
    assertThat(registry.require(LearningPlanCreationPolicyConstants.TYPE_CODE)).isSameAs(policyType);
  }

  @Test
  void personalizationMetricsFallsBackToNoopWithoutMicrometer() {
    @SuppressWarnings("unchecked")
    ObjectProvider<MeterRegistry> meterRegistry = mock(ObjectProvider.class);
    when(meterRegistry.getIfAvailable()).thenReturn(null);

    LearningPlanPersonalizationMetrics metrics = new LearningPlanConfiguration()
        .learningPlanPersonalizationMetrics(meterRegistry);

    assertThat(metrics).isSameAs(LearningPlanPersonalizationMetrics.NOOP);
  }

  @Test
  void personalizationMetricsUsesMicrometerWhenAvailable() {
    @SuppressWarnings("unchecked")
    ObjectProvider<MeterRegistry> meterRegistry = mock(ObjectProvider.class);
    when(meterRegistry.getIfAvailable()).thenReturn(new SimpleMeterRegistry());

    LearningPlanPersonalizationMetrics metrics = new LearningPlanConfiguration()
        .learningPlanPersonalizationMetrics(meterRegistry);

    assertThat(metrics).isInstanceOf(MicrometerLearningPlanPersonalizationMetrics.class);
  }
}
