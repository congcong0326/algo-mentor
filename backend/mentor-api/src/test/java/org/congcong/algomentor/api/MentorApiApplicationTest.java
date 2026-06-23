package org.congcong.algomentor.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@SpringBootTest
class MentorApiApplicationTest {

  @Autowired
  private ClientRegistrationRepository clientRegistrationRepository;

  @Test
  void contextLoads() {
  }

  @Test
  void googleOidcProviderHasJwkSetUri() {
    ClientRegistration google = clientRegistrationRepository.findByRegistrationId("google");

    assertThat(google).isNotNull();
    assertThat(google.getProviderDetails().getJwkSetUri())
        .isEqualTo("https://www.googleapis.com/oauth2/v3/certs");
  }
}
