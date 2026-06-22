package org.congcong.algomentor.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(AuthConfigurationKeys.AUTH_PREFIX)
public class AuthProperties {

  private String loginSuccessUrl = "/";
  private String logoutSuccessUrl = "/";
  private Duration sessionTimeout = Duration.ofDays(7);
  private boolean cookieSecure;
  private String cookieSameSite = "Lax";

  public String getLoginSuccessUrl() {
    return loginSuccessUrl;
  }

  public void setLoginSuccessUrl(String loginSuccessUrl) {
    this.loginSuccessUrl = loginSuccessUrl;
  }

  public String getLogoutSuccessUrl() {
    return logoutSuccessUrl;
  }

  public void setLogoutSuccessUrl(String logoutSuccessUrl) {
    this.logoutSuccessUrl = logoutSuccessUrl;
  }

  public Duration getSessionTimeout() {
    return sessionTimeout;
  }

  public void setSessionTimeout(Duration sessionTimeout) {
    this.sessionTimeout = sessionTimeout;
  }

  public boolean isCookieSecure() {
    return cookieSecure;
  }

  public void setCookieSecure(boolean cookieSecure) {
    this.cookieSecure = cookieSecure;
  }

  public String getCookieSameSite() {
    return cookieSameSite;
  }

  public void setCookieSameSite(String cookieSameSite) {
    this.cookieSameSite = cookieSameSite;
  }
}
