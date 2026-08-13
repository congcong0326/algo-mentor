package org.congcong.algomentor.api.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.congcong.algomentor.agent.core.execution.AgentExecutionConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Agent loop 专用执行线程池配置。 */
@Validated
@ConfigurationProperties(prefix = MentorConfigurationKeys.AGENT_EXECUTOR_PREFIX)
public class AgentExecutorProperties {

  /** 稳定执行组 code 到并发容量的静态映射；修改后需重启。 */
  private Map<String, Integer> groups = defaultGroups();

  @NotNull
  private Duration keepAlive = Duration.ofSeconds(60);

  @NotNull
  private Duration shutdownTimeout = Duration.ofSeconds(30);

  @NotBlank
  private String threadNamePrefix = AgentExecutionConstants.DEFAULT_THREAD_NAME_PREFIX;

  @AssertTrue(message = "Agent executor keep alive must be positive")
  public boolean isKeepAliveValid() {
    return keepAlive != null && !keepAlive.isNegative() && !keepAlive.isZero();
  }

  @AssertTrue(message = "Agent executor shutdown timeout must be positive")
  public boolean isShutdownTimeoutValid() {
    return shutdownTimeout != null && !shutdownTimeout.isNegative() && !shutdownTimeout.isZero();
  }

  public Map<String, Integer> getGroups() {
    return Map.copyOf(groups);
  }

  public void setGroups(Map<String, Integer> groups) {
    this.groups = groups == null ? Map.of() : new LinkedHashMap<>(groups);
  }

  public Duration getKeepAlive() {
    return keepAlive;
  }

  public void setKeepAlive(Duration keepAlive) {
    this.keepAlive = keepAlive;
  }

  public Duration getShutdownTimeout() {
    return shutdownTimeout;
  }

  public void setShutdownTimeout(Duration shutdownTimeout) {
    this.shutdownTimeout = shutdownTimeout;
  }

  public String getThreadNamePrefix() {
    return threadNamePrefix;
  }

  public void setThreadNamePrefix(String threadNamePrefix) {
    this.threadNamePrefix = threadNamePrefix;
  }

  private static Map<String, Integer> defaultGroups() {
    Map<String, Integer> groups = new LinkedHashMap<>();
    groups.put("practice", 27);
    groups.put("learning-plan", 2);
    groups.put("learner-profile-background", 1);
    return groups;
  }
}
