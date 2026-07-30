package org.congcong.algomentor.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        "GOOGLE_CLIENT_SECRET="
    })
@AutoConfigureMockMvc
class AuthSecurityWithoutGoogleOAuth2Test {

  @Autowired
  private ObjectProvider<ClientRegistrationRepository> clientRegistrationRepository;

  @Autowired
  private MockMvc mockMvc;

  @Test
  void startsWithoutGoogleOAuth2Credentials() throws Exception {
    assertThat(clientRegistrationRepository.getIfAvailable()).isNull();
    mockMvc.perform(get("/oauth2/authorization/google"))
        .andExpect(status().isNotFound());
  }
}
