package org.congcong.algomentor.api.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.congcong.algomentor.agent.core.execution.AgentExecutionConstants;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Agent loop 专用执行线程池配置。 */
@Validated
@ConfigurationProperties(prefix = MentorConfigurationKeys.AGENT_EXECUTOR_PREFIX)
public class AgentExecutorProperties {

  @Min(1)
  private int corePoolSize = 20;

  @Min(1)
  private int maxPoolSize = 100;

  @NotNull
  private Duration keepAlive = Duration.ofSeconds(60);

  @NotNull
  private Duration shutdownTimeout = Duration.ofSeconds(30);

  @NotBlank
  private String threadNamePrefix = AgentExecutionConstants.DEFAULT_THREAD_NAME_PREFIX;

  @AssertTrue(message = "Agent executor core pool size must not exceed max pool size")
  public boolean isPoolSizeRangeValid() {
    return corePoolSize <= maxPoolSize;
  }

  @AssertTrue(message = "Agent executor keep alive must be positive")
  public boolean isKeepAliveValid() {
    return keepAlive != null && !keepAlive.isNegative() && !keepAlive.isZero();
  }

  @AssertTrue(message = "Agent executor shutdown timeout must be positive")
  public boolean isShutdownTimeoutValid() {
    return shutdownTimeout != null && !shutdownTimeout.isNegative() && !shutdownTimeout.isZero();
  }

  public int getCorePoolSize() {
    return corePoolSize;
  }

  public void setCorePoolSize(int corePoolSize) {
    this.corePoolSize = corePoolSize;
  }

  public int getMaxPoolSize() {
    return maxPoolSize;
  }

  public void setMaxPoolSize(int maxPoolSize) {
    this.maxPoolSize = maxPoolSize;
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
}
