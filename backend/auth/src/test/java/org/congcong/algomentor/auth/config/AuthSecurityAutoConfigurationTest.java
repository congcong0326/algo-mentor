package org.congcong.algomentor.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.SessionCookieConfig;
import java.util.List;
import org.congcong.algomentor.auth.autoconfigure.AuthApiAutoConfiguration;
import org.congcong.algomentor.auth.model.AuthRole;
import org.congcong.algomentor.auth.model.AuthUserStatus;
import org.congcong.algomentor.auth.security.AuthAuthorities;
import org.congcong.algomentor.auth.security.AuthenticatedUserPrincipal;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

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

  @Test
  void permitsHealthEndpoint() throws Exception {
    mockMvc.perform(get("/api/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void protectedApiReturnsJson401WhenUnauthenticated() throws Exception {
    mockMvc.perform(get("/api/protected"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHENTICATED"));
  }

  @Test
  void oauth2AuthorizationEndpointRedirectsToProvider() throws Exception {
    mockMvc.perform(get("/oauth2/authorization/google"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", startsWith("https://accounts.google.com/o/oauth2/v2/auth")));
  }

  @Test
  void authenticatedCurrentUserEndpointReturnsPrincipal() throws Exception {
    mockMvc.perform(get("/api/auth/me").with(authentication(authenticationToken())))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.email").value("user@example.com"))
        .andExpect(jsonPath("$.data.roles[0]").value("USER"));
  }

  @Test
  void mutatingApiRequiresCsrfToken() throws Exception {
    mockMvc.perform(post("/api/protected").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());
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

  private static UsernamePasswordAuthenticationToken authenticationToken() {
    AuthenticatedUserPrincipal principal = new AuthenticatedUserPrincipal(
        42L,
        "user@example.com",
        "User Name",
        "https://example.com/avatar.png",
        List.of(AuthRole.USER),
        AuthUserStatus.ACTIVE);
    return new UsernamePasswordAuthenticationToken(
        principal,
        "n/a",
        AuthAuthorities.fromRoles(principal.roles()));
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
  }

  record StatusResponse(String status) {
  }
}
