package org.congcong.algomentor.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

public class OAuth2AuthenticationFailureHandler implements AuthenticationFailureHandler {

  private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationFailureHandler.class);

  private final SimpleUrlAuthenticationFailureHandler delegate =
      new SimpleUrlAuthenticationFailureHandler("/?auth=failed");

  @Override
  public void onAuthenticationFailure(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException exception
  ) throws IOException, ServletException {
    log.info(
        "OAuth2 authentication failed. {} exceptionType={} errorCode={}",
        AuthDiagnosticSupport.requestSummary(request),
        exception.getClass().getName(),
        errorCode(exception));
    delegate.onAuthenticationFailure(request, response, exception);
  }

  private static String errorCode(AuthenticationException exception) {
    if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
      return oauth2Exception.getError().getErrorCode();
    }
    return "n/a";
  }
}
