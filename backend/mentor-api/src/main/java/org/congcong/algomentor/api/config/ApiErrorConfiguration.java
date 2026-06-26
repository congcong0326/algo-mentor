package org.congcong.algomentor.api.config;

import java.util.List;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.common.api.ApiErrorLocales;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration(proxyBeanMethods = false)
public class ApiErrorConfiguration {

  @Bean
  public ApiErrorMessageResolver apiErrorMessageResolver() {
    return new ApiErrorMessageResolver();
  }

  @Bean
  public ApiErrorResponseFactory apiErrorResponseFactory(ApiErrorMessageResolver apiErrorMessageResolver) {
    return new ApiErrorResponseFactory(apiErrorMessageResolver);
  }

  @Bean
  public LocaleResolver localeResolver() {
    AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
    resolver.setDefaultLocale(ApiErrorLocales.DEFAULT);
    resolver.setSupportedLocales(List.of(ApiErrorLocales.ZH_CN, ApiErrorLocales.EN_US));
    return resolver;
  }
}
