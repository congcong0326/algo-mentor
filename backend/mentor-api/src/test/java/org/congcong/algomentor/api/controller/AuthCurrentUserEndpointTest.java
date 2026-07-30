package org.congcong.algomentor.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.controller.CurrentUserController;
import org.congcong.algomentor.auth.controller.CurrentUserResponseFactory;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.congcong.algomentor.auth.service.AuthPermissionService;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CurrentUserController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(AuthCurrentUserEndpointTest.TestConfig.class)
class AuthCurrentUserEndpointTest {

  @jakarta.annotation.Resource
  private MockMvc mockMvc;

  @Test
  void meEndpointIsRegisteredByAuthModule() throws Exception {
    mockMvc.perform(get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(42))
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.roles[0]").value("USER"))
        .andExpect(jsonPath("$.data.permissions[0]").value("learning-plan:read:own"));
  }

  @TestConfiguration(proxyBeanMethods = false)
  static class TestConfig {

    @Bean
    @Primary
    CurrentUserIdProvider currentUserIdProvider() {
      return () -> Optional.of(new AuthenticatedUserPrincipal(
          42L,
          "user@example.com",
          "User Name",
          "https://example.com/avatar.png",
          List.of(AuthRole.USER),
          AuthUserStatus.ACTIVE));
    }

    @Bean
    ApiErrorResponseFactory apiErrorResponseFactory() {
      return new ApiErrorResponseFactory(new ApiErrorMessageResolver());
    }

    @Bean
    CurrentUserResponseFactory currentUserResponseFactory() {
      return new CurrentUserResponseFactory(new AuthPermissionService());
    }

    @Bean
    CurrentUserController currentUserController(
        CurrentUserIdProvider currentUserIdProvider,
        ApiErrorResponseFactory apiErrorResponseFactory,
        CurrentUserResponseFactory currentUserResponseFactory
    ) {
      return new CurrentUserController(currentUserIdProvider, apiErrorResponseFactory, currentUserResponseFactory);
    }
  }
}
