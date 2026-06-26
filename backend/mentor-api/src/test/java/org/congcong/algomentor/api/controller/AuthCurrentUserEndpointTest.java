package org.congcong.algomentor.api.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.CurrentUserIdProvider;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
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
  }
}
