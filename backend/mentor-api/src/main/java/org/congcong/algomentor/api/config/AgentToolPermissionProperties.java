package org.congcong.algomentor.api.config;

import java.time.Duration;
import org.congcong.algomentor.agent.core.permission.InMemoryAgentToolPermissionCoordinator;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = MentorConfigurationKeys.AGENT_TOOL_PERMISSION_PREFIX)
public class AgentToolPermissionProperties {

  private boolean enabled = true;
  private Duration timeout = InMemoryAgentToolPermissionCoordinator.DEFAULT_TIMEOUT;
  private Duration cleanupInterval = Duration.ofMinutes(2);

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }

  public Duration getCleanupInterval() {
    return cleanupInterval;
  }

  public void setCleanupInterval(Duration cleanupInterval) {
    this.cleanupInterval = cleanupInterval;
  }

  public void validate() {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("Agent tool permission timeout must be positive");
    }
    if (cleanupInterval == null || cleanupInterval.isZero() || cleanupInterval.isNegative()) {
      throw new IllegalArgumentException("Agent tool permission cleanup interval must be positive");
    }
  }
}
