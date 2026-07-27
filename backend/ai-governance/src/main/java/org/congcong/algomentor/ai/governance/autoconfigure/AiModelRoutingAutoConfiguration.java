package org.congcong.algomentor.ai.governance.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.ai.governance.metrics.AiModelRouteMetrics;
import org.congcong.algomentor.ai.governance.provider.repository.AiConfiguredModelRepository;
import org.congcong.algomentor.ai.governance.provider.repository.AiProviderInstanceRepository;
import org.congcong.algomentor.ai.governance.provider.runtime.ProviderClientRegistry;
import org.congcong.algomentor.ai.governance.routing.AiModelRoutePolicyTypeContributor;
import org.congcong.algomentor.ai.governance.routing.AiModelRouteResolver;
import org.congcong.algomentor.ai.governance.routing.DefaultAiModelRouteResolver;
import org.congcong.algomentor.llm.core.provider.LlmProviderAdapterRegistry;
import org.congcong.algomentor.policy.autoconfigure.GenericPolicyAutoConfiguration;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.ObjectProvider;

/** 在通用策略查询服务可用后装配模型路由运行时解析器。 */
@AutoConfiguration(after = GenericPolicyAutoConfiguration.class)
public class AiModelRoutingAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public AiModelRouteMetrics aiModelRouteMetrics(ObjectProvider<MeterRegistry> meterRegistryProvider) {
    return new AiModelRouteMetrics(meterRegistryProvider.getIfAvailable());
  }

  @Bean
  @ConditionalOnBean({GenericPolicyQueryService.class, AiModelRoutePolicyTypeContributor.class,
      AiConfiguredModelRepository.class, AiProviderInstanceRepository.class,
      LlmProviderAdapterRegistry.class, ProviderClientRegistry.class})
  @ConditionalOnMissingBean
  public AiModelRouteResolver aiModelRouteResolver(
      GenericPolicyQueryService policyQueryService,
      AiModelRoutePolicyTypeContributor typeContributor,
      AiConfiguredModelRepository modelRepository,
      AiProviderInstanceRepository providerRepository,
      LlmProviderAdapterRegistry adapterRegistry,
      ProviderClientRegistry clientRegistry,
      AiModelRouteMetrics metrics
  ) {
    return new DefaultAiModelRouteResolver(
        policyQueryService,
        typeContributor,
        modelRepository,
        providerRepository,
        adapterRegistry,
        clientRegistry,
        metrics);
  }
}
