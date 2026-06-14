package org.congcong.algomentor.llm.openai;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "algo-mentor.ai.openai")
public class OpenAiLlmProperties {

  private boolean enabled;

  private String apiKey = "";

  private URI baseUrl = URI.create("https://api.openai.com/v1");

  private String model = "gpt-5.2";

  private Duration timeout = Duration.ofSeconds(30);

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
    this.apiKey = apiKey;
  }

  public URI getBaseUrl() {
    return baseUrl;
  }

  public void setBaseUrl(URI baseUrl) {
    if (baseUrl == null) {
      throw new IllegalArgumentException("OpenAI base URL must not be null");
    }
    this.baseUrl = baseUrl;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("OpenAI model must not be blank");
    }
    this.model = model.trim();
  }

  public Duration getTimeout() {
    return timeout;
  }

  public void setTimeout(Duration timeout) {
    if (timeout == null || timeout.isZero() || timeout.isNegative()) {
      throw new IllegalArgumentException("OpenAI timeout must be positive");
    }
    this.timeout = timeout;
  }

  public int getMaxRetries() {
    return maxRetries;
  }

  public void setMaxRetries(int maxRetries) {
    if (maxRetries < 0) {
      throw new IllegalArgumentException("OpenAI max retries must not be negative");
    }
    this.maxRetries = maxRetries;
  }
}
