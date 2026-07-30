package org.congcong.algomentor.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
    classes = AuthSecurityAutoConfigurationTest.TestApplication.class,
    properties = {
        "GOOGLE_CLIENT_ID=",
        "GOOGLE_CLIENT_SECRET=",
        "GITHUB_CLIENT_ID=test-github-client-id",
        "GITHUB_CLIENT_SECRET=test-github-client-secret"
    })
@AutoConfigureMockMvc
class AuthSecurityWithGitHubOAuth2Test {

  @Autowired
  private ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;

  @Autowired
  private MockMvc mockMvc;

  @Test
  void startsWithGitHubAsTheOnlyOAuth2Provider() throws Exception {
    ClientRegistrationRepository registrations = clientRegistrationRepository.getIfAvailable();
    assertThat(registrations).isNotNull();
    assertThat(registrations.findByRegistrationId("google")).isNull();
    assertThat(registrations.findByRegistrationId("github")).isNotNull();

    mockMvc.perform(get("/oauth2/authorization/github"))
        .andExpect(status().is3xxRedirection())
        .andExpect(header().string("Location", startsWith("https://github.com/login/oauth/authorize")));
  }
}
