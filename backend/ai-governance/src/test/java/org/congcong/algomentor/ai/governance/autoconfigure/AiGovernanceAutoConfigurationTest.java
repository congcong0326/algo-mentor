package org.congcong.algomentor.ai.governance.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AiGovernanceAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(AiGovernanceAutoConfiguration.class));

  @Test
  void loadsWithoutDataSourceAndExposesProperties() {
    contextRunner
        .withPropertyValues("algo-mentor.ai-governance.enabled=true")
        .run(context -> {
          assertThat(context).hasSingleBean(AiGovernanceProperties.class);
          assertThat(context).hasSingleBean(AiPurposePolicyResolver.class);
        });
  }
}
