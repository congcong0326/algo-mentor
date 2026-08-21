package org.congcong.algomentor.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import org.congcong.algomentor.auth.loginsettings.model.AuthLoginSettings;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class OAuthLoginEntryFilterTest {

  @Test
  void redirectsWhenTheProviderIsDisabled() throws Exception {
    OAuthLoginEntryFilter filter = new OAuthLoginEntryFilter(
        () -> new AuthLoginSettings((short) 1, true, true, true, false, true, null, null, Instant.EPOCH));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> {
      throw new AssertionError("disabled provider must not continue");
    });

    assertThat(response.getRedirectedUrl()).isEqualTo("/login?auth=provider-disabled");
  }

  @Test
  void leavesEnabledProviderEntryToSpringSecurity() throws Exception {
    OAuthLoginEntryFilter filter = new OAuthLoginEntryFilter(
        () -> new AuthLoginSettings((short) 1, true, true, true, true, false, null, null, Instant.EPOCH));
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/google");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicBoolean continued = new AtomicBoolean();

    filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> continued.set(true));

    assertThat(continued).isTrue();
  }
}
