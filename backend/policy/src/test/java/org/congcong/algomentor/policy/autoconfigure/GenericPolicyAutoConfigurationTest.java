package org.congcong.algomentor.policy.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Modifier;
import org.congcong.algomentor.cache.config.CacheAutoConfiguration;
import org.congcong.algomentor.policy.service.GenericPolicyManagementService;
import org.congcong.algomentor.policy.type.GenericPolicyType;
import org.congcong.algomentor.policy.type.GenericPolicyTypeRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class GenericPolicyAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class, GenericPolicyAutoConfiguration.class))
      .withUserConfiguration(RegisteredTypeConfiguration.class);

  @Test
  void registersBusinessProvidedPolicyTypeBean() {
    contextRunner.run(context -> {
      GenericPolicyTypeRegistry registry = context.getBean(GenericPolicyTypeRegistry.class);

      assertThat(registry.require("test-policy")).isSameAs(context.getBean("testPolicyType"));
      assertThat(context).hasSingleBean(org.congcong.algomentor.policy.cache.PolicySetCache.class);
    });
  }

  @Test
  void keepsTransactionalManagementServiceCglibProxyable() {
    assertThat(Modifier.isFinal(GenericPolicyManagementService.class.getModifiers())).isFalse();
  }

  @Configuration(proxyBeanMethods = false)
  static class RegisteredTypeConfiguration {

    @Bean
    GenericPolicyType<String> testPolicyType() {
      return GenericPolicyType.of("test-policy", String.class);
    }
  }
}
