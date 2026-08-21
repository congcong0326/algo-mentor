package org.congcong.algomentor.auth.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.congcong.algomentor.auth.config.AuthProperties;
import org.congcong.algomentor.auth.model.CompletePasswordResetRequest;
import org.congcong.algomentor.auth.model.PasswordLoginRequest;
import org.congcong.algomentor.auth.model.PasswordRegisterRequest;
import org.congcong.algomentor.auth.passwordreset.PasswordResetErrorCode;
import org.congcong.algomentor.auth.passwordreset.PasswordResetException;
import org.congcong.algomentor.auth.passwordreset.PasswordResetService;
import org.congcong.algomentor.auth.security.AuthAuthorities;
import org.congcong.algomentor.auth.security.AuthenticatedDaoAuthenticationProvider;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.AuthenticatedUserResponseHeaders;
import org.congcong.algomentor.auth.security.PasswordUserDetailsService;
import org.congcong.algomentor.auth.service.AdminEmailRoleService;
import org.congcong.algomentor.auth.service.AuthPermissionService;
import org.congcong.algomentor.auth.service.OAuth2LoginUserServiceTest;
import org.congcong.algomentor.auth.service.PasswordUserService;
import org.congcong.algomentor.common.api.ApiErrorMessageResolver;
import org.congcong.algomentor.common.api.ApiErrorResponseFactory;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
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
  private PasswordResetService passwordResetService;
  private AuthProperties authProperties;

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
    passwordResetService = mock(PasswordResetService.class);
    authProperties = new AuthProperties();
    PasswordAuthController controller = new PasswordAuthController(
        passwordUserService,
        new ProviderManager(new AuthenticatedDaoAuthenticationProvider(passwordEncoder, userDetailsService)),
        securityContextRepository,
        new ApiErrorResponseFactory(new ApiErrorMessageResolver()),
        new CurrentUserResponseFactory(new AuthPermissionService()),
        passwordResetService,
        null,
        authProperties);

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
        .andExpect(header().string(AuthenticatedUserResponseHeaders.USER, "admin@example.com"))
        .andExpect(header().string(AuthenticatedUserResponseHeaders.USER_ID, "1"))
        .andExpect(jsonPath("$.data.email").value("admin@example.com"))
        .andExpect(jsonPath("$.data.roles[0]").value("USER"))
        .andExpect(jsonPath("$.data.roles[1]").value("ADMIN"))
        .andExpect(jsonPath("$.data.permissions").isArray())
        .andExpect(jsonPath("$.data.permissions[3]").value("problem:read"))
        .andExpect(jsonPath("$.data.permissions[8]").value("beta-access:manage"))
        .andExpect(jsonPath("$.data.permissions[9]").value("auth-settings:manage"))
        .andExpect(jsonPath("$.data.permissions[13]").value("session:manage"))
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
        .andExpect(header().string(AuthenticatedUserResponseHeaders.USER, "user@example.com"))
        .andExpect(header().string(AuthenticatedUserResponseHeaders.USER_ID, "1"))
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.roles[0]").value("USER"));
  }

  @Test
  void existingPasswordUserCanLoginWhenNewAccountRegistrationIsDisabled() throws Exception {
    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordRegisterRequest(
                "user@example.com",
                "password-123",
                "User Name"))))
        .andExpect(status().isOk());
    authProperties.setAccountRegistrationEnabled(false);

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordLoginRequest(
                "user@example.com",
                "password-123"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("user@example.com"));
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
  void rejectsPasswordLoginBeforeAuthenticationWhenFeatureIsDisabled() throws Exception {
    authProperties.setPasswordLoginEnabled(false);

    mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordLoginRequest(
                "user@example.com",
                "password-123"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_PASSWORD_LOGIN_DISABLED"));
  }

  @Test
  void rejectsPasswordRegistrationBeforeUserCreationWhenFeatureIsDisabled() throws Exception {
    authProperties.setPasswordRegistrationEnabled(false);

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordRegisterRequest(
                "user@example.com",
                "password-123",
                "User Name"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_PASSWORD_REGISTRATION_DISABLED"));
  }

  @Test
  void rejectsPasswordRegistrationWhenPasswordLoginIsDisabled() throws Exception {
    authProperties.setPasswordLoginEnabled(false);

    mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new PasswordRegisterRequest(
                "user@example.com",
                "password-123",
                "User Name"))))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error.code").value("AUTH_PASSWORD_REGISTRATION_DISABLED"));
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

  @Test
  void completePasswordResetUpgradesRestrictedSession() throws Exception {
    AuthenticatedUserPrincipal restrictedPrincipal = restrictedPrincipal();
    AuthenticatedUserPrincipal completedPrincipal = restrictedPrincipal.withPasswordChangeRequired(false);
    when(passwordResetService.completeReset(
        restrictedPrincipal,
        "new-password-123",
        "new-password-123")).thenReturn(completedPrincipal);
    UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
        restrictedPrincipal,
        null,
        AuthAuthorities.fromRoles(restrictedPrincipal.roles()));

    MvcResult result = mockMvc.perform(post("/api/auth/password/complete-reset")
            .principal(authentication)
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new CompletePasswordResetRequest(
                "new-password-123",
                "new-password-123"))))
        .andExpect(status().isOk())
        .andExpect(header().string(AuthenticatedUserResponseHeaders.USER, "member@example.com"))
        .andExpect(header().string(AuthenticatedUserResponseHeaders.USER_ID, "42"))
        .andExpect(jsonPath("$.data.passwordChangeRequired").value(false))
        .andReturn();

    SecurityContext context = (SecurityContext) result.getRequest().getSession(false).getAttribute(
        HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
    org.assertj.core.api.Assertions.assertThat(context.getAuthentication().getPrincipal())
        .isEqualTo(completedPrincipal);
  }

  @Test
  void completePasswordResetUsesStableLocalizedValidationError() throws Exception {
    AuthenticatedUserPrincipal restrictedPrincipal = restrictedPrincipal();
    when(passwordResetService.completeReset(restrictedPrincipal, "short", "different"))
        .thenThrow(new PasswordResetException(
            PasswordResetErrorCode.AUTH_REQUEST_INVALID,
            "fallback"));
    UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
        restrictedPrincipal,
        null,
        AuthAuthorities.fromRoles(restrictedPrincipal.roles()));

    mockMvc.perform(post("/api/auth/password/complete-reset")
            .principal(authentication)
            .header("Accept-Language", "en-US")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsBytes(new CompletePasswordResetRequest("short", "different"))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error.code").value("AUTH_REQUEST_INVALID"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.AUTH_REQUEST_INVALID"))
        .andExpect(jsonPath("$.error.message").value("The authentication request is invalid."));
  }

  private void setUserStatus(String emailNormalized, AuthUserStatus status) {
    repository.setUserStatus(emailNormalized, status);
  }

  private static AuthenticatedUserPrincipal restrictedPrincipal() {
    return new AuthenticatedUserPrincipal(
        42L,
        "member@example.com",
        "Member",
        null,
        List.of(AuthRole.USER),
        AuthUserStatus.ACTIVE,
        true);
  }
}
