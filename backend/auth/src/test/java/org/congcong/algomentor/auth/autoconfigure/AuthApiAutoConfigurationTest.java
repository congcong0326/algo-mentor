package org.congcong.algomentor.auth.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.congcong.algomentor.auth.controller.PasswordAuthController;
import org.congcong.algomentor.auth.repository.mybatis.AuthUserMapper;
import org.congcong.algomentor.auth.security.PasswordUserDetailsService;
import org.congcong.algomentor.auth.service.OAuth2LoginUserService;
import org.congcong.algomentor.auth.service.PasswordUserService;
import org.congcong.algomentor.identity.autoconfigure.IdentityAutoConfiguration;
import org.congcong.algomentor.identity.repository.mybatis.IdentityUserMapper;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class AuthApiAutoConfigurationTest {

  @Test
  void createsAuthBeansWhenAuthAutoConfigurationIsDeclaredBeforeIdentity() {
    new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(AuthApiAutoConfiguration.class, IdentityAutoConfiguration.class))
        .withUserConfiguration(SqlSessionTemplateConfig.class)
        .run(context -> {
          assertThat(context).hasSingleBean(PasswordUserService.class);
          assertThat(context).hasSingleBean(PasswordUserDetailsService.class);
          assertThat(context).hasSingleBean(OAuth2LoginUserService.class);
          assertThat(context).hasSingleBean(PasswordAuthController.class);
        });
  }

  @Configuration(proxyBeanMethods = false)
  static class SqlSessionTemplateConfig {

    @Bean
    SqlSessionTemplate sqlSessionTemplate() {
      SqlSessionTemplate sqlSessionTemplate = mock(SqlSessionTemplate.class);
      when(sqlSessionTemplate.getMapper(AuthUserMapper.class)).thenReturn(mock(AuthUserMapper.class));
      when(sqlSessionTemplate.getMapper(IdentityUserMapper.class)).thenReturn(mock(IdentityUserMapper.class));
      return sqlSessionTemplate;
    }
  }
}
