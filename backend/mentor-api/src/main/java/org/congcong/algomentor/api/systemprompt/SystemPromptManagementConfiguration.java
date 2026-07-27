package org.congcong.algomentor.api.systemprompt;

import org.congcong.algomentor.mentor.application.prompt.CodeDefaultManagedSystemPromptResolver;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitionRegistry;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptDefinitions;
import org.congcong.algomentor.mentor.application.prompt.ManagedSystemPromptResolver;
import org.congcong.algomentor.policy.service.GenericPolicyQueryService;
import org.congcong.algomentor.policy.autoconfigure.GenericPolicyAutoConfiguration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** 系统提示词 definition、通用策略适配和管理目录的装配入口。 */
@AutoConfiguration(before = GenericPolicyAutoConfiguration.class)
@EnableConfigurationProperties(SystemPromptPolicyProperties.class)
public class SystemPromptManagementConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public ManagedSystemPromptDefinitionRegistry managedSystemPromptDefinitionRegistry() {
    return new ManagedSystemPromptDefinitionRegistry(ManagedSystemPromptDefinitions.all());
  }

  @Bean
  @ConditionalOnMissingBean
  public PromptPolicyTypeContributor promptPolicyTypeContributor(
      ManagedSystemPromptDefinitionRegistry definitionRegistry
  ) {
    return new PromptPolicyTypeContributor(definitionRegistry);
  }

  @Bean
  @ConditionalOnMissingBean(ManagedSystemPromptResolver.class)
  public ManagedSystemPromptResolver managedSystemPromptResolver(
      ManagedSystemPromptDefinitionRegistry definitionRegistry,
      PromptPolicyTypeContributor typeContributor,
      ObjectProvider<GenericPolicyQueryService> queryService,
      SystemPromptPolicyProperties properties
  ) {
    GenericPolicyQueryService policyQueryService = queryService.getIfAvailable();
    if (policyQueryService == null) {
      return new CodeDefaultManagedSystemPromptResolver(definitionRegistry);
    }
    return new PolicyBackedSystemPromptResolver(
        definitionRegistry, typeContributor, policyQueryService, properties.isEnabled());
  }

}
