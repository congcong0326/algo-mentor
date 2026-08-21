package org.congcong.algomentor.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PrivatePageRobotsFilterTest {

  private final PrivatePageRobotsFilter filter = new PrivatePageRobotsFilter();

  @Test
  void marksPrivatePagesNoindexButLeavesPublicSeoPagesIndexable() throws Exception {
    MockHttpServletRequest privateRequest = new MockHttpServletRequest("GET", "/me");
    MockHttpServletResponse privateResponse = new MockHttpServletResponse();
    filter.doFilter(privateRequest, privateResponse, new MockFilterChain());
    assertThat(privateResponse.getHeader("X-Robots-Tag")).isEqualTo("noindex, nofollow");

    MockHttpServletRequest passwordRequest = new MockHttpServletRequest("GET", "/password/change-required");
    MockHttpServletResponse passwordResponse = new MockHttpServletResponse();
    filter.doFilter(passwordRequest, passwordResponse, new MockFilterChain());
    assertThat(passwordResponse.getHeader("X-Robots-Tag")).isEqualTo("noindex, nofollow");

    MockHttpServletRequest publicRequest = new MockHttpServletRequest("GET", "/problems/two-sum");
    MockHttpServletResponse publicResponse = new MockHttpServletResponse();
    FilterChain chain = new MockFilterChain();
    filter.doFilter(publicRequest, publicResponse, chain);
    assertThat(publicResponse.getHeader("X-Robots-Tag")).isNull();
  }
}
