package org.congcong.algomentor.api.config;

import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 学习计划首次草案 Redis Stream 的独立连接与保留配置。 */
@ConfigurationProperties(prefix = MentorConfigurationKeys.LEARNING_PLAN_REALTIME_STREAM_PREFIX)
public class LearningPlanGenerationRealtimeStreamProperties {

  private static final int STREAMS_REDIS_PORT = 6380;
  private static final Duration MAX_TIMEOUT = Duration.ofSeconds(30);

  private boolean enabled = true;
  private String host = "localhost";
  private int port = STREAMS_REDIS_PORT;
  private int database;
  private String username = "";
  private String password = "";
  private boolean sslEnabled;
  private Duration commandTimeout = Duration.ofSeconds(3);
  private Duration connectTimeout = Duration.ofSeconds(2);
  private Duration shutdownTimeout = Duration.ofSeconds(2);
  private Duration readBlock = Duration.ofSeconds(2);
  private int maxReadConnections = 32;
  private Duration activeRetention = Duration.ofHours(2);
  private Duration completedRetention = Duration.ofHours(24);

  public boolean isEnabled() { return enabled; }
  public void setEnabled(boolean enabled) { this.enabled = enabled; }
  public String getHost() { return host; }
  public void setHost(String host) { this.host = nonBlank(host, "host"); }
  public int getPort() { return port; }
  public void setPort(int port) { this.port = port(port); }
  public int getDatabase() { return database; }
  public void setDatabase(int database) {
    if (database < 0 || database > 15) {
      throw new IllegalArgumentException("Learning plan generation realtime Redis database must be between 0 and 15");
    }
    this.database = database;
  }
  public String getUsername() { return username; }
  public void setUsername(String username) { this.username = username == null ? "" : username; }
  public String getPassword() { return password; }
  public void setPassword(String password) { this.password = password == null ? "" : password; }
  public boolean isSslEnabled() { return sslEnabled; }
  public void setSslEnabled(boolean sslEnabled) { this.sslEnabled = sslEnabled; }
  public Duration getCommandTimeout() { return commandTimeout; }
  public void setCommandTimeout(Duration value) { commandTimeout = timeout(value, "commandTimeout"); }
  public Duration getConnectTimeout() { return connectTimeout; }
  public void setConnectTimeout(Duration value) { connectTimeout = timeout(value, "connectTimeout"); }
  public Duration getShutdownTimeout() { return shutdownTimeout; }
  public void setShutdownTimeout(Duration value) { shutdownTimeout = timeout(value, "shutdownTimeout"); }
  public Duration getReadBlock() { return readBlock; }
  public void setReadBlock(Duration value) { readBlock = timeout(value, "readBlock"); }
  public int getMaxReadConnections() { return maxReadConnections; }
  public void setMaxReadConnections(int value) {
    if (value < 1) {
      throw new IllegalArgumentException("Learning plan generation realtime Redis maxReadConnections must be positive");
    }
    maxReadConnections = value;
  }
  public Duration getActiveRetention() { return activeRetention; }
  public void setActiveRetention(Duration value) { activeRetention = positive(value, "activeRetention"); }
  public Duration getCompletedRetention() { return completedRetention; }
  public void setCompletedRetention(Duration value) { completedRetention = positive(value, "completedRetention"); }

  public void validate() {
    host = nonBlank(host, "host");
    port = port(port);
    setDatabase(database);
    commandTimeout = timeout(commandTimeout, "commandTimeout");
    connectTimeout = timeout(connectTimeout, "connectTimeout");
    shutdownTimeout = timeout(shutdownTimeout, "shutdownTimeout");
    readBlock = timeout(readBlock, "readBlock");
    setMaxReadConnections(maxReadConnections);
    if (commandTimeout.compareTo(readBlock) <= 0) {
      throw new IllegalArgumentException(
          "Learning plan generation realtime Redis commandTimeout must be greater than readBlock");
    }
    activeRetention = positive(activeRetention, "activeRetention");
    completedRetention = positive(completedRetention, "completedRetention");
  }

  private static String nonBlank(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("Learning plan generation realtime Redis " + name + " must not be blank");
    }
    return value;
  }

  private static int port(int value) {
    if (value < 1 || value > 65_535) {
      throw new IllegalArgumentException("Learning plan generation realtime Redis port must be between 1 and 65535");
    }
    return value;
  }

  private static Duration timeout(Duration value, String name) {
    value = positive(value, name);
    if (value.compareTo(MAX_TIMEOUT) > 0) {
      throw new IllegalArgumentException("Learning plan generation realtime Redis " + name + " must be at most " + MAX_TIMEOUT);
    }
    return value;
  }

  private static Duration positive(Duration value, String name) {
    value = Objects.requireNonNull(value, "Learning plan generation realtime Redis " + name + " must not be null");
    if (value.isZero() || value.isNegative()) {
      throw new IllegalArgumentException("Learning plan generation realtime Redis " + name + " must be positive");
    }
    return value;
  }
}
