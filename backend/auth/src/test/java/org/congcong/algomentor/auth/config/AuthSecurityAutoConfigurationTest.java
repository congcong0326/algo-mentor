package org.congcong.algomentor.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.SessionCookieConfig;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.congcong.algomentor.auth.autoconfigure.AuthApiAutoConfiguration;
import org.congcong.algomentor.auth.security.AuthAuthorities;
import org.congcong.algomentor.auth.security.AuthenticatedOidcUser;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.congcong.algomentor.auth.security.OAuth2AuthenticationFailureHandler;
import org.congcong.algomentor.identity.model.AuthUser;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import org.springframework.boot.web.servlet.server.CookieSameSiteSupplier;
import org.springframework.boot.web.server.Cookie.SameSite;
import org.springframework.mock.web.MockServletContext;
import org.springframework.core.convert.ConversionService;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.ClassUtils;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.session.config.SessionRepositoryCustomizer;
import org.springframework.session.jdbc.JdbcIndexedSessionRepository;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.congcong.algomentor.identity.model.AuthRole;
import org.congcong.algomentor.identity.model.AuthUserStatus;
import org.congcong.algomentor.identity.model.IdentityUserPage;
import org.congcong.algomentor.identity.model.IdentityUserSearchQuery;
import org.congcong.algomentor.identity.repository.IdentityUserRepository;

@ExtendWith(SpringExtension.class)
@SpringBootTest(
    classes = AuthSecurityAutoConfigurationTest.TestApplication.class,
    properties = {
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret"
    })
@AutoConfigureMockMvc
class AuthSecurityAutoConfigurationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ServletContextInitializer authSessionCookieInitializer;

  @Autowired
  private CookieSameSiteSupplier authSessionCookieSameSiteSupplier;

  @Autowired
  private ConversionService springSessionConversionService;

  @Autowired
  private ObjectProvider<SessionRepositoryCustomizer<JdbcIndexedSessionRepository>> jdbcSessionRepositoryCustomizers;

  @Autowired
  private FakeIdentityUserRepository identityUsers;

  @BeforeEach
  void setUpIdentityUsers() {
    identityUsers.clear();
    identityUsers.save(authUser(42L, AuthUserStatus.ACTIVE));
  }

  @Test
  void permitsHealthEndpoint() throws Exception {
    mockMvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void protectedApiReturnsJson401WhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/protected").header("Accept-Language", "en-US"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"))
        .andExpect(jsonPath("$.error.messageKey").value("api.error.AUTH_UNAUTHENTICATED"))
        .andExpect(jsonPath("$.error.message").value("You are not signed in or your session has expired."));
  }

  @Test
  void unauthenticatedCurrentUserEndpointIssuesCsrfCookieForPasswordLogin() throws Exception {
    mockMvc.perform(get("/api/auth/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(cookie().exists("XSRF-TOKEN"))
        .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
  }

  @Test
  void loginPathIsReservedForFrontend() throws Exception {
    mockMvc.perform(get("/login"))
        .andExpect(status().isNotFound());
  }

  @Test
  void oauth2FailureRedirectsToFrontendLoginPage() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    new OAuth2AuthenticationFailureHandler().onAuthenticationFailure(
        new MockHttpServletRequest(),
        response,
        new OAuth2AuthenticationException(new OAuth2Error("invalid_request")));

    assertThat(response.getRedirectedUrl()).isEqualTo("/login?auth=failed");
  }

  @Test
  void oauth2AuthorizationEndpointRedirectsToProvider() throws Exception {
    mockMvc.perform(get("/oauth2/authorization/google"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", startsWith("https://accounts.google.com/o/oauth2/v2/auth")));
  }

  @Test
  void oidcJwtDecoderIsAvailableForGoogleLogin() {
    assertThat(ClassUtils.isPresent(
        "org.springframework.security.oauth2.jwt.JwtDecoder",
        getClass().getClassLoader()))
        .as("Google OAuth2 Login requests openid scope, so Spring Security must be able to validate id_token")
        .isTrue();
  }

  @Test
  void authenticatedCurrentUserEndpointReturnsPrincipal() throws Exception {
    mockMvc.perform(get("/api/auth/me").with(authentication(authenticationToken())))
        .andExpect(status().isOk())
        .andExpect(cookie().exists("XSRF-TOKEN"))
        .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.roles[0]").value("USER"));
  }

  @Test
  void rejectsAuthenticatedApiRequestWhenIdentityUserIsNoLongerActive() throws Exception {
    identityUsers.save(authUser(42L, AuthUserStatus.DISABLED));

    mockMvc.perform(get("/api/protected").with(authentication(authenticationToken())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));
  }

  @Test
  void logoutAcceptsCookieCsrfTokenIssuedByCurrentUserEndpoint() throws Exception {
    String csrfToken = mockMvc.perform(get("/api/auth/me").with(authentication(authenticationToken())))
        .andExpect(status().isOk())
        .andReturn()
        .getResponse()
        .getCookie("XSRF-TOKEN")
        .getValue();

    mockMvc.perform(post(AuthSecurityPaths.AUTH_LOGOUT_PATH)
            .cookie(new Cookie("XSRF-TOKEN", csrfToken))
            .header("X-XSRF-TOKEN", csrfToken)
            .with(authentication(authenticationToken())))
        .andExpect(status().is3xxRedirection());
  }

  @Test
  void mutatingApiRequiresCsrfToken() throws Exception {
    mockMvc.perform(post("/api/protected").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  void registerAndLoginEndpointsArePublicButStillRequireCsrfToken() throws Exception {
    mockMvc.perform(post(AuthSecurityPaths.AUTH_REGISTER_PATH).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
    mockMvc.perform(post(AuthSecurityPaths.AUTH_LOGIN_PATH).contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
  }

  @Test
  void adminApiRequiresAdminRole() throws Exception {
    mockMvc.perform(get("/api/admin/users").with(authentication(authenticationToken(AuthRole.USER))))
        .andExpect(status().isForbidden());

    mockMvc.perform(get("/api/admin/users").with(authentication(authenticationToken(AuthRole.ADMIN))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("admin"));
  }

  @Test
  void agentConversationApiRequiresAdminRole() throws Exception {
    mockMvc.perform(get("/api/agent/conversations/ping").with(authentication(authenticationToken(AuthRole.USER))))
        .andExpect(status().isForbidden());

    mockMvc.perform(get("/api/agent/conversations/ping").with(authentication(authenticationToken(AuthRole.ADMIN))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("agent"));
  }

  @Test
  void appliesSessionCookiePropertiesFromAuthConfiguration() throws Exception {
    MockServletContext servletContext = new MockServletContext();

    authSessionCookieInitializer.onStartup(servletContext);

    SessionCookieConfig cookieConfig = servletContext.getSessionCookieConfig();
    assertThat(cookieConfig.isHttpOnly()).isTrue();
    assertThat(cookieConfig.isSecure()).isFalse();
    assertThat(servletContext.getSessionTimeout()).isEqualTo(10080);
  }

  @Test
  void suppliesSameSiteForSessionCookie() {
    jakarta.servlet.http.Cookie sessionCookie = new jakarta.servlet.http.Cookie("JSESSIONID", "session");
    jakarta.servlet.http.Cookie otherCookie = new jakarta.servlet.http.Cookie("OTHER", "value");

    assertThat(authSessionCookieSameSiteSupplier.getSameSite(sessionCookie)).isEqualTo(SameSite.LAX);
    assertThat(authSessionCookieSameSiteSupplier.getSameSite(otherCookie)).isNull();
  }

  @Test
  void springSessionConversionServiceSerializesSecurityContext() {
    SecurityContextImpl securityContext = securityContext();

    byte[] serialized = springSessionConversionService.convert(securityContext, byte[].class);
    Object deserialized = springSessionConversionService.convert(serialized, Object.class);

    assertThat(deserialized).isInstanceOf(SecurityContextImpl.class);
  }

  @Test
  void passwordAuthenticationNameUsesStableUserId() {
    UsernamePasswordAuthenticationToken authentication = authenticationToken();

    assertThat(authentication.getName()).isEqualTo("42");
  }

  @Test
  void jdbcSessionAttributeInsertUsesPostgreSqlUpsert() {
    JdbcIndexedSessionRepository repository = new JdbcIndexedSessionRepository(
        mock(JdbcOperations.class),
        mock(TransactionOperations.class));

    jdbcSessionRepositoryCustomizers.orderedStream().forEach(customizer -> customizer.customize(repository));

    assertThat((String) ReflectionTestUtils.getField(repository, "createSessionAttributeQuery"))
        .contains("ON CONFLICT (SESSION_PRIMARY_ID, ATTRIBUTE_NAME)")
        .contains("DO UPDATE SET ATTRIBUTE_BYTES = EXCLUDED.ATTRIBUTE_BYTES");
  }

  @Test
  void oauth2SecurityContextIsJavaSerializable() throws Exception {
    Object deserialized = deserialize(serialize(securityContext()));

    assertThat(deserialized).isInstanceOf(SecurityContextImpl.class);
  }

  private static UsernamePasswordAuthenticationToken authenticationToken() {
    return authenticationToken(AuthRole.USER);
  }

  private static UsernamePasswordAuthenticationToken authenticationToken(AuthRole role) {
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
        42L,
        "user@example.com",
        "User Name",
        "https://example.com/avatar.png",
        List.of(role),
        AuthUserStatus.ACTIVE);
    return new UsernamePasswordAuthenticationToken(
        principal,
        "n/a",
        AuthAuthorities.fromRoles(principal.roles()));
  }

  private static SecurityContextImpl securityContext() {
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
        42L,
        "user@example.com",
        "User Name",
        "https://example.com/avatar.png",
        List.of(AuthRole.USER),
        AuthUserStatus.ACTIVE);
    Instant issuedAt = Instant.parse("2026-06-23T00:00:00Z");
    OidcIdToken idToken = new OidcIdToken(
        "id-token",
        issuedAt,
        issuedAt.plusSeconds(3600),
        Map.of(IdTokenClaimNames.SUB, "google-sub"));
    DefaultOidcUser oidcUser = new DefaultOidcUser(AuthAuthorities.fromRoles(principal.roles()), idToken);
    AuthenticatedOidcUser authenticatedOidcUser = new AuthenticatedOidcUser(
        principal,
        oidcUser,
        AuthAuthorities.fromRoles(principal.roles()));
    OAuth2AuthenticationToken authentication = new OAuth2AuthenticationToken(
        authenticatedOidcUser,
        AuthAuthorities.fromRoles(principal.roles()),
        "google");
    return new SecurityContextImpl(authentication);
  }

  private static byte[] serialize(Object object) throws Exception {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
      output.writeObject(object);
    }
    return bytes.toByteArray();
  }

  private static Object deserialize(byte[] bytes) throws Exception {
    try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
      return input.readObject();
    }
  }

  @SpringBootConfiguration
  @ImportAutoConfiguration({
      JacksonAutoConfiguration.class,
      HttpMessageConvertersAutoConfiguration.class,
      WebMvcAutoConfiguration.class,
      AuthApiAutoConfiguration.class,
      AuthSecurityAutoConfiguration.class
  })
  @Import(TestControllers.class)
  static class TestApplication {
    @Bean
    FakeIdentityUserRepository identityUserRepository() {
      return new FakeIdentityUserRepository();
    }
  }

  @RestController
  static class TestControllers {

    @GetMapping("/api/health")
    public StatusResponse health() {
      return new StatusResponse("UP");
    }

    @GetMapping("/api/protected")
    public StatusResponse protectedGet() {
      return new StatusResponse("protected");
    }

    @PostMapping("/api/protected")
    public StatusResponse protectedPost() {
      return new StatusResponse("protected");
    }

    @GetMapping("/api/admin/users")
    public StatusResponse adminUsers() {
      return new StatusResponse("admin");
    }

    @GetMapping("/api/agent/conversations/ping")
    public StatusResponse agentConversationPing() {
      return new StatusResponse("agent");
    }
  }

  record StatusResponse(String status) {
  }

  private static AuthUser authUser(long userId, AuthUserStatus status) {
    return new AuthUser(
        userId,
        "user@example.com",
        "user@example.com",
        "User Name",
        "https://example.com/avatar.png",
        status,
        Instant.parse("2026-06-30T00:00:00Z"),
        Instant.parse("2026-06-30T00:00:00Z"),
        null,
        null,
        null);
  }

  static class FakeIdentityUserRepository implements IdentityUserRepository {
    private final Map<Long, AuthUser> users = new HashMap<>();

    void clear() {
      users.clear();
    }

    void save(AuthUser user) {
      users.put(user.id(), user);
    }

    @Override
    public Optional<AuthUser> findUserById(long userId) {
      return Optional.ofNullable(users.get(userId));
    }

    @Override
    public Optional<AuthUser> findUserByEmailNormalized(String emailNormalized) {
      throw new UnsupportedOperationException("findUserByEmailNormalized is not used by this test.");
    }

    @Override
    public AuthUser createUser(
        String email,
        String emailNormalized,
        String displayName,
        String avatarUrl,
        AuthUserStatus status,
        Instant now
    ) {
      throw new UnsupportedOperationException("createUser is not used by this test.");
    }

    @Override
    public void addRole(long userId, AuthRole role) {
      throw new UnsupportedOperationException("addRole is not used by this test.");
    }

    @Override
    public List<AuthRole> findRoles(long userId) {
      throw new UnsupportedOperationException("findRoles is not used by this test.");
    }

    @Override
    public AuthUser updateLastLoginAt(long userId, Instant lastLoginAt) {
      throw new UnsupportedOperationException("updateLastLoginAt is not used by this test.");
    }

    @Override
    public IdentityUserPage searchUsers(IdentityUserSearchQuery query) {
      throw new UnsupportedOperationException("searchUsers is not used by this test.");
    }

    @Override
    public boolean updateUserStatus(
        long userId,
        AuthUserStatus expectedStatus,
        AuthUserStatus status,
        Instant updatedAt
    ) {
      throw new UnsupportedOperationException("updateUserStatus is not used by this test.");
    }

    @Override
    public boolean softDeleteUser(long userId, long operatorUserId, AuthUserStatus expectedStatus, Instant deletedAt) {
      throw new UnsupportedOperationException("softDeleteUser is not used by this test.");
    }
  }
}
