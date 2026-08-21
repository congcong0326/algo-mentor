package org.congcong.algomentor.auth.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.congcong.algomentor.auth.config.AuthSecurityPaths;
import org.congcong.algomentor.auth.loginsettings.service.AuthLoginSettingsProvider;
import org.congcong.algomentor.auth.model.OAuthProvider;
import org.springframework.web.filter.OncePerRequestFilter;

/** 在跳转至 OAuth 提供商前读取运行时开关，避免关闭的入口继续发起授权流程。 */
public class OAuthLoginEntryFilter extends OncePerRequestFilter {

  private static final String PROVIDER_DISABLED_LOGIN_URL = "/login?auth=provider-disabled";

  private final AuthLoginSettingsProvider loginSettingsProvider;

  public OAuthLoginEntryFilter(AuthLoginSettingsProvider loginSettingsProvider) {
    this.loginSettingsProvider = loginSettingsProvider;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith(
        request.getContextPath() + AuthSecurityPaths.OAUTH2_AUTHORIZATION_BASE_URI + "/");
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {
    String path = request.getRequestURI().substring(request.getContextPath().length());
    String registrationId = path.substring((AuthSecurityPaths.OAUTH2_AUTHORIZATION_BASE_URI + "/").length());
    OAuthProvider provider = OAuthProvider.fromRegistrationId(registrationId).orElse(null);
    if (provider != null && !loginSettingsProvider.oauthLoginEnabled(provider)) {
      response.sendRedirect(request.getContextPath() + PROVIDER_DISABLED_LOGIN_URL);
      return;
    }
    filterChain.doFilter(request, response);
  }
}
