package org.congcong.algomentor.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

  private final SimpleUrlAuthenticationFailureHandler delegate =
      new SimpleUrlAuthenticationFailureHandler("/?auth=failed");

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException exception
  ) throws IOException, ServletException {
    delegate.onAuthenticationFailure(request, response, exception);
  }
}
