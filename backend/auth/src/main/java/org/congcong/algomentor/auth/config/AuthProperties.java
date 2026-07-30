package org.congcong.algomentor.auth.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(AuthConfigurationKeys.AUTH_PREFIX)
public class AuthProperties {

  private String loginSuccessUrl = "/";
  private String logoutSuccessUrl = "/";
  private Duration sessionTimeout = Duration.ofDays(7);
  private Duration sessionMonitoringActiveWindow = Duration.ofMinutes(5);
  private boolean cookieSecure;
  private String cookieSameSite = "Lax";
  private List<String> adminEmails = List.of();
  private boolean passwordLoginEnabled = true;
  private boolean passwordRegistrationEnabled = true;

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
    if (sessionTimeout == null || sessionTimeout.isZero() || sessionTimeout.isNegative()
        || sessionTimeout.toSeconds() < 1 || sessionTimeout.toSeconds() > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("sessionTimeout must be positive and fit Servlet Session seconds.");
    }
    this.sessionTimeout = sessionTimeout;
  }

  public Duration getSessionMonitoringActiveWindow() {
    return sessionMonitoringActiveWindow;
  }

  public void setSessionMonitoringActiveWindow(Duration sessionMonitoringActiveWindow) {
    if (sessionMonitoringActiveWindow == null || sessionMonitoringActiveWindow.isZero()
        || sessionMonitoringActiveWindow.isNegative()) {
      throw new IllegalArgumentException("sessionMonitoringActiveWindow must be positive.");
    }
    this.sessionMonitoringActiveWindow = sessionMonitoringActiveWindow;
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

  public List<String> getAdminEmails() {
    return adminEmails;
  }

  public void setAdminEmails(List<String> adminEmails) {
    this.adminEmails = adminEmails == null ? List.of() : List.copyOf(adminEmails);
  }

  public boolean isPasswordLoginEnabled() {
    return passwordLoginEnabled;
  }

  public void setPasswordLoginEnabled(boolean passwordLoginEnabled) {
    this.passwordLoginEnabled = passwordLoginEnabled;
  }

  public boolean isPasswordRegistrationEnabled() {
    return passwordRegistrationEnabled;
  }

  public void setPasswordRegistrationEnabled(boolean passwordRegistrationEnabled) {
    this.passwordRegistrationEnabled = passwordRegistrationEnabled;
  }
}
