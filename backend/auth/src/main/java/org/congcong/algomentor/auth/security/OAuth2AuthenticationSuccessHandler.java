package org.congcong.algomentor.auth.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.congcong.algomentor.auth.config.AuthSecurityPaths;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyErrorCode;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyException;
import org.congcong.algomentor.auth.session.policy.AuthSessionPolicyLoginService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

  private static final Logger log = LoggerFactory.getLogger(OAuth2AuthenticationSuccessHandler.class);

  private final SavedRequestAwareAuthenticationSuccessHandler delegate =
      new SavedRequestAwareAuthenticationSuccessHandler();
  private final AuthSessionPolicyLoginService sessionPolicyLoginService;

  public OAuth2AuthenticationSuccessHandler(String defaultTargetUrl) {
    this(defaultTargetUrl, null);
  }

  public OAuth2AuthenticationSuccessHandler(
      String defaultTargetUrl,
      AuthSessionPolicyLoginService sessionPolicyLoginService
  ) {
    delegate.setDefaultTargetUrl(defaultTargetUrl);
    this.sessionPolicyLoginService = sessionPolicyLoginService;
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
    try {
      if (sessionPolicyLoginService == null) {
        throw new AuthSessionPolicyException(
            AuthSessionPolicyErrorCode.AUTH_SESSION_POLICY_UNAVAILABLE,
            "用户会话策略暂不可用，请稍后重试。");
      }
      sessionPolicyLoginService.apply(authenticatedUserId(authentication), request.getSession(false));
    } catch (AuthSessionPolicyException exception) {
      SecurityContextHolder.clearContext();
      invalidateCurrentSession(request);
      log.warn("OAuth2 login rejected because auth session policy is unavailable. code={}", exception.code());
      response.sendRedirect(AuthSecurityPaths.OAUTH2_SESSION_POLICY_FAILURE_URL);
      return;
    }
    delegate.onAuthenticationSuccess(request, response, authentication);
  }

  private static long authenticatedUserId(Authentication authentication) {
    if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedOAuth2User user) {
      return user.authenticatedUserPrincipal().userId();
    }
    throw new AuthSessionPolicyException(
        AuthSessionPolicyErrorCode.AUTH_SESSION_POLICY_UNAVAILABLE,
        "OAuth2 登录主体不可用于会话策略控制。");
  }

  private static void invalidateCurrentSession(HttpServletRequest request) {
    HttpSession session = request.getSession(false);
    if (session == null) {
      return;
    }
    try {
      session.invalidate();
    } catch (IllegalStateException ignored) {
      // 登录控制服务已经清理当前 Session 时不需要再次处理。
    }
  }
}
