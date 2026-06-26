package org.congcong.algomentor.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.util.List;
import java.util.Optional;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CurrentUserControllerTest {

  private MockMvc mockMvc;
  private Optional<AuthenticatedUserPrincipal> currentUser;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    mockMvc = MockMvcBuilders
        .standaloneSetup(new CurrentUserController(() -> currentUser))
        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
        .build();
  }

  @Test
  void meReturnsCurrentUserProfileEnvelope() throws Exception {
    currentUser = Optional.of(new AuthenticatedUserPrincipal(
        42L,
        "user@example.com",
        "User Name",
        "https://example.com/avatar.png",
        List.of(AuthRole.USER),
        AuthUserStatus.ACTIVE));

    mockMvc.perform(get("/api/auth/me"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.data.id").value(42))
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.displayName").value("User Name"))
        .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar.png"))
        .andExpect(jsonPath("$.data.roles[0]").value("USER"))
        .andExpect(jsonPath("$.data.permissions[0]").value("learning-plan:read:own"))
        .andExpect(jsonPath("$.data.permissions[1]").value("learning-plan:write:own"))
        .andExpect(jsonPath("$.data.permissions[2]").value("practice-session:write:own"))
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));
  }

  @Test
  void meReturns401WhenCurrentUserIsUnavailable() throws Exception {
    currentUser = Optional.empty();

    mockMvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.AUTH_UNAUTHENTICATED"))
        .andExpect(jsonPath("$.error.message").value("当前请求未登录或登录状态已失效。"));
  }

  @Test
  void meReturnsLocalized401WhenCurrentUserIsUnavailable() throws Exception {
    currentUser = Optional.empty();

    mockMvc.perform(get("/api/auth/me").header("Accept-Language", "en-US"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.AUTH_UNAUTHENTICATED"))
        .andExpect(jsonPath("$.error.message").value("You are not signed in or your session has expired."));
  }
}
