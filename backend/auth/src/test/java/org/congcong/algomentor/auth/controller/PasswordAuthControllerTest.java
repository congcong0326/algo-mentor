package org.congcong.algomentor.auth.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.congcong.algomentor.auth.model.PasswordLoginRequest;
import org.congcong.algomentor.auth.model.PasswordRegisterRequest;
import org.congcong.algomentor.auth.security.AuthenticatedDaoAuthenticationProvider;
import org.congcong.algomentor.auth.security.PasswordUserDetailsService;
import org.congcong.algomentor.auth.service.AdminEmailRoleService;
import org.congcong.algomentor.auth.service.AuthPermissionService;
import org.congcong.algomentor.auth.service.OAuth2LoginUserServiceTest;
import org.congcong.algomentor.auth.service.PasswordUserService;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class PasswordAuthControllerTest {

  private static final Instant NOW = Instant.parse("2026-06-26T00:00:00Z");

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private final OAuth2LoginUserServiceTest.InMemoryAuthUserRepository repository =
      new OAuth2LoginUserServiceTest.InMemoryAuthUserRepository();
  private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    AdminEmailRoleService adminEmailRoleService = new AdminEmailRoleService(repository, List.of("admin@example.com"));
    PasswordUserService passwordUserService = new PasswordUserService(
        repository,
        repository,
        passwordEncoder,
        clock,
        adminEmailRoleService);
    PasswordUserDetailsService userDetailsService = new PasswordUserDetailsService(
        repository,
        repository,
        clock,
        adminEmailRoleService);
    HttpSessionSecurityContextRepository securityContextRepository = new HttpSessionSecurityContextRepository();
    PasswordAuthController controller = new PasswordAuthController(
        passwordUserService,
        new ProviderManager(new AuthenticatedDaoAuthenticationProvider(passwordEncoder, userDetailsService)),
        securityContextRepository,
        new AuthPermissionService());

    mockMvc = MockMvcBuilders
        .standaloneSetup(controller)
        .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
        .build();
  }

  @Test
  void registerCreatesSessionAndReturnsCurrentUser() throws Exception {
    MvcResult result = mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordRegisterRequest(
                "admin@example.com",
                "password-123",
                "Admin User"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("admin@example.com"))
        .andExpect(jsonPath("$.data.roles[0]").value("USER"))
        .andExpect(jsonPath("$.data.roles[1]").value("ADMIN"))
        .andExpect(jsonPath("$.data.permissions").isArray())
        .andExpect(jsonPath("$.data.permissions[3]").value("problem:read"))
        .andExpect(jsonPath("$.data.permissions[6]").value("debug:access"))
        .andReturn();

    Object context = result.getRequest().getSession(false).getAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    org.assertj.core.api.Assertions.assertThat(context).isInstanceOf(SecurityContext.class);
  }

  @Test
  void loginReturnsCurrentUserWhenPasswordMatches() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordRegisterRequest(
                "user@example.com",
                "password-123",
                "User Name"))))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordLoginRequest(
                "USER@example.com",
                "password-123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.roles[0]").value("USER"));
  }

  @Test
  void loginReturnsUnifiedErrorForWrongPassword() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordRegisterRequest(
                "user@example.com",
                "password-123",
                "User Name"))))
        .andExpect(status().isOk());

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordLoginRequest(
                "user@example.com",
                "wrong-password"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
  }

  @Test
  void disabledUserReceivesUnifiedLoginFailure() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordRegisterRequest(
                "disabled@example.com",
                "password-123",
                "Disabled User"))))
        .andExpect(status().isOk());
    setUserStatus("disabled@example.com", AuthUserStatus.DISABLED);

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordLoginRequest(
                "disabled@example.com",
                "password-123"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
  }

  @Test
  void deletedUserReceivesUnifiedLoginFailure() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordRegisterRequest(
                "deleted@example.com",
                "password-123",
                "Deleted User"))))
        .andExpect(status().isOk());
    setUserStatus("deleted@example.com", AuthUserStatus.DELETED);

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordLoginRequest(
                "deleted@example.com",
                "password-123"))))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
  }

  @Test
  void registerReturnsBadRequestWhenDisplayNameIsMissing() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordRegisterRequest(
                "user@example.com",
                "password-123",
                " "))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_DISPLAY_NAME_REQUIRED"))
        .andExpect(jsonPath("$.error.message").value("请输入昵称。"));
  }

  private void setUserStatus(String emailNormalized, AuthUserStatus status) {
    repository.setUserStatus(emailNormalized, status);
  }
}
