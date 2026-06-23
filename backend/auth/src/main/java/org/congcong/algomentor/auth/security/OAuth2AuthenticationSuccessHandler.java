package org.congcong.algomentor.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

  private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

  private final SavedRequestAwareAuthenticationSuccessHandler delegate =
      new SavedRequestAwareAuthenticationSuccessHandler();

  public OAuth2AuthenticationSuccessHandler(String defaultTargetUrl) {
    delegate.setDefaultTargetUrl(defaultTargetUrl);
  }

  @Override
  public void onAuthenticationSuccess(
      HttpServletRequest request,
      HttpServletResponse response,
      Authentication authentication
  ) throws IOException, ServletException {
    log.info(
        "OAuth2 authentication succeeded. {} {}",
        AuthDiagnosticSupport.requestSummary(request),
        AuthDiagnosticSupport.authenticationSummary(authentication));
    delegate.onAuthenticationSuccess(request, response, authentication);
  }
}
