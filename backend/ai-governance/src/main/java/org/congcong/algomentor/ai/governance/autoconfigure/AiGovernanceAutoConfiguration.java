package org.congcong.algomentor.ai.governance.autoconfigure;

import org.congcong.algomentor.ai.governance.policy.AiGovernanceProperties;
import org.congcong.algomentor.ai.governance.policy.AiPurposePolicyResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(AiGovernanceProperties.class)
public class AiGovernanceAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AiPurposePolicyResolver aiPurposePolicyResolver(AiGovernanceProperties properties) {
    return new AiPurposePolicyResolver(properties);
  }
}
