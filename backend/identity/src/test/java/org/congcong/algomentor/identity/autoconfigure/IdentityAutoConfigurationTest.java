package org.congcong.algomentor.identity.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.congcong.algomentor.identity.controller.AdminUserController;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;
import org.congcong.algomentor.identity.repository.mybatis.IdentityUserMapper;
import org.congcong.algomentor.identity.service.IdentityUserService;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class IdentityAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(IdentityAutoConfiguration.class));

  @Test
  void createsIdentityBeansWhenSqlSessionTemplateExists() {
    contextRunner
        .withUserConfiguration(SqlSessionTemplateConfig.class)
        .run(context -> {
          assertThat(context).hasSingleBean(IdentityUserMapper.class);
          assertThat(context).hasSingleBean(IdentityUserRepository.class);
          assertThat(context).hasSingleBean(IdentityUserService.class);
          assertThat(context).hasSingleBean(AdminUserController.class);
        });
  }

  @Configuration(proxyBeanMethods = false)
  static class SqlSessionTemplateConfig {

    @Bean
    SqlSessionTemplate sqlSessionTemplate() {
      SqlSessionTemplate sqlSessionTemplate = mock(SqlSessionTemplate.class);
      when(sqlSessionTemplate.getMapper(IdentityUserMapper.class)).thenReturn(mock(IdentityUserMapper.class));
      return sqlSessionTemplate;
    }
  }
}
