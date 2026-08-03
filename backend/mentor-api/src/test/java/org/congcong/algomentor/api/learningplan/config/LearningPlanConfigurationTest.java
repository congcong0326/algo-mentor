package org.congcong.algomentor.api.learningplan.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.congcong.algomentor.mentor.application.learningplan.personalization.LearningPlanPersonalizationMetrics;
import org.congcong.algomentor.mentor.application.learningplan.personalization.MicrometerLearningPlanPersonalizationMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class LearningPlanConfigurationTest {

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
