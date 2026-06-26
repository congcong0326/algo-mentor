package org.congcong.algomentor.llm.openai;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OpenAI provider 的 Spring 配置模型。
 */
@ConfigurationProperties(prefix = "algo-mentor.ai.openai")
public class OpenAiLlmProperties {

  private boolean enabled;
  private String apiKey = "";
  private URI baseUrl = URI.create("https://api.openai.com/v1");
  private String model = "gpt-5.2";
  private Duration timeout = Duration.ofMinutes(5);
  private Duration streamTimeout = Duration.ofMinutes(5);
  private int maxRetries = 2;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public String getApiKey() {
    return apiKey;
  }

  public void setApiKey(String apiKey) {
    this.apiKey = apiKey == null ? "" : apiKey;
  }

  public URI getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(URI baseUrl) {
    this.baseUrl = baseUrl;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    this.timeout = timeout;
  }

  public Duration getStreamTimeout() {
    return streamTimeout;
  }

  public void setStreamTimeout(Duration streamTimeout) {
    this.streamTimeout = streamTimeout;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    this.maxRetries = maxRetries;
  }

  public void validate() {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("OpenAI model must not be blank");
    }
    if (baseUrl == null) {
      throw new IllegalArgumentException("OpenAI base URL must not be null");
    }
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("OpenAI timeout must be positive");
    }
    if (streamTimeout == null || streamTimeout.isZero() || streamTimeout.isNegative()) {
      throw new IllegalArgumentException("OpenAI stream timeout must be positive");
    }
    if (maxRetries < 0) {
      throw new IllegalArgumentException("OpenAI max retries must not be negative");
    }
    if (enabled && (apiKey == null || apiKey.isBlank())) {
      throw new IllegalArgumentException("OpenAI API key must not be blank when provider is enabled");
    }
  }
}
